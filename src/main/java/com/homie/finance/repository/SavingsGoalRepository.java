package com.homie.finance.repository;

import com.homie.finance.entity.SavingsGoal;
import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, String> {

    List<SavingsGoal> findByUserOrderByCompletedAsc(User user);

    Optional<SavingsGoal> findByIdAndUser(String id, User user);
}
