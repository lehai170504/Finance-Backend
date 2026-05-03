package com.homie.finance.service;

import com.homie.finance.dto.goal.SavingsGoalRequest;
import com.homie.finance.dto.goal.SavingsGoalResponse;
import com.homie.finance.dto.transaction.TransactionRequest;
import com.homie.finance.entity.SavingsGoal;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.SavingsGoalRepository;
import com.homie.finance.repository.WalletRepository;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final WalletRepository walletRepository;
    private final com.homie.finance.repository.CategoryRepository categoryRepository;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;
    private final TransactionService transactionService;

    // 1. Lấy tất cả mục tiêu của tôi (Chưa đạt lên trên, Đã đạt xuống dưới)
    @Transactional(readOnly = true)
    public List<SavingsGoalResponse> getMyGoals() {
        User currentUser = securityUtils.getCurrentUser();
        return savingsGoalRepository.findByUserOrderByCompletedAsc(currentUser)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // 2. Tạo mục tiêu tiết kiệm mới
    @Transactional
    public SavingsGoalResponse createGoal(SavingsGoalRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        if (request.getTargetAmount() == null || request.getTargetAmount() <= 0) {
            throw new IllegalArgumentException("Số tiền mục tiêu phải lớn hơn 0!");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Tên mục tiêu không được để trống!");
        }

        SavingsGoal goal = new SavingsGoal();
        goal.setName(request.getName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setSavedAmount(0.0);
        goal.setIcon(request.getIcon());
        goal.setColor(request.getColor());
        goal.setUser(currentUser);
        goal.setCompleted(false);

        return mapToDto(savingsGoalRepository.save(goal));
    }

    // 3. Nạp tiền vào Lợn Đất (Trích từ Ví sang Goal)
    @Transactional
    public SavingsGoalResponse depositToGoal(String goalId, String walletId, Double amount) {
        User currentUser = securityUtils.getCurrentUser();

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp vào phải lớn hơn 0!");
        }

        SavingsGoal goal = savingsGoalRepository.findByIdAndUser(goalId, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục tiêu tiết kiệm!"));

        if (goal.isCompleted()) {
            throw new IllegalArgumentException("Mục tiêu này đã hoàn thành rồi, không cần nạp thêm!");
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Ví này không thuộc về bạn!");
        }

        if (wallet.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư ví không đủ để nạp vào mục tiêu!");
        }

        // Trừ tiền từ ví
        wallet.setBalance(wallet.getBalance() - amount);
        walletRepository.save(wallet);

        // Cộng vào lợn đất
        double newSaved = goal.getSavedAmount() + amount;
        goal.setSavedAmount(newSaved);

        // Ghi log giao dịch hệ thống
        categoryRepository.findByNameIgnoreCase("Tiết kiệm").ifPresent(cat -> {
            TransactionRequest txReq = new TransactionRequest();
            txReq.setAmount(amount);
            txReq.setNote("Nạp tiền vào mục tiêu: " + goal.getName());
            txReq.setDate(java.time.LocalDate.now());
            txReq.setWalletId(walletId);
            txReq.setCategoryId(cat.getId());

            transactionService.createSystemTransaction(txReq, currentUser); // Gọi 2 tham số
        });

        // Kiểm tra đạt mục tiêu chưa
        if (newSaved >= goal.getTargetAmount()) {
            goal.setCompleted(true);
            notificationService.createNotification(currentUser,
                    "Chúc mừng! Bạn đã đạt mục tiêu tiết kiệm \"" + goal.getName() + "\"! Xứng đáng lắm homie!");
        }

        return mapToDto(savingsGoalRepository.save(goal));
    }

    // 4. Rút tiền ra khỏi Lợn Đất (Trả về Ví)
    @Transactional
    public SavingsGoalResponse withdrawFromGoal(String goalId, String walletId, Double amount) {
        User currentUser = securityUtils.getCurrentUser();

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút ra phải lớn hơn 0!");
        }

        SavingsGoal goal = savingsGoalRepository.findByIdAndUser(goalId, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục tiêu tiết kiệm!"));

        if (goal.getSavedAmount() < amount) {
            throw new IllegalArgumentException(
                    "Số tiền trong lợn đất không đủ! Hiện có: " + goal.getSavedAmount());
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Ví này không thuộc về bạn!");
        }

        // Trả tiền về ví
        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);

        // Trừ khỏi lợn đất
        goal.setSavedAmount(goal.getSavedAmount() - amount);

        // Ghi log giao dịch hệ thống (Thu nhập từ tiết kiệm)
        categoryRepository.findByNameIgnoreCase("Tiết kiệm").ifPresent(cat -> {
            TransactionRequest txReq = new TransactionRequest();
            txReq.setAmount(amount);
            txReq.setNote("Rút tiền từ mục tiêu: " + goal.getName());
            txReq.setDate(java.time.LocalDate.now());
            txReq.setWalletId(walletId);
            txReq.setCategoryId(cat.getId());

            transactionService.createSystemTransaction(txReq, currentUser);
        });

        // Nếu đã hoàn thành mà rút tiền ra thì đánh dấu chưa hoàn thành lại
        if (goal.isCompleted() && goal.getSavedAmount() < goal.getTargetAmount()) {
            goal.setCompleted(false);
        }

        return mapToDto(savingsGoalRepository.save(goal));
    }

    // 5. Xóa mục tiêu (Tự động hoàn tiền về ví mặc định nếu còn tiền trong lợn)
    @Transactional
    public void deleteGoal(String goalId, String walletId) {
        User currentUser = securityUtils.getCurrentUser();

        SavingsGoal goal = savingsGoalRepository.findByIdAndUser(goalId, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục tiêu tiết kiệm!"));

        // Hoàn tiền về ví nếu còn trong lợn
        if (goal.getSavedAmount() > 0) {
            if (walletId == null || walletId.isBlank()) {
                throw new IllegalArgumentException(
                        "Lợn đất còn " + goal.getSavedAmount()
                                + " VNĐ. Vui lòng cung cấp walletId để hoàn tiền trước khi xóa!");
            }

            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví để hoàn tiền!"));

            if (!wallet.getUser().getId().equals(currentUser.getId())) {
                throw new IllegalArgumentException("Ví hoàn tiền không thuộc về bạn!");
            }

            wallet.setBalance(wallet.getBalance() + goal.getSavedAmount());
            walletRepository.save(wallet);
        }

        savingsGoalRepository.delete(goal);
    }

    // --- MAPPER ---
    private SavingsGoalResponse mapToDto(SavingsGoal goal) {
        SavingsGoalResponse res = new SavingsGoalResponse();
        res.setId(goal.getId());
        res.setName(goal.getName());
        res.setTargetAmount(goal.getTargetAmount());
        res.setSavedAmount(goal.getSavedAmount());
        res.setIcon(goal.getIcon());
        res.setColor(goal.getColor());
        res.setCompleted(goal.isCompleted());

        double progress = (goal.getTargetAmount() > 0)
                ? Math.min((goal.getSavedAmount() / goal.getTargetAmount()) * 100.0, 100.0)
                : 0.0;
        res.setProgressPercent(Math.round(progress * 10.0) / 10.0);

        return res;
    }
}