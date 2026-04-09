package com.homie.finance.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrUtils {

    // Hàm bóc tách số tiền lớn nhất (thường là tổng bill)
    public static Double extractAmount(String text) {
        if (text == null || text.isEmpty()) return null;

        // Regex tìm các con số nằm sau các chữ như "Total", "Tổng cộng", "Thanh toán"
        // Khớp với định dạng: 150.000, 150,000, 150000
        Pattern pattern = Pattern.compile("(?i)(?:total|tổng cộng|thanh toán|số tiền)[:\\s]*([\\d,.]{3,})");
        Matcher matcher = pattern.matcher(text.replace("\n", " "));

        double maxAmount = 0.0;
        while (matcher.find()) {
            try {
                // Xóa dấu chấm, dấu phẩy để parse thành số
                String cleanAmount = matcher.group(1).replaceAll("[.,]", "");
                double val = Double.parseDouble(cleanAmount);
                if (val > maxAmount) maxAmount = val; // Lấy số to nhất
            } catch (Exception ignored) {}
        }
        return maxAmount > 0 ? maxAmount : null;
    }

    // Hàm lấy tên quán (Thường là dòng đầu tiên của hóa đơn)
    public static String extractStoreName(String text) {
        if (text == null || text.isEmpty()) return "Hóa đơn mua sắm";
        String[] lines = text.split("\n");
        if (lines.length > 0 && lines[0].length() > 3) {
            return lines[0].trim(); // Trả về dòng đầu tiên làm Ghi chú
        }
        return "Hóa đơn mua sắm";
    }
}