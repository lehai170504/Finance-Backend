package com.homie.finance.service;

import com.homie.finance.dto.wallet.WalletRequest;
import com.homie.finance.dto.wallet.WalletResponse;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import com.homie.finance.entity.Wallet;
import com.homie.finance.repository.CategoryRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.repository.WalletRepository;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SecurityUtils securityUtils;

    private void validatePositiveAmount(Double amount, String message) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public WalletResponse createWallet(WalletRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        Wallet newWallet = new Wallet();
        newWallet.setName(request.getName());
        newWallet.setBalance(request.getBalance() != null ? request.getBalance() : 0.0);
        newWallet.setColor(request.getColor());
        newWallet.setUser(currentUser);

        return mapToDto(walletRepository.save(newWallet));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public List<WalletResponse> getMyWallets() {
        return walletRepository.findByUser(securityUtils.getCurrentUser())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public void transferMoney(String fromId, String toId, Double amount) {
        User currentUser = securityUtils.getCurrentUser();

        validatePositiveAmount(amount, "Số tiền chuyển phải lớn hơn 0!");
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Không thể chuyển tiền trong cùng một ví!");
        }

        Wallet fromWallet = walletRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Ví nguồn không tồn tại!"));

        Wallet toWallet = walletRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Ví đích không tồn tại!"));

        // Check quyền sở hữu
        if (!fromWallet.getUser().getId().equals(currentUser.getId()) ||
                !toWallet.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Ví không thuộc quyền sở hữu của bạn!");
        }

        if (fromWallet.isDeleted() || toWallet.isDeleted()) {
            throw new IllegalArgumentException("Không thể chuyển tiền liên quan đến ví đã xóa!");
        }

        if (fromWallet.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư ví nguồn không đủ!");
        }

        // Thực hiện trừ/cộng tiền
        fromWallet.setBalance(fromWallet.getBalance() - amount);
        toWallet.setBalance(toWallet.getBalance() + amount);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Tạo giao dịch đối ứng (Transfer logic)
        saveTransferTransactions(fromWallet, toWallet, amount, currentUser);

        log.info("Chuyển tiền thành công: {} từ '{}' sang '{}'", amount, fromWallet.getName(), toWallet.getName());
    }

    private void saveTransferTransactions(Wallet fromWallet, Wallet toWallet, Double amount, User user) {
        // 1. Lấy hoặc tạo Category "Chuyển tiền" (Dùng hàm dùng chung cho gọn)
        Category transferOutCat = getOrCreateCategory("Chuyển tiền đi", "EXPENSE", "swap_horiz");
        Category transferInCat = getOrCreateCategory("Nhận tiền về", "INCOME", "swap_horiz");

        LocalDate today = LocalDate.now();

        // 2. Tạo giao dịch chuyển đi (txOut)
        Transaction txOut = new Transaction();
        txOut.setAmount(amount);
        txOut.setNote("Chuyển sang ví " + toWallet.getName());
        txOut.setDate(today);
        txOut.setCategory(transferOutCat);
        txOut.setWallet(fromWallet);
        txOut.setUser(user);
        // txOut.setType("EXPENSE"); // Nếu Entity của ông có field type thì set thêm vào đây
        transactionRepository.save(txOut);

        // 3. Tạo giao dịch nhận về (txIn)
        Transaction txIn = new Transaction();
        txIn.setAmount(amount);
        txIn.setNote("Nhận từ ví " + fromWallet.getName());
        txIn.setDate(today);
        txIn.setCategory(transferInCat);
        txIn.setWallet(toWallet);
        txIn.setUser(user);
        // txIn.setType("INCOME");
        transactionRepository.save(txIn);
    }

    private Category getOrCreateCategory(String name, String type, String icon) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> categoryRepository.save(new Category(name, type, icon)));
    }

    public Double getTotalBalance() {
        Double total = walletRepository.sumBalanceByUser(securityUtils.getCurrentUser());
        return total != null ? total : 0.0;
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public WalletResponse updateWallet(String id, WalletRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ví không tồn tại!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Bạn không có quyền sửa ví này!");
        }

        wallet.setName(request.getName());
        wallet.setColor(request.getColor());

        if (request.getBalance() != null && !request.getBalance().equals(wallet.getBalance())) {
            throw new IllegalArgumentException("Không được sửa trực tiếp số dư. Hãy tạo giao dịch!");
        }

        return mapToDto(walletRepository.save(wallet));
    }

    @Transactional
    @CacheEvict(value = "wallets", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public void deleteWallet(String id) {
        User currentUser = securityUtils.getCurrentUser();
        Wallet wallet = walletRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví!"));

        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Bạn không có quyền xóa ví này!");
        }

        if (wallet.getBalance() > 0) {
            throw new IllegalArgumentException("Ví còn tiền, hãy chuyển hết tiền đi trước khi xóa!");
        }

        wallet.setDeleted(true);
        walletRepository.save(wallet);
    }

    // --- MAPPER ---
    private WalletResponse mapToDto(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .name(wallet.getName())
                .balance(wallet.getBalance())
                .color(wallet.getColor())
                .build();
    }
}