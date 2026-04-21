package com.sssupply.customerportal.service.impl;

import com.sssupply.customerportal.dto.*;
import com.sssupply.customerportal.entity.User;
import com.sssupply.customerportal.repository.AnalyticsRepository;
import com.sssupply.customerportal.repository.KbArticleRepository;
import com.sssupply.customerportal.repository.UserRepository;
import com.sssupply.customerportal.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final UserRepository userRepository;
    private final KbArticleRepository kbArticleRepository;

    // ==================== getIssueTrends ====================

    @Override
    public List<IssueTrendPoint> getIssueTrends(LocalDate from, LocalDate to, String groupBy) {
        UUID workspaceId = getCurrentUser().getWorkspace().getId();
        List<Object[]> rows = analyticsRepository.findIssueTrends(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return rows.stream().map(row -> IssueTrendPoint.builder()
                .date(toLocalDate(row[0]))
                .status(row[1] != null ? row[1].toString() : null)
                .category(row[2] != null ? row[2].toString() : null)
                .count(((Number) row[3]).longValue())
                .build()
        ).collect(Collectors.toList());
    }

    // ==================== getResolutionTime ====================

    @Override
    public List<ResolutionTimeResponse> getResolutionTime(LocalDate from, LocalDate to) {
        UUID workspaceId = getCurrentUser().getWorkspace().getId();
        List<Object[]> rows = analyticsRepository.findResolutionTime(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return rows.stream().map(row -> ResolutionTimeResponse.builder()
                .priority(row[0] != null ? row[0].toString() : null)
                .category(row[1] != null ? row[1].toString() : null)
                .avgHours(row[2] != null
                        ? BigDecimal.valueOf(((Number) row[2]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .ticketCount(((Number) row[3]).longValue())
                .build()
        ).collect(Collectors.toList());
    }

    // ==================== getSlaCompliance ====================

    @Override
    public List<SlaComplianceResponse> getSlaCompliance(LocalDate from, LocalDate to) {
        UUID workspaceId = getCurrentUser().getWorkspace().getId();
        List<Object[]> rows = analyticsRepository.findSlaCompliance(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return rows.stream().map(row -> {
            long total     = ((Number) row[1]).longValue();
            long withinSla = ((Number) row[2]).longValue();
            long breached  = ((Number) row[3]).longValue();
            BigDecimal pct = total > 0
                    ? BigDecimal.valueOf(withinSla * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return SlaComplianceResponse.builder()
                    .priority(row[0] != null ? row[0].toString() : null)
                    .totalTickets(total)
                    .withinSla(withinSla)
                    .breached(breached)
                    .compliancePercent(pct)
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== getCsat ====================

    @Override
    public CsatResponse getCsat(LocalDate from, LocalDate to) {
        // CSAT is derived from KB article feedback (helpful vs not-helpful votes)
        // as a proxy until a dedicated CSAT survey module is built.
        UUID workspaceId = getCurrentUser().getWorkspace().getId();
        long helpful    = kbArticleRepository.sumHelpfulCountInRange(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        long notHelpful = kbArticleRepository.sumNotHelpfulCountInRange(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        long total = helpful + notHelpful;
        BigDecimal pct = total > 0
                ? BigDecimal.valueOf(helpful * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return CsatResponse.builder()
                .averageScore(pct)
                .totalResponses(total)
                .positiveCount(helpful)
                .negativeCount(notHelpful)
                .satisfactionPercent(pct)
                .build();
    }

    // ==================== getAgentPerformance ====================

    @Override
    public List<AgentPerformanceResponse> getAgentPerformance(LocalDate from, LocalDate to) {
        UUID workspaceId = getCurrentUser().getWorkspace().getId();
        List<Object[]> rows = analyticsRepository.findAgentPerformance(
                workspaceId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return rows.stream().map(row -> AgentPerformanceResponse.builder()
                .agentId(toUUID(row[0]))
                .agentName(row[1] != null ? row[1].toString() : "Unknown")
                .assignedTickets(((Number) row[2]).longValue())
                .resolvedTickets(((Number) row[3]).longValue())
                .avgResolutionHours(row[4] != null
                        ? BigDecimal.valueOf(((Number) row[4]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                        : null)
                .slaCompliancePercent(row[5] != null
                        ? BigDecimal.valueOf(((Number) row[5]).doubleValue()).setScale(1, RoundingMode.HALF_UP)
                        : null)
                .build()
        ).collect(Collectors.toList());
    }

    // ==================== exportReport ====================

    @Override
    public byte[] exportReport(AnalyticsExportRequest request) {
        if (!"csv".equalsIgnoreCase(request.getFormat())) {
            throw new IllegalArgumentException("Only 'csv' format is supported at this time. PDF export coming soon.");
        }
        try {
            return buildCsv(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV export", e);
        }
    }

    // ==================== Helpers ====================

    /**
     * Safe LocalDate converter for native query Object[] results.
     *
     * Hibernate 5  → returns java.sql.Timestamp for timestamp columns.
     * Hibernate 6+ → returns java.time.LocalDateTime directly (Spring Boot 3+).
     * Both cases are handled here to avoid ClassCastException on either version.
     */
    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt)       return ldt.toLocalDate();
        if (val instanceof LocalDate ld)            return ld;
        if (val instanceof java.sql.Timestamp ts)   return ts.toLocalDateTime().toLocalDate();
        if (val instanceof java.sql.Date sd)        return sd.toLocalDate();
        // Fallback: try parsing the toString() representation (covers some driver edge-cases)
        return LocalDate.parse(val.toString().substring(0, 10));
    }

    /**
     * Safe LocalDateTime converter — same dual-version logic as toLocalDate().
     */
    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt)       return ldt;
        if (val instanceof java.sql.Timestamp ts)   return ts.toLocalDateTime();
        if (val instanceof java.sql.Date sd)        return sd.toLocalDate().atStartOfDay();
        return LocalDateTime.parse(val.toString().replace(" ", "T"));
    }

    /**
     * Safe UUID converter — Postgres JDBC can return UUID directly or as String.
     */
    private UUID toUUID(Object val) {
        if (val == null) return null;
        if (val instanceof UUID uuid) return uuid;
        return UUID.fromString(val.toString());
    }

    private byte[] buildCsv(AnalyticsExportRequest request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            switch (request.getReportType().toLowerCase()) {
                case "issue-trends" -> {
                    printer.printRecord("date", "status", "category", "count");
                    getIssueTrends(request.getFrom(), request.getTo(), "day")
                            .forEach(p -> {
                                try { printer.printRecord(p.getDate(), p.getStatus(), p.getCategory(), p.getCount()); }
                                catch (IOException e) { throw new RuntimeException(e); }
                            });
                }
                case "resolution-time" -> {
                    printer.printRecord("priority", "category", "avg_hours", "ticket_count");
                    getResolutionTime(request.getFrom(), request.getTo())
                            .forEach(r -> {
                                try { printer.printRecord(r.getPriority(), r.getCategory(), r.getAvgHours(), r.getTicketCount()); }
                                catch (IOException e) { throw new RuntimeException(e); }
                            });
                }
                case "sla" -> {
                    printer.printRecord("priority", "total", "within_sla", "breached", "compliance_pct");
                    getSlaCompliance(request.getFrom(), request.getTo())
                            .forEach(r -> {
                                try { printer.printRecord(r.getPriority(), r.getTotalTickets(), r.getWithinSla(), r.getBreached(), r.getCompliancePercent()); }
                                catch (IOException e) { throw new RuntimeException(e); }
                            });
                }
                case "agent-performance" -> {
                    printer.printRecord("agent_id", "agent_name", "assigned", "resolved", "avg_hours", "sla_pct");
                    getAgentPerformance(request.getFrom(), request.getTo())
                            .forEach(r -> {
                                try { printer.printRecord(r.getAgentId(), r.getAgentName(), r.getAssignedTickets(), r.getResolvedTickets(), r.getAvgResolutionHours(), r.getSlaCompliancePercent()); }
                                catch (IOException e) { throw new RuntimeException(e); }
                            });
                }
                default -> throw new IllegalArgumentException("Unknown reportType: " + request.getReportType());
            }
        }
        return out.toByteArray();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}