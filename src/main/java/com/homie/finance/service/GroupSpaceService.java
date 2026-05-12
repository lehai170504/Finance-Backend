package com.homie.finance.service;

import com.homie.finance.dto.group.GroupRequest;
import com.homie.finance.dto.group.GroupSpaceResponse;
import com.homie.finance.dto.group.UserSummaryDto;
import com.homie.finance.entity.GroupSpace;
import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import com.homie.finance.repository.DebtRepository;
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
    private final DebtRepository debtRepository;
    private final TransactionRepository transactionRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    @Transactional
    public GroupSpaceResponse createGroup(GroupRequest request) {
        User me = getCurrentUser();
        String groupName = normalizeGroupName(request.getName());

        if (groupSpaceRepository.existsByNameAndOwner(groupName, me)) {
            throw new IllegalArgumentException("Homie đã có một nhóm tên '" + groupName + "' rồi!");
        }

        GroupSpace group = new GroupSpace();
        group.setName(groupName);
        group.setOwner(me);
        group.setInviteCode(generateInviteCode());

        Set<User> members = new HashSet<>();
        members.add(me);
        group.setMembers(members);

        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup);
    }

    @Transactional
    public GroupSpaceResponse joinGroup(String inviteCode) {
        User me = getCurrentUser();

        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã mời không được để trống!");
        }

        GroupSpace group = groupSpaceRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Mã mời không hợp lệ!"));

        if (group.getMembers().contains(me)) {
            throw new IllegalArgumentException("Homie đã ở trong nhóm này rồi!");
        }

        group.getMembers().add(me);

        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup);
    }

    @Transactional(readOnly = true)
    public List<GroupSpaceResponse> getMyGroups() {
        User me = getCurrentUser();

        return groupSpaceRepository.findByMembersContainingWithMembers(me)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupSpaceResponse updateGroup(String groupId, GroupRequest request) {
        User me = getCurrentUser();
        String newName = normalizeGroupName(request.getName());

        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (!group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chỉ chủ nhóm mới có quyền đổi tên!");
        }

        boolean isSameName = group.getName() != null && group.getName().equalsIgnoreCase(newName);

        if (!isSameName && groupSpaceRepository.existsByNameAndOwner(newName, me)) {
            throw new IllegalArgumentException("Tên nhóm này homie đã sử dụng rồi!");
        }

        group.setName(newName);

        GroupSpace savedGroup = groupSpaceRepository.save(group);
        return mapToResponse(savedGroup);
    }

    @Transactional
    public void deleteGroup(String groupId) {
        User me = getCurrentUser();

        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm!"));

        if (!group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chỉ chủ nhóm mới được giải tán nhóm!");
        }

        List<Transaction> transactions = transactionRepository.findByGroupSpace(group);
        for (Transaction transaction : transactions) {
            transaction.setGroupSpace(null);
        }
        transactionRepository.saveAll(transactions);

        debtRepository.deleteByGroup(group);

        group.getMembers().clear();
        groupSpaceRepository.save(group);

        groupSpaceRepository.delete(group);
    }

    @Transactional
    public void leaveGroup(String groupId) {
        User me = getCurrentUser();

        GroupSpace group = groupSpaceRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Nhóm không tồn tại!"));

        if (group.getOwner().getId().equals(me.getId())) {
            throw new RuntimeException("Chủ nhóm không được rời, chỉ có thể giải tán nhóm!");
        }

        if (!group.getMembers().contains(me)) {
            throw new IllegalArgumentException("Homie không phải thành viên nhóm này!");
        }

        boolean hasUnsettledDebt =
                debtRepository.existsByGroupAndDebtorAndIsSettledFalse(group, me)
                        || debtRepository.existsByGroupAndCreditorAndIsSettledFalse(group, me);

        if (hasUnsettledDebt) {
            throw new IllegalArgumentException(
                    "Bạn cần thanh toán hoặc được thanh toán hết các khoản nợ trong nhóm trước khi rời đi!"
            );
        }

        group.getMembers().remove(me);
        groupSpaceRepository.save(group);
    }

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

    private GroupSpaceResponse mapToResponse(GroupSpace group) {
        UserSummaryDto ownerDto = UserSummaryDto.builder()
                .id(group.getOwner().getId())
                .username(group.getOwner().getUsername())
                .email(group.getOwner().getEmail())
                .avatarUrl(group.getOwner().getAvatarUrl())
                .build();

        List<UserSummaryDto> memberDtos = group.getMembers()
                .stream()
                .map(member -> UserSummaryDto.builder()
                        .id(member.getId())
                        .username(member.getUsername())
                        .email(member.getEmail())
                        .avatarUrl(member.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());

        return GroupSpaceResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .inviteCode(group.getInviteCode())
                .createdAt(group.getCreatedAt())
                .owner(ownerDto)
                .members(memberDtos)
                .build();
    }

    private String normalizeGroupName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên nhóm không được để trống!");
        }

        return name.trim();
    }

    private String generateInviteCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
    }
}