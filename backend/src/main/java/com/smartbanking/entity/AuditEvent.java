package com.smartbanking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only record of security-relevant actions. Never holds passwords, tokens or amounts. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "entity_reference", length = 100)
    private String entityReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(Long actorUserId, AuditEventType eventType, String entityReference, Instant createdAt) {
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.entityReference = entityReference;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
