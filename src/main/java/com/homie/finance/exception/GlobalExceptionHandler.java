package com.homie.finance.exception;

import com.homie.finance.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice // Đánh dấu đây là Trạm kiểm soát lỗi toàn hệ thống
public class GlobalExceptionHandler {

    // 1. BẮT CÁC LỖI LOGIC DO MÌNH CHỦ ĐỘNG NÉM RA (IllegalArgumentException,
    // IllegalStateException)
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleLogicError(Exception ex) {
        return new ApiResponse<>(400, ex.getMessage(), null);
    }

    // 2. BẮT LỖI NGƯỜI DÙNG NHẬP THIẾU THÔNG TIN (Do thằng @Valid bắt được)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidationError(MethodArgumentNotValidException ex) {
        // Gom tất cả các lỗi nhập liệu (VD: để trống mật khẩu, sai định dạng email) lại
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return new ApiResponse<>(400, "Dữ liệu nhập vào chưa chuẩn xác!", errors);
    }

    // 3. BẮT TẤT CẢ CÁC LỖI KHÔNG LƯỜNG TRƯỚC ĐƯỢC (Sập server, đứt cáp...)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Mã 500
    public ApiResponse<Object> handleUnwantedException(Exception ex) {
        // Log lỗi ra console để dev fix
        ex.printStackTrace();
        return new ApiResponse<>(500, "Hệ thống đang bảo trì hoặc gặp sự cố, homie quay lại sau nhé!", null);
    }
}