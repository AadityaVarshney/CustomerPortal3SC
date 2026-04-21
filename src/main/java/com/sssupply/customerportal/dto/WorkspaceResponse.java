package com.sssupply.customerportal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponse {
    private UUID id;
    private String name;
    private String logoUrl;
    private String slug;
    private String primaryColor;
    private String accentColor;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
