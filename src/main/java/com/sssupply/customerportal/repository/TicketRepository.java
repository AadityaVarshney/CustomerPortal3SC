package com.sssupply.customerportal.repository;
import com.sssupply.customerportal.entity.Ticket;
import com.sssupply.customerportal.enums.TicketPriority;
import com.sssupply.customerportal.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Optional<Ticket> findByIdAndDeletedAtIsNull(UUID id);
    List<Ticket> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
    List<Ticket> findByWorkspaceIdAndStatusAndDeletedAtIsNull(UUID workspaceId, TicketStatus status);
    List<Ticket> findByCreatedByIdAndDeletedAtIsNull(UUID createdById);
    List<Ticket> findByAssignedToIdAndDeletedAtIsNull(UUID assignedToId);
    @Query(value = "SELECT * FROM tickets WHERE search_vector @@ plainto_tsquery(:query) AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Ticket> searchByText(String query);
    @Query("""
        SELECT t FROM Ticket t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:priority IS NULL OR t.priority = :priority)
          AND (:projectId IS NULL OR t.project.id = :projectId)
          AND t.deletedAt IS NULL
    """)
    Page<Ticket> findAllWithFilters(TicketStatus status, TicketPriority priority, UUID projectId, Pageable pageable);

    long countByStatusAndDeletedAtIsNull(TicketStatus status);

    long countByStatusAndResolvedAtAfterAndDeletedAtIsNull(TicketStatus status, java.time.LocalDateTime after);

    long countByStatusAndResolvedAtBetweenAndDeletedAtIsNull(TicketStatus status, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
