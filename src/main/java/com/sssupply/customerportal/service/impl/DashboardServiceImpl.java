package com.sssupply.customerportal.service.impl;

import com.sssupply.customerportal.dto.DashboardStatsResponse;
import com.sssupply.customerportal.enums.TicketStatus;
import com.sssupply.customerportal.repository.TicketRepository;
import com.sssupply.customerportal.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final TicketRepository ticketRepository;

    @Override
    public DashboardStatsResponse getStats() {
        long open = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.IN_PROGRESS);

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sixtyDaysAgo = LocalDateTime.now().minusDays(60);

        long resolvedThisPeriod = ticketRepository
                .countByStatusAndResolvedAtAfterAndDeletedAtIsNull(TicketStatus.RESOLVED, thirtyDaysAgo);
        long resolvedLastPeriod = ticketRepository
                .countByStatusAndResolvedAtBetweenAndDeletedAtIsNull(TicketStatus.RESOLVED, sixtyDaysAgo, thirtyDaysAgo);

        Integer resolvedTrend = null;
        if (resolvedLastPeriod > 0) {
            resolvedTrend = (int) Math.round(
                    ((double) (resolvedThisPeriod - resolvedLastPeriod) / resolvedLastPeriod) * 100);
        }

        return DashboardStatsResponse.builder()
                .openCount(open)
                .inProgressCount(inProgress)
                .resolvedCount(resolvedThisPeriod)
                .resolvedTrend(resolvedTrend)
                .avgResponseTime(null)
                .build();
    }
}
