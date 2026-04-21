package com.homie.finance.dto.format;

import lombok.Data;
import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> content;      // Chứa danh sách dữ liệu (VD: 10 cái giao dịch)
    private int currentPage;      // Đang ở trang số mấy
    private int totalPages;       // Tổng cộng có bao nhiêu trang
    private long totalElements;   // Tổng cộng có bao nhiêu dòng trong Database

    // Constructor để code bên Service gọn hơn
    public PageResponse(List<T> content, int currentPage, int totalPages, long totalElements) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }
}