package com.homie.finance.job;

import com.homie.finance.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistCleanupJob {

    private final BlacklistedTokenRepository blacklistRepository;

    /**
     * Tự động dọn dẹp Blacklist Token
     * Chạy vào lúc 03:00 sáng mỗi ngày (cron = "0 0 3 * * ?")
     * Hoặc chạy mỗi tiếng một lần để test: fixedRate = 3600000
     */
    @Transactional
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTokens() {
        log.info("Đang bắt đầu dọn dẹp danh sách đen Token...");

        try {
            blacklistRepository.deleteByExpiryDateBefore(Instant.now());

            log.info("Đã dọn dẹp sạch sẽ các Token hết hạn khỏi Database.");
        } catch (Exception e) {
            log.error("Lỗi khi dọn dẹp Blacklist: {}", e.getMessage());
        }
    }
}