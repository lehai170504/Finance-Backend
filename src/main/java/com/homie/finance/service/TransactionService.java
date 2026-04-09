package com.homie.finance.service;

import com.homie.finance.dto.*;
import com.homie.finance.entity.*;
import com.homie.finance.repository.*;
import com.homie.finance.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
public class TransactionService {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CloudinaryService cloudinaryService;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private AlertService alertService;
    @Autowired private GroupSpaceRepository groupSpaceRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private DebtRepository debtRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private TransactionLogRepository transactionLogRepository;
    @Autowired private LogService logService;
    @Autowired private SecurityUtils securityUtils;

    // --- 1. TẠO GIAO DỊCH ---
    @Transactional
    public TransactionResponse createTransaction(String walletId, String categoryId, String groupId, TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        if (categoryId == null || categoryId.isEmpty()) {
            String suggestedId = suggestCategoryId(request.getNote());
            if (suggestedId != null) {
                categoryId = suggestedId;
            } else {
                throw new IllegalArgumentException("Vui lòng chọn danh mục cho giao dịch này, homie!");
            }
        }

        // Sau khi đã có categoryId (từ user hoặc từ gợi ý), ta tiến hành tìm kiếm
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Homie ơi, ví này không thuộc về bạn!");
        }

        // 1. Cập nhật số dư ví
        if ("EXPENSE".equals(category.getType())) {
            if (wallet.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("Số dư không đủ để thực hiện giao dịch này!");
            }
            wallet.setBalance(wallet.getBalance() - request.getAmount());
        } else {
            wallet.setBalance(wallet.getBalance() + request.getAmount());
        }
        walletRepository.save(wallet);

        // 2. Tạo Entity
        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(currentUser);
        transaction.setDeleted(false);

