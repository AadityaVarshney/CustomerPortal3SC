package com.sssupply.customerportal.service.impl;

import com.sssupply.customerportal.dto.*;
import com.sssupply.customerportal.entity.User;
import com.sssupply.customerportal.entity.Workspace;
import com.sssupply.customerportal.enums.UserRole;
import com.sssupply.customerportal.repository.UserRepository;
import com.sssupply.customerportal.repository.WorkspaceRepository;
import com.sssupply.customerportal.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listAllWorkspaces() {
        return workspaceRepository.findByDeletedAtIsNull()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public WorkspaceResponse createWorkspace(WorkspaceRequest request) {
        String baseSlug = request.getName()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")   // non-alphanumeric → hyphen
                .replaceAll("^-|-$", "");        // trim leading/trailing hyphens

        // Ensure uniqueness — append random suffix if slug already taken
        String slug = baseSlug;
        if (workspaceRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .slug(slug)                       // ← was missing
                .build();

        workspace = workspaceRepository.save(workspace);
        return mapToResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceById(UUID id) {
        return mapToResponse(findWorkspace(id));
    }

    @Override
    public WorkspaceResponse updateBranding(UUID id, BrandingRequest request) {
        Workspace workspace = findWorkspace(id);

        if (request.getLogoUrl() != null) workspace.setLogoUrl(request.getLogoUrl());
        if (request.getPrimaryColor() != null) workspace.setPrimaryColor(request.getPrimaryColor());
        if (request.getAccentColor() != null) workspace.setAccentColor(request.getAccentColor());

        workspace = workspaceRepository.save(workspace);
        return mapToResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID workspaceId) {
        findWorkspace(workspaceId); // verify existence + access
        return userRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId)
                .stream()
                .map(this::mapMemberToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public WorkspaceMemberResponse updateMemberRole(UUID memberId, UpdateMemberRoleRequest request) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found with id: " + memberId));

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole()
                    + ". Valid values: CUSTOMER_ADMIN, CUSTOMER_USER, INTERNAL_AGENT, INTERNAL_LEAD, INTERNAL_ADMIN");
        }

        member.setRole(newRole);
        member = userRepository.save(member);
        return mapMemberToResponse(member);
    }

    @Override
    public void removeMember(UUID workspaceId, UUID userId) {
        findWorkspace(workspaceId); // verify existence + access
        User member = userRepository.findById(userId)
                .filter(u -> u.getWorkspace() != null && u.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> new RuntimeException("Member not found in workspace"));

        // Soft-delete the user from the workspace
        member.setDeletedAt(LocalDateTime.now());
        userRepository.save(member);
    }

    // ==================== Helpers ====================

    private Workspace findWorkspace(UUID id) {
        return workspaceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Workspace not found with id: " + id));
    }

    private WorkspaceResponse mapToResponse(Workspace workspace) {
        int memberCount = userRepository.countByWorkspaceIdAndDeletedAtIsNull(workspace.getId());
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .logoUrl(workspace.getLogoUrl())
                .primaryColor(workspace.getPrimaryColor())
                .accentColor(workspace.getAccentColor())
                .memberCount(memberCount)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

    private WorkspaceMemberResponse mapMemberToResponse(User user) {
        return WorkspaceMemberResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .joinedAt(user.getCreatedAt())
                .build();
    }
}
