package com.smartbanking.repository;

import com.smartbanking.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID>, JpaSpecificationExecutor<FraudAlert> {

    List<FraudAlert> findByTransferIdOrderByCreatedAtAsc(UUID transferId);

    @Query("select distinct a.transfer.id from FraudAlert a where a.transfer.id in :transferIds")
    Set<UUID> findFlaggedTransferIds(@Param("transferIds") Collection<UUID> transferIds);
}