        if (groupId != null && !groupId.isEmpty()) {
            GroupSpace group = groupSpaceRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));
            transaction.setGroupSpace(group);
        }

        Transaction savedTx = transactionRepository.save(transaction);

        // 3. Nhật ký và Thông báo
        logService.saveLog(savedTx.getId(), currentUser.getUsername(), "CREATE",
                String.format("Tạo mới: %.0fđ [%s] - Danh mục: %s",
                        savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(currentUser, "Ví '" + wallet.getName() + "' sắp cạn tiền rồi homie!");
        }

        // 4. Kiểm tra ngân sách và chia nợ
        if ("EXPENSE".equals(category.getType())) {
            checkBudgetAndAlert(currentUser, category, request);

            if (savedTx.getGroupSpace() != null) {
                processSplit(savedTx, savedTx.getGroupSpace());
            }
        }

        return mapToDto(savedTx);
    }

    private void processSplit(Transaction t, GroupSpace group) {
        Set<User> members = group.getMembers();
        if (members.size() <= 1) return;

        double shareAmount = Math.floor(t.getAmount() / members.size());

        for (User member : members) {
            if (!member.getId().equals(t.getUser().getId())) {
                Debt debt = new Debt();
                debt.setCreditor(t.getUser());
                debt.setDebtor(member);
                debt.setAmount(shareAmount);
                debt.setGroup(group);
                debt.setSettled(false);
                debtRepository.save(debt);

                String msg = "Bạn có khoản nợ mới: " + String.format("%.0f", shareAmount) +
                        "đ từ " + t.getUser().getUsername() + " cho \"" + t.getNote() + "\"";
                notificationService.createNotification(member, msg);
            }
        }
    }

    // --- 2. CẬP NHẬT GIAO DỊCH ---
    @Transactional
    public TransactionResponse updateTransaction(String id, String newWalletId, String newCategoryId, TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction oldTx = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(oldTx, currentUser);

        // 1. Hoàn tiền ví cũ
        Wallet oldWallet = oldTx.getWallet();
        if ("EXPENSE".equals(oldTx.getCategory().getType())) oldWallet.setBalance(oldWallet.getBalance() + oldTx.getAmount());
        else oldWallet.setBalance(oldWallet.getBalance() - oldTx.getAmount());
        walletRepository.save(oldWallet);

        // 2. Trừ tiền ví mới (Có check số dư)
        Wallet newWallet = walletRepository.findById(newWalletId).orElseThrow();
        Category newCategory = categoryRepository.findById(newCategoryId).orElseThrow();

        if ("EXPENSE".equals(newCategory.getType()) && newWallet.getBalance() < request.getAmount()) {
            throw new IllegalArgumentException("Ví mới không đủ số dư để thực hiện thay đổi này!");
        }

        if ("EXPENSE".equals(newCategory.getType())) newWallet.setBalance(newWallet.getBalance() - request.getAmount());
        else newWallet.setBalance(newWallet.getBalance() + request.getAmount());
        walletRepository.save(newWallet);

        // 3. LƯU AUDIT LOG (NHẬT KÝ THAY ĐỔI)
        String logDetail = String.format("Sửa: %.0f -> %.0f | Ghi chú: '%s' -> '%s'",
                oldTx.getAmount(), request.getAmount(), oldTx.getNote(), request.getNote());
        logService.saveLog(oldTx.getId(), currentUser.getUsername(), "UPDATE", logDetail);

        // 4. Cập nhật Entity
        oldTx.setAmount(request.getAmount());
        oldTx.setNote(request.getNote());
        oldTx.setDate(request.getDate());
        oldTx.setCategory(newCategory);
        oldTx.setWallet(newWallet);

        return mapToDto(transactionRepository.save(oldTx));
    }

    // --- 3. HỆ THỐNG THÙNG RÁC ---

    @Transactional
    public void deleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (transaction.isDeleted()) throw new IllegalArgumentException("Giao dịch này đã ở trong thùng rác rồi!");

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
    }

    public List<TransactionResponse> getTrash() {
        User currentUser = securityUtils.getCurrentUser();
        return transactionRepository.findByUserAndIsDeletedTrue(currentUser)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse restoreTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();

        // Kiểm tra quyền sở hữu
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (!transaction.isDeleted()) {
            throw new IllegalArgumentException("Giao dịch không nằm trong thùng rác!");
        }

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
        return mapToDto(transactionRepository.save(transaction));
    }

    @Transactional
    public void forceDeleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (!transaction.isDeleted()) {
            throw new IllegalArgumentException("Chỉ được xóa vĩnh viễn giao dịch đang ở trong thùng rác!");
        }
        transactionRepository.delete(transaction);
    }

    // --- 4. THỐNG KÊ NHÓM ---
    public GroupStatsResponse getGroupStats(String groupId, int month, int year) {
        List<Transaction> transactions = transactionRepository.findAllByGroupSpaceIdAndIsDeletedFalse(groupId).stream()
                .filter(t -> t.getDate() != null && t.getDate().getMonthValue() == month && t.getDate().getYear() == year)
                .collect(Collectors.toList());

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

    // --- 5. TÌM KIẾM & PHÂN TRANG ---
    public PageResponse<TransactionResponse> getAllTransactions(int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByUserAndIsDeletedFalse(currentUser, pageable));
    }

    public PageResponse<TransactionResponse> searchTransactions(String keyword, int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByUserAndNoteContainingIgnoreCaseAndIsDeletedFalse(currentUser, keyword, pageable));
    }

    public TransactionResponse uploadReceipt(String transactionId, MultipartFile file) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("File quá nặng!");
        if (transaction.getReceiptUrl() != null) cloudinaryService.deleteImage(transaction.getReceiptUrl());

        transaction.setReceiptUrl(cloudinaryService.uploadImage(file));
        transactionRepository.save(transaction);
        return mapToDto(transaction);
    }

    public List<TransactionResponse> getAllTransactionsForExport() {
        return transactionRepository.findByUserAndIsDeletedFalse(securityUtils.getCurrentUser(), Pageable.unpaged())
                .getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public Double getTotalByType(String type) {
        Double total = transactionRepository.sumAmountByUserAndType(securityUtils.getCurrentUser(), type);
        return total != null ? total : 0.0;
    }

    public List<TransactionResponse> getTransactionsByType(String type) {
        return transactionRepository.findByUserAndCategoryTypeAndIsDeletedFalse(securityUtils.getCurrentUser(), type)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // --- 6. THỐNG KÊ CHI TIÊU & BUDGET ---
    public List<StatisticResponse> getCategoryStatistics(LocalDate startDate, LocalDate endDate) {
        User currentUser = securityUtils.getCurrentUser();
        if (startDate == null || endDate == null) {
            YearMonth currentMonth = YearMonth.now();
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }
        return transactionRepository.getCategoryStatistics(currentUser, startDate, endDate);
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
                        String msg = "Cảnh báo: Bạn đã chi tiêu vượt định mức của danh mục " + category.getName() +
                                " (Hạn mức: " + String.format("%.0f", limit) + "đ)";
                        notificationService.createNotification(currentUser, msg);
                    }
                });
    }

    public PageResponse<TransactionResponse> getGroupTransactions(String groupId, int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        if (!groupSpaceRepository.existsByIdAndMembersContaining(groupId, currentUser)) throw new IllegalArgumentException("Không có quyền!");
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByGroupSpaceIdAndIsDeletedFalse(groupId, pageable));
    }

    // --- 7. QUẢN LÝ NỢ (DEBT) ---
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

        String msg = currentUser.getUsername() + " đã xác nhận bạn trả xong khoản nợ " + debt.getAmount() + "đ. Hết nợ nần nhé!";
        notificationService.createNotification(debt.getDebtor(), msg);
    }

    @Transactional(readOnly = true)
    public List<DebtResponse> getGroupDebts(String groupId) {
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

    // --- MAPPING ---
    private TransactionResponse mapToDto(Transaction t) {
        TransactionResponse res = new TransactionResponse();
        res.setId(t.getId());
        res.setAmount(t.getAmount());
        res.setNote(t.getNote());
        res.setDate(t.getDate());
        res.setReceiptUrl(t.getReceiptUrl());

        if (t.getCategory() != null) {
            res.setCategoryName(t.getCategory().getName());
            res.setCategoryType(t.getCategory().getType());
        }
        if (t.getWallet() != null) res.setWalletName(t.getWallet().getName());

        if (t.getGroupSpace() != null) {
            res.setGroupName(t.getGroupSpace().getName());
            res.setGroupId(t.getGroupSpace().getId());
        }

        if (t.getUser() != null) {
            res.setUserId(t.getUser().getId());
            res.setUserName(t.getUser().getUsername());
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

        // 1. Tìm trong lịch sử giao dịch (Giữ nguyên vì Repo đã dùng LOWER trong Query rồi)
        List<Category> suggestions = transactionRepository.findSuggestedCategory(
                currentUser, note, PageRequest.of(0, 1));

        if (!suggestions.isEmpty()) {
            return suggestions.get(0).getId();
        }

        // 2. Hard-coded Mapping (Nên để chữ thường hết cho dễ quản lý)
        Map<String, String> commonMap = Map.of(
                "starbucks", "Ăn uống",
                "highlands", "Ăn uống",
                "phúc long", "Ăn uống",
                "grab", "Di chuyển",
                "be", "Di chuyển",
                "netflix", "Giải trí"
        );

        String lowerNote = note.toLowerCase().trim();

        for (Map.Entry<String, String> entry : commonMap.entrySet()) {
            if (lowerNote.contains(entry.getKey())) {
                return categoryRepository.findByNameIgnoreCase(entry.getValue().trim())
                        .map(Category::getId)
                        .orElse(null);
            }
        }
        return null;
    }

    @Transactional
    public TransactionResponse createSystemTransaction(String walletId, String categoryId, User user, TransactionRequest request) {

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        // 1. Cập nhật số dư ví
        if ("EXPENSE".equals(category.getType())) {
            if (wallet.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("Số dư không đủ để thực hiện giao dịch định kỳ!");
            }
            wallet.setBalance(wallet.getBalance() - request.getAmount());
        } else {
            wallet.setBalance(wallet.getBalance() + request.getAmount());
        }
        walletRepository.save(wallet);

        // 2. Tạo Entity
        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(user);
        transaction.setDeleted(false);

        Transaction savedTx = transactionRepository.save(transaction);

        // 3. Nhật ký và Thông báo (Ghi log là HỆ THỐNG thực hiện)
        logService.saveLog(savedTx.getId(), "HỆ THỐNG", "CREATE_AUTO",
                String.format("Hệ thống tự động tạo: %.0fđ [%s] - Danh mục: %s",
                        savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(user, "Cảnh báo: Ví '" + wallet.getName() + "' sắp cạn tiền sau khi trừ phí định kỳ!");
        }

        // 4. Kiểm tra ngân sách
        if ("EXPENSE".equals(category.getType())) {
            checkBudgetAndAlert(user, category, request);
        }

        return mapToDto(savedTx);
    }

}