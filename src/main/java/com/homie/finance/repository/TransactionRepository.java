package com.homie.finance.repository;

import com.homie.finance.dto.statistic.StatisticResponse;
import com.homie.finance.dto.transaction.CashFlowResponse;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

        // Lấy rác (isDeleted = true)
        @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.isDeleted = true")
        List<Transaction> findTrashByUser(@Param("userId") String userId);

        // Tìm bao gồm cả rác
        @Query("SELECT t FROM Transaction t WHERE t.id = :id")
        Optional<Transaction> findByIdIncludingTrash(@Param("id") String id);

        @EntityGraph(attributePaths = {"category", "wallet"})
        Page<Transaction> findByUser(User user, Pageable pageable);

        @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t WHERE t.category.type = :type AND t.user = :user")
        Double sumAmountByUserAndType(@Param("user") User user, @Param("type") String type);

        @EntityGraph(attributePaths = {"category"})
        List<Transaction> findByUserAndCategoryType(User user, String type);

        @EntityGraph(attributePaths = {"category", "wallet"})
        Page<Transaction> findByUserAndNoteContainingIgnoreCase(User user, String keyword, Pageable pageable);

        // Thống kê theo Category
        @Query("SELECT new com.homie.finance.dto.statistic.StatisticResponse(c.name, c.type, SUM(t.amount)) " +
                "FROM Transaction t JOIN t.category c " +
                "WHERE t.user = :user AND t.date BETWEEN :startDate AND :endDate " +
                "GROUP BY c.name, c.type")
        List<StatisticResponse> getCategoryStatistics(
                @Param("user") User user,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate);

        // Thống kê dòng tiền (CashFlow)
        @Query("SELECT new com.homie.finance.dto.transaction.CashFlowResponse(t.date, " +
                "SUM(CASE WHEN c.type = 'INCOME' THEN t.amount ELSE 0.0 END), " +
                "SUM(CASE WHEN c.type = 'EXPENSE' THEN t.amount ELSE 0.0 END)) " +
                "FROM Transaction t JOIN t.category c " +
                "WHERE t.user = :user AND t.date BETWEEN :startDate AND :endDate " +
                "GROUP BY t.date " +
                "ORDER BY t.date ASC")
        List<CashFlowResponse> getCashFlowStatistics(
                @Param("user") User user,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate);

        @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t " +
                "WHERE t.user = :user " +
                "AND t.category.type = 'EXPENSE' " +
                "AND t.date BETWEEN :startDate AND :endDate")
        Double sumTotalExpenseByUser(@Param("user") User user,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

        @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t WHERE t.user = :user AND t.category = :category AND t.date BETWEEN :startDate AND :endDate")
        Double sumAmountByUserAndCategoryAndDateBetween(
                @Param("user") User user,
                @Param("category") Category category,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate);

        @EntityGraph(attributePaths = {"category", "user"})
        Page<Transaction> findByGroupSpaceId(String groupSpaceId, Pageable pageable);

        @Query("SELECT t.category FROM Transaction t " +
                "WHERE t.user = :user " +
                "AND LOWER(t.note) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "ORDER BY t.date DESC")
        List<Category> findSuggestedCategory(@Param("user") User user, @Param("keyword") String keyword, Pageable pageable);

        @EntityGraph(attributePaths = {"category", "user"})
        List<Transaction> findAllByGroupSpaceId(String groupSpaceId);


        void deleteByGroupSpace(GroupSpace groupSpace);
}