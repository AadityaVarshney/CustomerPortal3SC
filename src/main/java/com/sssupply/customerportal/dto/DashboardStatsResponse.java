package com.sssupply.customerportal.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse {
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private Integer resolvedTrend;   // % change vs previous 30 days, nullable
    private Double avgResponseTime;  // hours, nullable
}
