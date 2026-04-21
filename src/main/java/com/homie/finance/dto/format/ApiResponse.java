package com.homie.finance.dto.format;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private int status;      // Mã trạng thái (VD: 200 là OK, 400 là Lỗi)
    private String message;  // Lời nhắn cho Frontend (VD: "Thành công!")
    private T data;          // Dữ liệu thực tế (Có thể là List, là 1 Object, v.v...)
}