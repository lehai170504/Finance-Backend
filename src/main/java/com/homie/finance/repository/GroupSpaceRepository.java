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
    List<GroupSpace> findByMembersContaining(User user);

    // Tìm nhóm bằng mã mời
    Optional<GroupSpace> findByInviteCode(String inviteCode);

    boolean existsByNameAndOwner(String name, User owner);

    // Check xem một User có thuộc Group này không (trả về boolean)
    boolean existsByIdAndMembersContaining(String groupId, User user);

    @Query("SELECT g FROM GroupSpace g LEFT JOIN FETCH g.members WHERE g.id = :id")
    Optional<GroupSpace> findByIdWithMembers(@Param("id") String id);
}