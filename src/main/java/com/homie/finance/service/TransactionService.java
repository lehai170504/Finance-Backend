package com.homie.finance.service;

import com.homie.finance.dto.transaction.BulkTransactionRequest;
import com.homie.finance.dto.transaction.DebtResponse;
import com.homie.finance.dto.group.GroupStatsResponse;
import com.homie.finance.dto.format.PageResponse;
import com.homie.finance.dto.statistic.StatisticResponse;
import com.homie.finance.dto.transaction.CashFlowResponse;
import com.homie.finance.dto.transaction.TransactionRequest;
import com.homie.finance.dto.transaction.TransactionResponse;
import com.homie.finance.entity.*;
import com.homie.finance.repository.*;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final BudgetRepository budgetRepository;
    private final AlertService alertService;
    private final GroupSpaceRepository groupSpaceRepository;
    private final WalletRepository walletRepository;
    private final DebtRepository debtRepository;
    private final NotificationService notificationService;
    private final LogService logService;
    private final SecurityUtils securityUtils;

    private void adjustWalletBalance(Wallet wallet, Category category, Double amount, String errorMessage) {
        if ("EXPENSE".equals(category.getType())) {
            if (wallet.getBalance() < amount) {
                throw new IllegalArgumentException(errorMessage);
            }
            wallet.setBalance(wallet.getBalance() - amount);
        } else {
            wallet.setBalance(wallet.getBalance() + amount);
        }
        walletRepository.save(wallet);
    }

    private GroupSpace requireGroupMembership(String groupId, User currentUser) {
        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (group.getMembers() == null || group.getMembers().stream().noneMatch(member -> member.getId().equals(currentUser.getId()))) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập nhóm này!");
        }
        return group;
    }

    @Transactional
    public List<TransactionResponse> createMultipleTransactions(BulkTransactionRequest bulkRequest) {
        return bulkRequest.getItems().stream().map(item -> {
            TransactionRequest request = new TransactionRequest();
            request.setAmount(item.getAmount());
            request.setNote(item.getNote());
            request.setDate(bulkRequest.getDate());
            request.setReceiptUrl(bulkRequest.getReceiptUrl());
            request.setWalletId(bulkRequest.getWalletId());
            request.setCategoryId(item.getCategoryId());
            request.setGroupSpaceId(bulkRequest.getGroupId());

            return createTransaction(request);
        }).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public TransactionResponse createTransaction(TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        String categoryId = request.getCategoryId();
        if (categoryId == null || categoryId.isEmpty()) {
            categoryId = suggestCategoryId(request.getNote());
            if (categoryId == null) {
                throw new IllegalArgumentException("Vui lòng chọn danh mục cho giao dịch này!");
            }
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Ví này không thuộc về bạn!");
        }

        adjustWalletBalance(wallet, category, request.getAmount(), "Số dư không đủ để thực hiện giao dịch này!");

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(currentUser);
        transaction.setReceiptUrl(request.getReceiptUrl());

        if (request.getGroupSpaceId() != null && !request.getGroupSpaceId().isEmpty()) {
            transaction.setGroupSpace(requireGroupMembership(request.getGroupSpaceId(), currentUser));
        }

        Transaction savedTx = transactionRepository.save(transaction);

        logService.saveLog(savedTx.getId(), currentUser.getUsername(), "CREATE",
                String.format("Tạo mới: %.0fđ [%s] - Danh mục: %s", savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(currentUser, "Ví '" + wallet.getName() + "' sắp cạn tiền rồi!");
        }

        if ("EXPENSE".equals(category.getType())) {
            checkBudgetAndAlert(currentUser, category, request);
            if (savedTx.getGroupSpace() != null) {
                processSplit(savedTx, savedTx.getGroupSpace());
            }
        }

        return mapToDto(savedTx);
    }

    private void processSplit(Transaction transaction, GroupSpace group) {
        Set<User> members = group.getMembers();
        if (members.size() <= 1) return;

        double shareAmount = Math.floor(transaction.getAmount() / members.size());

        for (User member : members) {
            if (!member.getId().equals(transaction.getUser().getId())) {
                Debt debt = new Debt();
                debt.setCreditor(transaction.getUser());
                debt.setDebtor(member);
                debt.setAmount(shareAmount);
                debt.setGroup(group);
                debt.setTransaction(transaction);
                debt.setSettled(false);
                debtRepository.save(debt);

                String msg = "Bạn có khoản nợ mới: " + String.format("%.0f", shareAmount)
                        + "đ từ " + transaction.getUser().getUsername() + " cho \"" + transaction.getNote() + "\"";
                notificationService.createNotification(member, msg);
            }
        }
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public TransactionResponse updateTransaction(String id, TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction oldTx = transactionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch!"));
        securityUtils.validateTransactionOwner(oldTx, currentUser);

        // Hoàn tiền lại cho ví cũ
        Wallet oldWallet = oldTx.getWallet();
        if ("EXPENSE".equals(oldTx.getCategory().getType())) {
            oldWallet.setBalance(oldWallet.getBalance() + oldTx.getAmount());
        } else {
            oldWallet.setBalance(oldWallet.getBalance() - oldTx.getAmount());
        }
        walletRepository.save(oldWallet);

        Wallet newWallet = walletRepository.findById(request.getWalletId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví mới!"));
        Category newCategory = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục mới!"));

        if (!newWallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Ví mới không thuộc về bạn!");
        }

        adjustWalletBalance(newWallet, newCategory, request.getAmount(), "Ví mới không đủ số dư để thực hiện thay đổi này!");

        String logDetail = String.format("Sửa: %.0f -> %.0f | Ghi chú: '%s' -> '%s'",
                oldTx.getAmount(), request.getAmount(), oldTx.getNote(), request.getNote());
        logService.saveLog(oldTx.getId(), currentUser.getUsername(), "UPDATE", logDetail);

        oldTx.setAmount(request.getAmount());
        oldTx.setNote(request.getNote());
        oldTx.setDate(request.getDate());
        oldTx.setCategory(newCategory);
        oldTx.setWallet(newWallet);
        oldTx.setReceiptUrl(request.getReceiptUrl());

        if (oldTx.getGroupSpace() != null) {
            List<Debt> existingDebts = debtRepository.findByTransaction(oldTx);
            Set<User> members = oldTx.getGroupSpace().getMembers();
            double newShare = members.size() > 1 ? Math.floor(request.getAmount() / members.size()) : 0;

            for (Debt debt : existingDebts) {
                debt.setAmount(newShare);
                debtRepository.save(debt);
            }
        }

        return mapToDto(transactionRepository.save(oldTx));
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public void deleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        Wallet wallet = transaction.getWallet();
        Category category = transaction.getCategory();

        if (wallet != null && category != null) {
            if ("EXPENSE".equals(category.getType())) {
                wallet.setBalance(wallet.getBalance() + transaction.getAmount());
            } else if ("INCOME".equals(category.getType())) {
                wallet.setBalance(wallet.getBalance() - transaction.getAmount());
            }
            walletRepository.save(wallet);
        }

        transaction.setDeleted(true);
        transaction.setDeletedAt(LocalDateTime.now());
        logService.saveLog(transaction.getId(), currentUser.getUsername(), "DELETE", "Xóa giao dịch vào thùng rác");
        transactionRepository.save(transaction);

        List<Debt> existingDebts = debtRepository.findByTransaction(transaction);
        debtRepository.deleteAll(existingDebts);
    }

    public List<TransactionResponse> getTrash() {
        User currentUser = securityUtils.getCurrentUser();
        return transactionRepository.findTrashByUser(currentUser.getId())
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public TransactionResponse restoreTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdIncludingTrash(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        Wallet wallet = transaction.getWallet();
        Category category = transaction.getCategory();
        if (wallet != null && category != null) {
            if ("EXPENSE".equals(category.getType())) {
                wallet.setBalance(wallet.getBalance() - transaction.getAmount());
            } else if ("INCOME".equals(category.getType())) {
                wallet.setBalance(wallet.getBalance() + transaction.getAmount());
            }
            walletRepository.save(wallet);
        }

        transaction.setDeleted(false);
        transaction.setDeletedAt(null);
        logService.saveLog(transaction.getId(), currentUser.getUsername(), "RESTORE", "Khôi phục giao dịch từ thùng rác");
        Transaction restoredTx = transactionRepository.save(transaction);

        if (restoredTx.getGroupSpace() != null) {
            processSplit(restoredTx, restoredTx.getGroupSpace());
        }

        return mapToDto(restoredTx);
    }

    @Transactional
    public void forceDeleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdIncludingTrash(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        List<Debt> existingDebts = debtRepository.findByTransaction(transaction);
        debtRepository.deleteAll(existingDebts);

        transactionRepository.delete(transaction);
    }

    public GroupStatsResponse getGroupStats(String groupId, int month, int year) {
        User currentUser = securityUtils.getCurrentUser();
        requireGroupMembership(groupId, currentUser);

        List<Transaction> transactions = transactionRepository.findAllByGroupSpaceId(groupId).stream()
                .filter(t -> t.getDate() != null && t.getDate().getMonthValue() == month && t.getDate().getYear() == year)
                .toList();

        Double totalExpense = transactions.stream()
                .filter(t -> t.getCategory() != null && "EXPENSE".equals(t.getCategory().getType()))
                .mapToDouble(Transaction::getAmount).sum();

        Map<String, Double> byCategory = transactions.stream()
                .filter(t -> t.getCategory() != null && "EXPENSE".equals(t.getCategory().getType()))
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.summingDouble(Transaction::getAmount)));

        Map<String, Double> byUser = transactions.stream()
                .filter(t -> t.getUser() != null)
                .collect(Collectors.groupingBy(t -> t.getUser().getUsername(), Collectors.summingDouble(Transaction::getAmount)));

        return new GroupStatsResponse(totalExpense, byCategory, byUser);
    }

    public PageResponse<TransactionResponse> getAllTransactions(int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByUser(currentUser, pageable));
    }

    public PageResponse<TransactionResponse> searchTransactions(String keyword, int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByUserAndNoteContainingIgnoreCase(currentUser, keyword, pageable));
    }

    public TransactionResponse uploadReceipt(String transactionId, MultipartFile file) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("File quá nặng! (Tối đa 5MB)");

        if (transaction.getReceiptUrl() != null) {
            cloudinaryService.deleteImage(transaction.getReceiptUrl());
        }

        transaction.setReceiptUrl(cloudinaryService.uploadImage(file));
        transactionRepository.save(transaction);
        return mapToDto(transaction);
    }

    public List<TransactionResponse> getAllTransactionsForExport() {
        return transactionRepository.findByUser(securityUtils.getCurrentUser(), Pageable.unpaged())
                .getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public Double getTotalByType(String type) {
        Double total = transactionRepository.sumAmountByUserAndType(securityUtils.getCurrentUser(), type);
        return total != null ? total : 0.0;
    }

    public List<TransactionResponse> getTransactionsByType(String type) {
        return transactionRepository.findByUserAndCategoryType(securityUtils.getCurrentUser(), type)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<StatisticResponse> getCategoryStatistics(LocalDate startDate, LocalDate endDate) {
        User currentUser = securityUtils.getCurrentUser();
        if (startDate == null || endDate == null) {
            YearMonth currentMonth = YearMonth.now();
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }
        return transactionRepository.getCategoryStatistics(currentUser, startDate, endDate);
    }

    public List<CashFlowResponse> getCashFlowStatistics(LocalDate startDate, LocalDate endDate) {
        User currentUser = securityUtils.getCurrentUser();
        if (startDate == null || endDate == null) {
            YearMonth currentMonth = YearMonth.now();
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }
        return transactionRepository.getCashFlowStatistics(currentUser, startDate, endDate);
    }

    private void checkBudgetAndAlert(User currentUser, Category category, TransactionRequest request) {
        int month = request.getDate().getMonthValue();
        int year = request.getDate().getYear();

        budgetRepository.findByUserAndCategoryAndMonthAndYear(currentUser, category, month, year)
                .ifPresent(budget -> {
                    Double limit = budget.getLimitAmount();
                    LocalDate startDate = YearMonth.of(year, month).atDay(1);
                    LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();
                    Double spent = transactionRepository.sumAmountByUserAndCategoryAndDateBetween(currentUser, category, startDate, endDate);
                    if (spent == null) spent = 0.0;

                    if (spent + request.getAmount() > limit) {
                        alertService.sendBudgetAlertEmail(currentUser.getEmail(), currentUser.getUsername(), category.getName(), limit);
                        String msg = "Cảnh báo: Bạn đã chi tiêu vượt định mức của danh mục " + category.getName() + " (Hạn mức: " + String.format("%.0f", limit) + "đ)";
                        notificationService.createNotification(currentUser, msg);
                    }
                });
    }

    public PageResponse<TransactionResponse> getGroupTransactions(String groupId, int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        requireGroupMembership(groupId, currentUser);
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByGroupSpaceId(groupId, pageable));
    }

    @Transactional
    public void settleDebt(String debtId) {
        User currentUser = securityUtils.getCurrentUser();
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoản nợ này!"));

        if (!debt.getCreditor().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Chỉ chủ nợ mới có quyền xác nhận thanh toán!");
        }

        debt.setSettled(true);
        debtRepository.save(debt);

        String msg = currentUser.getUsername() + " đã xác nhận bạn trả xong khoản nợ " + String.format("%.0f", debt.getAmount()) + "đ.";
        notificationService.createNotification(debt.getDebtor(), msg);
    }

    @Transactional(readOnly = true)
    public List<DebtResponse> getGroupDebts(String groupId) {
        User currentUser = securityUtils.getCurrentUser();
        requireGroupMembership(groupId, currentUser);

        return debtRepository.findByGroupIdAndIsSettledFalse(groupId).stream().map(debt -> {
            DebtResponse res = new DebtResponse();
            res.setId(debt.getId());
            res.setAmount(debt.getAmount());
            if (debt.getCreditor() != null) res.setCreditorName(debt.getCreditor().getUsername());
            if (debt.getDebtor() != null) res.setDebtorName(debt.getDebtor().getUsername());
            res.setSettled(debt.isSettled());
            return res;
        }).collect(Collectors.toList());
    }

    private TransactionResponse mapToDto(Transaction transaction) {
        if (transaction == null) return null;

        TransactionResponse res = TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .note(transaction.getNote())
                .date(transaction.getDate())
                .receiptUrl(transaction.getReceiptUrl())
                .build();

        if (transaction.getCategory() != null) {
            res.setCategoryName(transaction.getCategory().getName());
            res.setType(transaction.getCategory().getType());
        } else {
            res.setCategoryName("Chưa phân loại");
            res.setType("EXPENSE");
        }

        if (transaction.getWallet() != null) {
            res.setWalletName(transaction.getWallet().getName());
        } else {
            res.setWalletName("Ví không xác định");
        }

        if (transaction.getGroupSpace() != null) {
            res.setGroupId(transaction.getGroupSpace().getId());
            res.setGroupName(transaction.getGroupSpace().getName());
        }

        if (transaction.getUser() != null) {
            res.setUserId(transaction.getUser().getId());
            res.setUserName(transaction.getUser().getUsername());
        }

        return res;
    }

    private PageResponse<TransactionResponse> mapToPageResponse(Page<Transaction> page) {
        List<TransactionResponse> content = page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
        return new PageResponse<>(content, page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    public String suggestCategoryId(String note) {
        if (note == null || note.length() < 2) return null;
        User currentUser = securityUtils.getCurrentUser();

        List<Category> suggestions = transactionRepository.findSuggestedCategory(currentUser, note, PageRequest.of(0, 1));
        if (!suggestions.isEmpty()) return suggestions.get(0).getId();

        Map<String, String> commonMap = Map.of(
                "starbucks", "An uong",
                "highlands", "An uong",
                "phuc long", "An uong",
                "grab", "Di chuyen",
                "be", "Di chuyen",
                "netflix", "Giai tri");

        String lowerNote = note.toLowerCase().trim();
        for (Map.Entry<String, String> entry : commonMap.entrySet()) {
            if (lowerNote.contains(entry.getKey())) {
                return categoryRepository.findByNameIgnoreCase(entry.getValue().trim())
                        .map(Category::getId).orElse(null);
            }
        }
        return null;
    }

    @Transactional
    public TransactionResponse createSystemTransaction(TransactionRequest request, User user) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        adjustWalletBalance(wallet, category, request.getAmount(), "Số dư không đủ để thực hiện giao dịch định kỳ!");

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(user);
        transaction.setReceiptUrl(request.getReceiptUrl());

        Transaction savedTx = transactionRepository.save(transaction);

        logService.saveLog(savedTx.getId(), "HE THONG", "CREATE_AUTO",
                String.format("Hệ thống tự động tạo: %.0fđ [%s] - Danh mục: %s", savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(user, "Cảnh báo: Ví '" + wallet.getName() + "' sắp cạn tiền sau khi trừ phí định kỳ!");
        }

        if ("EXPENSE".equals(category.getType())) {
            checkBudgetAndAlert(user, category, request);
        }

        return mapToDto(savedTx);
    }
}