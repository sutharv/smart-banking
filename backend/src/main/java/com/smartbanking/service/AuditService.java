package com.smartbanking.service;

import com.smartbanking.entity.AuditEvent;
import com.smartbanking.entity.AuditEventType;
import com.smartbanking.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Records security-relevant actions. Joins the caller's transaction when there is one, so a
 * transfer's audit record commits or rolls back together with the transfer.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(Long actorUserId, AuditEventType type, String entityReference) {
        repository.save(new AuditEvent(actorUserId, type, entityReference, clock.instant()));
    }
}
