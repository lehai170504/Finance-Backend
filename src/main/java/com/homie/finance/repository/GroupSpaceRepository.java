package com.homie.finance.repository;

import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupSpaceRepository extends JpaRepository<GroupSpace, String> {

    // Tìm các nhóm mà User này là thành viên
    @Query("SELECT DISTINCT g FROM GroupSpace g LEFT JOIN FETCH g.members WHERE :user MEMBER OF g.members")
    List<GroupSpace> findByMembersContainingWithMembers(@Param("user") User user);

    // Tìm nhóm bằng mã mời
    @Query("SELECT g FROM GroupSpace g LEFT JOIN FETCH g.members WHERE g.inviteCode = :inviteCode")
    Optional<GroupSpace> findByInviteCode(@Param("inviteCode") String inviteCode);

    boolean existsByNameAndOwner(String name, User owner);

    @Query("SELECT g FROM GroupSpace g LEFT JOIN FETCH g.members WHERE g.id = :id")
    Optional<GroupSpace> findByIdWithMembers(@Param("id") String id);
}