package com.smartbanking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fraud_alert")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private Transfer transfer;

    @Column(name = "rule_code", nullable = false, length = 40, updatable = false)
    private String ruleCode;

    @Column(nullable = false, length = 500, updatable = false)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.OPEN;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FraudAlert() {
        // for JPA
    }

    public FraudAlert(Transfer transfer, String ruleCode, String explanation, Severity severity, Instant createdAt) {
        this.transfer = Objects.requireNonNull(transfer);
        this.ruleCode = Objects.requireNonNull(ruleCode);
        this.explanation = Objects.requireNonNull(explanation);
        this.severity = Objects.requireNonNull(severity);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** Records an analyst decision. Only OPEN alerts can be reviewed. */
    public void review(ReviewStatus decision, String note, Long reviewerId, Instant when) {
        if (decision == ReviewStatus.OPEN) {
            throw new IllegalArgumentException("A review must confirm or dismiss the alert");
        }
        if (reviewStatus != ReviewStatus.OPEN) {
            throw new IllegalStateException("Alert " + id + " has already been reviewed");
        }
        this.reviewStatus = decision;
        this.reviewNote = note;
        this.reviewedByUserId = Objects.requireNonNull(reviewerId);
        this.reviewedAt = Objects.requireNonNull(when);
    }

    public UUID getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getExplanation() {
        return explanation;
    }

    public Severity getSeverity() {
        return severity;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
