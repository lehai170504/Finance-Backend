package com.homie.finance.service;

import com.homie.finance.dto.DebtResponse;
import com.homie.finance.dto.GroupStatsResponse;
import com.homie.finance.dto.PageResponse;
import com.homie.finance.dto.StatisticResponse;
import com.homie.finance.dto.TransactionRequest;
import com.homie.finance.dto.TransactionResponse;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.Debt;
import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.BudgetRepository;
import com.homie.finance.repository.CategoryRepository;
import com.homie.finance.repository.DebtRepository;
import com.homie.finance.repository.GroupSpaceRepository;
import com.homie.finance.repository.TransactionLogRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.WalletRepository;
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

    private GroupSpace requireGroupMembership(String groupId, User currentUser) {
        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay nhom!"));

        if (group.getMembers() == null || group.getMembers().stream().noneMatch(member -> member.getId().equals(currentUser.getId()))) {
            throw new IllegalArgumentException("Ban khong co quyen truy cap nhom nay!");
        }

        return group;
    }

    @Transactional
    public TransactionResponse createTransaction(String walletId, String categoryId, String groupId, TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        if (categoryId == null || categoryId.isEmpty()) {
            String suggestedId = suggestCategoryId(request.getNote());
            if (suggestedId != null) {
                categoryId = suggestedId;
            } else {
                throw new IllegalArgumentException("Vui long chon danh muc cho giao dich nay!");
            }
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay danh muc!"));

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay vi!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Vi nay khong thuoc ve ban!");
        }

        if ("EXPENSE".equals(category.getType())) {
            if (wallet.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("So du khong du de thuc hien giao dich nay!");
            }
            wallet.setBalance(wallet.getBalance() - request.getAmount());
        } else {
            wallet.setBalance(wallet.getBalance() + request.getAmount());
        }
        walletRepository.save(wallet);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(currentUser);
        transaction.setDeleted(false);

        if (groupId != null && !groupId.isEmpty()) {
            transaction.setGroupSpace(requireGroupMembership(groupId, currentUser));
        }

        Transaction savedTx = transactionRepository.save(transaction);

        logService.saveLog(savedTx.getId(), currentUser.getUsername(), "CREATE",
                String.format("Tao moi: %.0fd [%s] - Danh muc: %s",
                        savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(currentUser, "Vi '" + wallet.getName() + "' sap can tien roi!");
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
        if (members.size() <= 1) {
            return;
        }

        double shareAmount = Math.floor(transaction.getAmount() / members.size());

        for (User member : members) {
            if (!member.getId().equals(transaction.getUser().getId())) {
                Debt debt = new Debt();
                debt.setCreditor(transaction.getUser());
                debt.setDebtor(member);
                debt.setAmount(shareAmount);
                debt.setGroup(group);
                debt.setSettled(false);
                debtRepository.save(debt);

                String msg = "Ban co khoan no moi: " + String.format("%.0f", shareAmount)
                        + "d tu " + transaction.getUser().getUsername() + " cho \"" + transaction.getNote() + "\"";
                notificationService.createNotification(member, msg);
            }
        }
    }

    @Transactional
    public TransactionResponse updateTransaction(String id, String newWalletId, String newCategoryId, TransactionRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction oldTx = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(oldTx, currentUser);

        Wallet oldWallet = oldTx.getWallet();
        if ("EXPENSE".equals(oldTx.getCategory().getType())) {
            oldWallet.setBalance(oldWallet.getBalance() + oldTx.getAmount());
        } else {
            oldWallet.setBalance(oldWallet.getBalance() - oldTx.getAmount());
        }
        walletRepository.save(oldWallet);

        Wallet newWallet = walletRepository.findById(newWalletId).orElseThrow();
        Category newCategory = categoryRepository.findById(newCategoryId).orElseThrow();

        if (!newWallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Vi moi khong thuoc ve ban!");
        }

        if ("EXPENSE".equals(newCategory.getType()) && newWallet.getBalance() < request.getAmount()) {
            throw new IllegalArgumentException("Vi moi khong du so du de thuc hien thay doi nay!");
        }

        if ("EXPENSE".equals(newCategory.getType())) {
            newWallet.setBalance(newWallet.getBalance() - request.getAmount());
        } else {
            newWallet.setBalance(newWallet.getBalance() + request.getAmount());
        }
        walletRepository.save(newWallet);

        String logDetail = String.format("Sua: %.0f -> %.0f | Ghi chu: '%s' -> '%s'",
                oldTx.getAmount(), request.getAmount(), oldTx.getNote(), request.getNote());
        logService.saveLog(oldTx.getId(), currentUser.getUsername(), "UPDATE", logDetail);

        oldTx.setAmount(request.getAmount());
        oldTx.setNote(request.getNote());
        oldTx.setDate(request.getDate());
        oldTx.setCategory(newCategory);
        oldTx.setWallet(newWallet);

        return mapToDto(transactionRepository.save(oldTx));
    }

    @Transactional
    public void deleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (transaction.isDeleted()) {
            throw new IllegalArgumentException("Giao dich nay da o trong thung rac roi!");
        }

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
        logService.saveLog(transaction.getId(), currentUser.getUsername(), "DELETE", "Xoa giao dich vao thung rac");
        transactionRepository.save(transaction);
    }

    public List<TransactionResponse> getTrash() {
        User currentUser = securityUtils.getCurrentUser();
        return transactionRepository.findByUserAndIsDeletedTrue(currentUser)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse restoreTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (!transaction.isDeleted()) {
            throw new IllegalArgumentException("Giao dich khong nam trong thung rac!");
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
        logService.saveLog(transaction.getId(), currentUser.getUsername(), "RESTORE", "Khoi phuc giao dich tu thung rac");
        return mapToDto(transactionRepository.save(transaction));
    }

    @Transactional
    public void forceDeleteTransaction(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        securityUtils.validateTransactionOwner(transaction, currentUser);

        if (!transaction.isDeleted()) {
            throw new IllegalArgumentException("Chi duoc xoa vinh vien giao dich dang o trong thung rac!");
        }
        transactionRepository.delete(transaction);
    }

    public GroupStatsResponse getGroupStats(String groupId, int month, int year) {
        User currentUser = securityUtils.getCurrentUser();
        requireGroupMembership(groupId, currentUser);

        List<Transaction> transactions = transactionRepository.findAllByGroupSpaceIdAndIsDeletedFalse(groupId).stream()
                .filter(t -> t.getDate() != null && t.getDate().getMonthValue() == month && t.getDate().getYear() == year)
                .collect(Collectors.toList());

        Double totalExpense = transactions.stream()
                .filter(t -> t.getCategory() != null && "EXPENSE".equals(t.getCategory().getType()))
                .mapToDouble(Transaction::getAmount)
                .sum();

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

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File qua nang!");
        }
        if (transaction.getReceiptUrl() != null) {
            cloudinaryService.deleteImage(transaction.getReceiptUrl());
        }

        transaction.setReceiptUrl(cloudinaryService.uploadImage(file));
        transactionRepository.save(transaction);
        return mapToDto(transaction);
    }

    public List<TransactionResponse> getAllTransactionsForExport() {
        return transactionRepository.findByUserAndIsDeletedFalse(securityUtils.getCurrentUser(), Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Double getTotalByType(String type) {
        Double total = transactionRepository.sumAmountByUserAndType(securityUtils.getCurrentUser(), type);
        return total != null ? total : 0.0;
    }

    public List<TransactionResponse> getTransactionsByType(String type) {
        return transactionRepository.findByUserAndCategoryTypeAndIsDeletedFalse(securityUtils.getCurrentUser(), type)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

    private void checkBudgetAndAlert(User currentUser, Category category, TransactionRequest request) {
        int month = request.getDate().getMonthValue();
        int year = request.getDate().getYear();

        budgetRepository.findByUserAndCategoryAndMonthAndYear(currentUser, category, month, year)
                .ifPresent(budget -> {
                    Double limit = budget.getLimitAmount();
                    LocalDate startDate = YearMonth.of(year, month).atDay(1);
                    LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();
                    Double spent = transactionRepository.sumAmountByUserAndCategoryAndDateBetween(currentUser, category, startDate, endDate);
                    if (spent == null) {
                        spent = 0.0;
                    }

                    if (spent + request.getAmount() > limit) {
                        alertService.sendBudgetAlertEmail(currentUser.getEmail(), currentUser.getUsername(), category.getName(), limit);
                        String msg = "Canh bao: Ban da chi tieu vuot dinh muc cua danh muc " + category.getName()
                                + " (Han muc: " + String.format("%.0f", limit) + "d)";
                        notificationService.createNotification(currentUser, msg);
                    }
                });
    }

    public PageResponse<TransactionResponse> getGroupTransactions(String groupId, int page, int size) {
        User currentUser = securityUtils.getCurrentUser();
        requireGroupMembership(groupId, currentUser);
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return mapToPageResponse(transactionRepository.findByGroupSpaceIdAndIsDeletedFalse(groupId, pageable));
    }

    @Transactional
    public void settleDebt(String debtId) {
        User currentUser = securityUtils.getCurrentUser();
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay khoan no nay!"));

        if (!debt.getCreditor().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Chi chu no moi co quyen xac nhan thanh toan!");
        }

        debt.setSettled(true);
        debtRepository.save(debt);

        String msg = currentUser.getUsername() + " da xac nhan ban tra xong khoan no " + debt.getAmount() + "d.";
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
            if (debt.getCreditor() != null) {
                res.setCreditorName(debt.getCreditor().getUsername());
            }
            if (debt.getDebtor() != null) {
                res.setDebtorName(debt.getDebtor().getUsername());
            }
            res.setSettled(debt.isSettled());
            return res;
        }).collect(Collectors.toList());
    }

    private TransactionResponse mapToDto(Transaction transaction) {
        TransactionResponse res = new TransactionResponse();
        res.setId(transaction.getId());
        res.setAmount(transaction.getAmount());
        res.setNote(transaction.getNote());
        res.setDate(transaction.getDate());
        res.setReceiptUrl(transaction.getReceiptUrl());

        if (transaction.getCategory() != null) {
            res.setCategoryName(transaction.getCategory().getName());
            res.setCategoryType(transaction.getCategory().getType());
        }
        if (transaction.getWallet() != null) {
            res.setWalletName(transaction.getWallet().getName());
        }

        if (transaction.getGroupSpace() != null) {
            res.setGroupName(transaction.getGroupSpace().getName());
            res.setGroupId(transaction.getGroupSpace().getId());
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
        if (note == null || note.length() < 2) {
            return null;
        }
        User currentUser = securityUtils.getCurrentUser();

        List<Category> suggestions = transactionRepository.findSuggestedCategory(
                currentUser, note, PageRequest.of(0, 1));

        if (!suggestions.isEmpty()) {
            return suggestions.get(0).getId();
        }

        Map<String, String> commonMap = Map.of(
                "starbucks", "An uong",
                "highlands", "An uong",
                "phuc long", "An uong",
                "grab", "Di chuyen",
                "be", "Di chuyen",
                "netflix", "Giai tri"
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
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay danh muc!"));

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay vi!"));

        if ("EXPENSE".equals(category.getType())) {
            if (wallet.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("So du khong du de thuc hien giao dich dinh ky!");
            }
            wallet.setBalance(wallet.getBalance() - request.getAmount());
        } else {
            wallet.setBalance(wallet.getBalance() + request.getAmount());
        }
        walletRepository.save(wallet);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setNote(request.getNote());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setWallet(wallet);
        transaction.setUser(user);
        transaction.setDeleted(false);

        Transaction savedTx = transactionRepository.save(transaction);

        logService.saveLog(savedTx.getId(), "HE THONG", "CREATE_AUTO",
                String.format("He thong tu dong tao: %.0fd [%s] - Danh muc: %s",
                        savedTx.getAmount(), savedTx.getNote(), category.getName()));

        if (wallet.getBalance() < 100000) {
            notificationService.createNotification(user, "Canh bao: Vi '" + wallet.getName() + "' sap can tien sau khi tru phi dinh ky!");
        }

        if ("EXPENSE".equals(category.getType())) {
            checkBudgetAndAlert(user, category, request);
        }

        return mapToDto(savedTx);
    }
}
