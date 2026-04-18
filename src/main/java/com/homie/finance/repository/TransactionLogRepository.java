package com.homie.finance.repository;

import com.homie.finance.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, String> {
    List<TransactionLog> findByTransactionIdOrderByUpdatedAtDesc(String transactionId);

    @Query("SELECT l FROM TransactionLog l WHERE l.transactionId IN " +
            "(SELECT t.id FROM Transaction t WHERE t.groupSpace.id = :groupId) " +
            "ORDER BY l.updatedAt DESC")
    List<TransactionLog> findAllLogsByGroupId(@Param("groupId") String groupId);
}