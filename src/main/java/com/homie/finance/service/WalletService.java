package com.homie.finance.service;

import com.homie.finance.dto.WalletRequest;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private com.homie.finance.repository.TransactionRepository transactionRepository;
    @Autowired
    private com.homie.finance.repository.CategoryRepository categoryRepository;

    private User getCurrentLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng!"));
    }

    private void validatePositiveAmount(Double amount, String message) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public Wallet createWallet(WalletRequest request) {
        User currentUser = getCurrentLoggedInUser();
        Wallet newWallet = new Wallet();
        newWallet.setName(request.getName());
        newWallet.setBalance(request.getBalance() != null ? request.getBalance() : 0.0);
        newWallet.setColor(request.getColor());
        newWallet.setUser(currentUser);
        return walletRepository.save(newWallet);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public List<Wallet> getMyWallets() {
        return walletRepository.findByUser(getCurrentLoggedInUser());
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public void transferMoney(String fromId, String toId, Double amount) {
        User currentUser = getCurrentLoggedInUser();

        validatePositiveAmount(amount, "Số tiền chuyển phải lớn hơn 0!");
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Không thể chuyển tiền trong cùng một ví!");
        }

        if (!walletRepository.existsByIdAndUser(fromId, currentUser)) {
            throw new IllegalArgumentException("Ví nguồn không tồn tại hoặc không thuộc về bạn!");
        }

        if (!walletRepository.existsByIdAndUser(toId, currentUser)) {
            throw new IllegalArgumentException("Ví đích không thuộc về bạn!");
        }

        Wallet fromWallet = walletRepository.findById(fromId).orElseThrow();
        Wallet toWallet = walletRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví đích"));

        if (fromWallet.isDeleted()) {
            throw new IllegalArgumentException("Ví nguồn đã bị xóa!");
        }

        if (toWallet.isDeleted()) {
            throw new IllegalArgumentException("Ví đích đã bị xóa!");
        }

        if (fromWallet.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư ví nguồn không đủ!");
        }

        fromWallet.setBalance(fromWallet.getBalance() - amount);
        toWallet.setBalance(toWallet.getBalance() + amount);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Record transactions for transfer
        com.homie.finance.entity.Category transferOutCategory = categoryRepository
                .findByNameIgnoreCase("Chuyển tiền đi")
                .orElseGet(() -> {
                    com.homie.finance.entity.Category c = new com.homie.finance.entity.Category();
                    c.setName("Chuyển tiền đi");
                    c.setType("EXPENSE");
                    c.setIcon("swap_horiz");
                    return categoryRepository.save(c);
                });

        com.homie.finance.entity.Category transferInCategory = categoryRepository.findByNameIgnoreCase("Nhận tiền về")
                .orElseGet(() -> {
                    com.homie.finance.entity.Category c = new com.homie.finance.entity.Category();
                    c.setName("Nhận tiền về");
                    c.setType("INCOME");
                    c.setIcon("swap_horiz");
                    return categoryRepository.save(c);
                });

        java.time.LocalDate today = java.time.LocalDate.now();

        com.homie.finance.entity.Transaction txOut = new com.homie.finance.entity.Transaction();
        txOut.setAmount(amount);
        txOut.setNote("Chuyển tiền sang ví " + toWallet.getName());
        txOut.setDate(today);
        txOut.setCategory(transferOutCategory);
        txOut.setWallet(fromWallet);
        txOut.setUser(currentUser);
        txOut.setDeleted(false);
        txOut.setType("EXPENSE");
        transactionRepository.save(txOut);

        com.homie.finance.entity.Transaction txIn = new com.homie.finance.entity.Transaction();
        txIn.setAmount(amount);
        txIn.setNote("Nhận tiền từ ví " + fromWallet.getName());
        txIn.setDate(today);
        txIn.setCategory(transferInCategory);
        txIn.setWallet(toWallet);
        txIn.setUser(currentUser);
        txIn.setDeleted(false);
        txIn.setType("INCOME");
        transactionRepository.save(txIn);
    }

    public Double getTotalBalance() {
        Double total = walletRepository.sumBalanceByUser(getCurrentLoggedInUser());
        return total != null ? total : 0.0;
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public Wallet updateWallet(String id, WalletRequest request) {
        User currentUser = getCurrentLoggedInUser();

        if (!walletRepository.existsByIdAndUser(id, currentUser)) {
            throw new RuntimeException("Vi khong ton tai hoac ban khong co quyen sua!");
        }

        Wallet wallet = walletRepository.findById(id).orElseThrow();
        wallet.setName(request.getName());
        wallet.setColor(request.getColor());

        if (request.getBalance() != null && !request.getBalance().equals(wallet.getBalance())) {
            throw new IllegalArgumentException(
                    "Không được sửa trực tiếp số dư ví. Hãy tạo giao dịch hoặc chuyển tiền.");
        }

        return walletRepository.save(wallet);
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public void deleteWallet(String id) {
        User currentUser = getCurrentLoggedInUser();

        if (!walletRepository.existsByIdAndUser(id, currentUser)) {
            throw new RuntimeException("Ví không tồn tại hoặc bạn không có quyền xóa!");
        }

        Wallet wallet = walletRepository.findById(id).orElseThrow();

        if (wallet.getBalance() != null && wallet.getBalance() > 0) {
            throw new IllegalArgumentException(
                    "Ví còn tiền (" + wallet.getBalance() + "). Phải chuyển hết tiền trước khi xóa.");
        }

        wallet.setDeleted(true);
        walletRepository.save(wallet);
    }
}
