package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.entity.GroupSpace;
import com.homie.finance.service.GroupSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "6. Group Space", description = "Quản lý Không gian chi tiêu nhóm")
public class GroupSpaceController {

    @Autowired
    private GroupSpaceService groupSpaceService;

    @PostMapping("/create")
    @Operation(summary = "Tạo Không gian nhóm")
    public ApiResponse<GroupSpace> createGroup(@RequestParam String name) {
        return new ApiResponse<>(201, "Đã tạo nhóm thành công!", groupSpaceService.createGroup(name));
    }

    @PostMapping("/join")
    @Operation(summary = "Tham gia nhóm")
    public ApiResponse<GroupSpace> joinGroup(@RequestParam String inviteCode) {
        return new ApiResponse<>(200, "Đã tham gia nhóm!", groupSpaceService.joinGroup(inviteCode));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy danh sách nhóm của tôi")
    public ApiResponse<List<GroupSpace>> getMyGroups() {
        return new ApiResponse<>(200, "Danh sách không gian chung", groupSpaceService.getMyGroups());
    }

    // Lấy chi tiết 1 nhóm (Kèm danh sách thành viên)
    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết nhóm", description = "Lấy thông tin nhóm và danh sách thành viên bên trong để hiển thị")
    public ApiResponse<GroupSpace> getGroupDetails(@PathVariable String id) {
        return new ApiResponse<>(200, "Lấy thông tin nhóm thành công", groupSpaceService.getGroupById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Chỉnh sửa tên nhóm")
    public ApiResponse<GroupSpace> updateGroup(@PathVariable String id, @RequestParam String newName) {
        return new ApiResponse<>(200, "Cập nhật thành công", groupSpaceService.updateGroup(id, newName));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhóm")
    public ApiResponse<String> deleteGroup(@PathVariable String id) {
        groupSpaceService.deleteGroup(id);
        return new ApiResponse<>(200, "Đã giải tán nhóm thành công", null);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Rời khỏi nhóm")
    public ApiResponse<String> leaveGroup(@PathVariable String id) {
        groupSpaceService.leaveGroup(id);
        return new ApiResponse<>(200, "Bạn đã rời khỏi nhóm", null);
    }
}