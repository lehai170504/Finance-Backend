package com.homie.finance.service;

import com.homie.finance.dto.transaction.TransactionResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelService {

    public ByteArrayInputStream exportToExcel(List<TransactionResponse> transactions) {
        // Tạo một cuốn sổ Excel mới tinh
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Tạo 1 trang (sheet) tên là Lịch sử giao dịch
            Sheet sheet = workbook.createSheet("Lich_Su_Giao_Dich");

            // 1. Dựng Hàng Tiêu Đề (Header) ở dòng 0
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Ngày", "Danh mục", "Loại", "Số tiền (VNĐ)", "Ghi chú"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // 2. Đổ dữ liệu thật vào từ dòng 1 trở đi
            int rowIdx = 1;
            for (TransactionResponse t : transactions) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(t.getDate() != null ? t.getDate().toString() : "");
                row.createCell(1).setCellValue(t.getCategoryName());
                row.createCell(2).setCellValue("EXPENSE".equals(t.getType()) ? "Chi tiêu" : "Thu nhập");

                row.createCell(3).setCellValue(t.getAmount());
                row.createCell(4).setCellValue(t.getNote() != null ? t.getNote() : "");
            }

            // 3. Đóng gói và xuất xưởng
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi tạo file Excel: " + e.getMessage());
        }
    }
}