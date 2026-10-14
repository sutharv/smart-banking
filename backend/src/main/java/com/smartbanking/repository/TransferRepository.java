package com.smartbanking.repository;

import com.smartbanking.entity.Transfer;
import com.smartbanking.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatedByUserIdAndIdempotencyKey(Long initiatedByUserId, String idempotencyKey);

    /**
     * History of one account: everything it sent (including rejected attempts) and
     * completed transfers it received. Rejected incoming attempts are not shown to the recipient.
     */
    @Query(value = """
            select t from Transfer t
              join fetch t.sourceAccount
              join fetch t.destinationAccount
            where t.sourceAccount.id = :accountId
               or (t.destinationAccount.id = :accountId and t.status = :completed)
            order by t.createdAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from Transfer t
            where t.sourceAccount.id = :accountId
               or (t.destinationAccount.id = :accountId and t.status = :completed)
            """)
    Page<Transfer> findHistory(@Param("accountId") Long accountId,
                               @Param("completed") TransferStatus completed,
                               Pageable pageable);

    @Query("""
            select count(t) from Transfer t
            where t.sourceAccount.id = :accountId
              and t.status = :status
              and t.createdAt >= :since
              and t.id <> :excludeId
            """)
    long countOutgoingSince(@Param("accountId") Long accountId,
                            @Param("status") TransferStatus status,
                            @Param("since") Instant since,
                            @Param("excludeId") UUID excludeId);

    /** Returns null when there are no matching transfers. */
    @Query("""
            select sum(t.amount) from Transfer t
            where t.sourceAccount.id = :accountId
              and t.status = :status
              and t.createdAt >= :since
              and t.id <> :excludeId
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId,
                                @Param("status") TransferStatus status,
                                @Param("since") Instant since,
                                @Param("excludeId") UUID excludeId);
}
