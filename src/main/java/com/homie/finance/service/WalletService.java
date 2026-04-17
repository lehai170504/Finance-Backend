package com.homie.finance.service;

import com.homie.finance.dto.WalletRequest;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private UserRepository userRepository;

    private User getCurrentLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Loi xac thuc nguoi dung!"));
    }

    private void validatePositiveAmount(Double amount, String message) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @Transactional
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
    public List<Wallet> getMyWallets() {
        return walletRepository.findByUser(getCurrentLoggedInUser());
    }

    @Transactional
    public void transferMoney(String fromId, String toId, Double amount) {
        User currentUser = getCurrentLoggedInUser();

        validatePositiveAmount(amount, "So tien chuyen phai lon hon 0!");
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Khong the chuyen tien trong cung mot vi!");
        }

        if (!walletRepository.existsByIdAndUser(fromId, currentUser)) {
            throw new IllegalArgumentException("Vi nguon khong ton tai hoac khong thuoc ve ban!");
        }

        if (!walletRepository.existsByIdAndUser(toId, currentUser)) {
            throw new IllegalArgumentException("Vi dich khong thuoc ve ban!");
        }

        Wallet fromWallet = walletRepository.findById(fromId).orElseThrow();
        Wallet toWallet = walletRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay vi dich"));

        if (fromWallet.getBalance() < amount) {
            throw new IllegalArgumentException("So du vi nguon khong du!");
        }

        fromWallet.setBalance(fromWallet.getBalance() - amount);
        toWallet.setBalance(toWallet.getBalance() + amount);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
    }

    public Double getTotalBalance() {
        Double total = walletRepository.sumBalanceByUser(getCurrentLoggedInUser());
        return total != null ? total : 0.0;
    }

    @Transactional
    public Wallet updateWallet(String id, WalletRequest request) {
        User currentUser = getCurrentLoggedInUser();

        if (!walletRepository.existsByIdAndUser(id, currentUser)) {
            throw new RuntimeException("Vi khong ton tai hoac ban khong co quyen sua!");
        }

        Wallet wallet = walletRepository.findById(id).orElseThrow();
        wallet.setName(request.getName());
        wallet.setColor(request.getColor());

        if (request.getBalance() != null && !request.getBalance().equals(wallet.getBalance())) {
            throw new IllegalArgumentException("Khong duoc sua truc tiep so du vi. Hay tao giao dich hoac chuyen tien.");
        }

        return walletRepository.save(wallet);
    }

    @Transactional
    public void deleteWallet(String id) {
        User currentUser = getCurrentLoggedInUser();

        if (!walletRepository.existsByIdAndUser(id, currentUser)) {
            throw new RuntimeException("Vi khong ton tai hoac ban khong co quyen xoa!");
        }

        Wallet wallet = walletRepository.findById(id).orElseThrow();

        if (wallet.getBalance() != null && wallet.getBalance() > 0) {
            throw new IllegalArgumentException("Vi van con tien (" + wallet.getBalance() + "). Phai chuyen het tien truoc khi xoa.");
        }

        walletRepository.delete(wallet);
    }
}
