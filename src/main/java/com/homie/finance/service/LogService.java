package com.homie.finance.service;

import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.TransactionLog;
import com.homie.finance.entity.User;
import com.homie.finance.repository.GroupSpaceRepository;
import com.homie.finance.repository.TransactionLogRepository;
import com.homie.finance.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LogService {

    @Autowired
    private TransactionLogRepository transactionLogRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveLog(String txId, String username, String action, String details) {
        TransactionLog log = new TransactionLog();
        log.setTransactionId(txId);
        log.setUpdatedBy(username);
        log.setUpdatedAt(LocalDateTime.now());
        log.setAction(action);
        log.setDetails(details);

        transactionLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<TransactionLog> getGroupActivityLogs(String groupId) {
        User currentUser = securityUtils.getCurrentUser();

        GroupSpace group = groupSpaceRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Homie ơi, không tìm thấy nhóm này!"));

        if (!group.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Quyền truy cập bị từ chối: Chỉ trưởng nhóm mới có quyền xem nhật ký hoạt động!");
        }

        return transactionLogRepository.findAllLogsByGroupId(groupId);
    }

    public List<TransactionLog> getLogsForTransaction(String transactionId) {
        // Chỉ cần gọi hàm repo là xong
        return transactionLogRepository.findByTransactionIdOrderByUpdatedAtDesc(transactionId);
    }
}