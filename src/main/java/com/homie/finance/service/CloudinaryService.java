package com.homie.finance.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    public String uploadImage(MultipartFile file) {
        try {
            // Ném file lên Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

            // Lấy cái đường link HTTPS an toàn mang về
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi tải ảnh lên Cloudinary: " + e.getMessage());
        }
    }

    // 🔴 2.Hàm Xóa ảnh trên mây
    public void deleteImage(String imageUrl) {
        try {
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                // Gọi hàm destroy của Cloudinary để dọn rác
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (IOException e) {
            System.err.println("Không thể xóa ảnh cũ trên mây: " + e.getMessage());
        }
    }

    private String extractPublicId(String imageUrl) {
        try {
            String[] parts = imageUrl.split("/");
            String lastPart = parts[parts.length - 1]; // Lấy khúc cuối cùng "abc-123.jpg"
            int dotIndex = lastPart.lastIndexOf(".");
            if (dotIndex != -1) {
                return lastPart.substring(0, dotIndex); // Bỏ đuôi .jpg đi
            }
            return lastPart;
        } catch (Exception e) {
            return null;
        }
    }

}