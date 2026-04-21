package com.homie.finance.controller;

import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.dto.wallet.TransferRequest;
import com.homie.finance.dto.wallet.WalletRequest;
import com.homie.finance.dto.wallet.WalletResponse;
import com.homie.finance.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Tag(name = "9. Wallet", description = "Quản lý nguồn tiền (Ví cá nhân, Thẻ ngân hàng...)")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @Operation(summary = "Lấy danh sách Ví", description = "Load tất cả các ví của user đang đăng nhập.")
    public ApiResponse<List<WalletResponse>> getWallets() {
        return new ApiResponse<>(200, "Danh sách ví", walletService.getMyWallets());
    }

    @PostMapping
    @Operation(summary = "Tạo Ví mới", description = "FE gửi name, balance, color. User tự động gán từ Token.")
    public ApiResponse<WalletResponse> create(@RequestBody WalletRequest wallet) {
        return new ApiResponse<>(201, "Đã tạo ví mới", walletService.createWallet(wallet));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật ví", description = "Đổi tên, màu hoặc điều chỉnh số dư ví.")
    public ApiResponse<WalletResponse> update(@PathVariable String id, @RequestBody WalletRequest walletRequest) {
        return new ApiResponse<>(200, "Đã cập nhật ví", walletService.updateWallet(id, walletRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa ví", description = "Xóa ví khỏi hệ thống. Chỉ cho phép xóa khi số dư bằng 0.")
    public ApiResponse<String> delete(@PathVariable String id) {
        walletService.deleteWallet(id);
        return new ApiResponse<>(200, "Đã xóa ví thành công", "ID: " + id);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Chuyển tiền giữa các Ví", description = "Dịch chuyển số dư từ ví A sang ví B (Nội bộ User).")
    public ApiResponse<String> transfer(@RequestBody TransferRequest request) {
        walletService.transferMoney(request.getFromId(), request.getToId(), request.getAmount());
        return new ApiResponse<>(200, "Chuyển tiền thành công!", null);
    }

    @GetMapping("/total-balance")
    @Operation(summary = "Lấy Tổng số dư hiện tại")
    public ApiResponse<Double> getTotalBalance() {
        return new ApiResponse<>(200, "Tổng số dư thực tế", walletService.getTotalBalance());
    }
}