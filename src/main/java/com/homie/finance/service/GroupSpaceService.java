package com.homie.finance.service;

import com.homie.finance.dto.group.GroupRequest;
import com.homie.finance.dto.group.GroupSpaceResponse;
import com.homie.finance.dto.group.UserSummaryDto;
import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.User;
import com.homie.finance.repository.GroupSpaceRepository;
import com.homie.finance.repository.TransactionRepository;
import com.homie.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupSpaceService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final UserRepository userRepository;
    private final com.homie.finance.repository.DebtRepository debtRepository;
    private final TransactionRepository transactionRepository;

    // Lấy User hiện tại từ Token (Giữ nguyên, dùng nội bộ)
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    // 1. Tạo nhóm mới (Đổi String -> GroupRequest, Trả về GroupSpaceResponse)
    @Transactional
    public GroupSpaceResponse createGroup(GroupRequest request) {
        User me = getCurrentUser();

        if (groupSpaceRepository.existsByNameAndOwner(request.getName(), me)) {
            throw new IllegalArgumentException("Homie đã có một nhóm tên '" + request.getName() + "' rồi!");
        }

        GroupSpace group = new GroupSpace();
        group.setName(request.getName());
        group.setOwner(me);
        group.setInviteCode(UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        Set<User> members = new HashSet<>();
        members.add(me);
        group.setMembers(members);

        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup); // Map sang DTO
    }

    // 2. Tham gia nhóm bằng Mã Code
    @Transactional
    public GroupSpaceResponse joinGroup(String inviteCode) {
        User me = getCurrentUser();

        GroupSpace group = groupSpaceRepository.findByInviteCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Mã mời không hợp lệ!"));

        if (group.getMembers().contains(me)) {
            throw new IllegalArgumentException("Homie đã ở trong nhóm này rồi!");
        }

        group.getMembers().add(me);
        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup); // Map sang DTO
    }

    // 3. Lấy danh sách nhóm của tôi
    @Transactional(readOnly = true)
    public List<GroupSpaceResponse> getMyGroups() {
        User me = getCurrentUser();
        List<GroupSpace> groups = groupSpaceRepository.findByMembersContainingWithMembers(me);

        // Convert cả list Entity sang list DTO
        return groups.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // 4. Chỉnh sửa tên nhóm
    @Transactional
    public GroupSpaceResponse updateGroup(String groupId, GroupRequest request) {
        User me = getCurrentUser();

        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (!group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chỉ chủ nhóm mới có quyền đổi tên!");
        }

        if (groupSpaceRepository.existsByNameAndOwner(request.getName(), me)) {
            throw new IllegalArgumentException("Tên nhóm này homie đã sử dụng rồi!");
        }

        group.setName(request.getName());
        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup);
    }

    // 5. Giải tán nhóm (Return void nên không cần sửa)
    @Transactional
    public void deleteGroup(String groupId) {
        // ... (Giữ nguyên logic cũ của ông)
        User me = getCurrentUser();
        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (!group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chỉ chủ nhóm mới được giải tán nhóm!");
        }

        group.getMembers().clear();
        groupSpaceRepository.save(group);

        debtRepository.deleteByGroup(group);
        transactionRepository.deleteByGroupSpace(group);
        groupSpaceRepository.delete(group);
    }

    // 6. Rời khỏi nhóm (Return void nên không cần sửa)
    @Transactional
    public void leaveGroup(String groupId) {
        // ... (Giữ nguyên logic cũ của ông)
        User me = getCurrentUser();
        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Nhóm không tồn tại!"));

        if (group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chủ nhóm không được rời, chỉ có thể giải tán nhóm!");
        }

        if (!group.getMembers().contains(me)) {
            throw new IllegalArgumentException("Homie không phải thành viên nhóm này!");
        }

        if (debtRepository.existsByGroupAndDebtorAndIsSettledFalse(group, me) ||
                debtRepository.existsByGroupAndCreditorAndIsSettledFalse(group, me)) {
            throw new IllegalArgumentException(
                    "Bạn cần thanh toán hoặc được thanh toán hết các khoản nợ trong nhóm trước khi rời đi!");
        }

        group.getMembers().remove(me);
        groupSpaceRepository.save(group);
    }

    // 7. Lấy chi tiết nhóm
    @Transactional(readOnly = true)
    public GroupSpaceResponse getGroupById(String groupId) {
        User me = getCurrentUser();

        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (!group.getMembers().contains(me)) {
            throw new IllegalArgumentException("Homie không có quyền xem nhóm này!");
        }

        return mapToResponse(group);
    }

    // ==========================================
    // HÀM CHUYỂN ĐỔI ENTITY -> DTO
    // ==========================================
    private GroupSpaceResponse mapToResponse(GroupSpace group) {
        // Map Owner
        UserSummaryDto ownerDto = UserSummaryDto.builder()
                .id(group.getOwner().getId())
                .username(group.getOwner().getUsername())
                .email(group.getOwner().getEmail())
                .avatarUrl(group.getOwner().getAvatarUrl())
                .build();

        // Map danh sách Members
        List<UserSummaryDto> memberDtos = group.getMembers().stream()
                .map(m -> UserSummaryDto.builder()
                        .id(m.getId())
                        .username(m.getUsername())
                        .email(m.getEmail())
                        .avatarUrl(m.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());

        // Build Response tổng
        return GroupSpaceResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .inviteCode(group.getInviteCode())
                .createdAt(group.getCreatedAt())
                .owner(ownerDto)
                .members(memberDtos)
                .build();
    }
}