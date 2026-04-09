package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.dto.WalletRequest;
import com.homie.finance.entity.Wallet;
import com.homie.finance.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@Tag(name = "9. Wallet", description = "Quản lý nguồn tiền (Ví cá nhân, Thẻ ngân hàng...)")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @GetMapping
    @Operation(summary = "Lấy danh sách Ví", description = "Load tất cả các ví của user đang đăng nhập.")
    public ApiResponse<List<Wallet>> getWallets() {
        return new ApiResponse<>(200, "Danh sách ví", walletService.getMyWallets());
    }

    @PostMapping
    @Operation(summary = "Tạo Ví mới", description = "FE gửi name, balance, color. User tự động gán từ Token.")
    public ApiResponse<Wallet> create(@RequestBody WalletRequest wallet) {
        return new ApiResponse<>(201, "Đã tạo ví mới", walletService.createWallet(wallet));
    }

    // Cập nhật thông tin ví
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật ví", description = "Đổi tên, màu hoặc điều chỉnh số dư ví.")
    public ApiResponse<Wallet> update(@PathVariable String id, @RequestBody WalletRequest walletRequest) {
        return new ApiResponse<>(200, "Đã cập nhật ví", walletService.updateWallet(id, walletRequest));
    }

    // Xóa ví (Service đã check số dư = 0 mới cho xóa)
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa ví", description = "Xóa ví khỏi hệ thống. Chỉ cho phép xóa khi số dư bằng 0.")
    public ApiResponse<String> delete(@PathVariable String id) {
        walletService.deleteWallet(id);
        return new ApiResponse<>(200, "Đ đã xóa ví thành công", null);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Chuyển tiền giữa các Ví", description = "Dịch chuyển số dư từ ví A sang ví B (Nội bộ User).")
    public ApiResponse<String> transfer(@RequestParam String fromId, @RequestParam String toId, @RequestParam Double amount) {
        walletService.transferMoney(fromId, toId, amount);
        return new ApiResponse<>(200, "Chuyển tiền thành công!", null);
    }

    @GetMapping("/total-balance")
    @Operation(summary = "Lấy Tổng số dư hiện tại")
    public ApiResponse<Double> getTotalBalance() {
        return new ApiResponse<>(200, "Tổng số dư thực tế", walletService.getTotalBalance());
    }
}