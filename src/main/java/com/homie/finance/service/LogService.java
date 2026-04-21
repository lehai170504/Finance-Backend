package com.homie.finance.service;

import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.TransactionLog;
import com.homie.finance.entity.User;
import com.homie.finance.repository.GroupSpaceRepository;
import com.homie.finance.repository.TransactionLogRepository;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final TransactionLogRepository transactionLogRepository;
    private final GroupSpaceRepository groupSpaceRepository;
    private final SecurityUtils securityUtils;

    /**
     * Lưu nhật ký hoạt động
     * Dùng REQUIRES_NEW để đảm bảo Log luôn được lưu kể cả khi giao dịch chính bị Rollback
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(String txId, String username, String action, String details) {
        try {
            TransactionLog logEntry = new TransactionLog();
            logEntry.setTransactionId(txId);
            logEntry.setUpdatedBy(username);
            logEntry.setUpdatedAt(LocalDateTime.now());
            logEntry.setAction(action);
            logEntry.setDetails(details);

            transactionLogRepository.save(logEntry);
        } catch (Exception e) {
            // Log lỗi ghi log để không làm sập luồng xử lý chính
            log.error("Không thể lưu Transaction Log cho TxID: {}. Lỗi: {}", txId, e.getMessage());
        }
    }

    /**
     * Lấy lịch sử hoạt động của nhóm (Chỉ chủ nhóm mới xem được)
     */
    @Transactional(readOnly = true)
    public List<TransactionLog> getGroupActivityLogs(String groupId) {
        User currentUser = securityUtils.getCurrentUser();

        GroupSpace group = groupSpaceRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Homie ơi, không tìm thấy nhóm này!"));

        if (!group.getOwner().getId().equals(currentUser.getId())) {
            log.warn("Người dùng {} cố gắng truy cập trái phép log của nhóm {}", currentUser.getUsername(), groupId);
            throw new RuntimeException("Quyền truy cập bị từ chối: Chỉ trưởng nhóm mới có quyền xem nhật ký hoạt động!");
        }

        return transactionLogRepository.findAllLogsByGroupId(groupId);
    }

    /**
     * Lấy lịch sử chỉnh sửa của một giao dịch cụ thể
     */
    @Transactional(readOnly = true)
    public List<TransactionLog> getLogsForTransaction(String transactionId) {
        return transactionLogRepository.findByTransactionIdOrderByUpdatedAtDesc(transactionId);
    }
}