package com.smartbanking.entity;

public enum AuditEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    TRANSFER_COMPLETED,
    TRANSFER_REJECTED,
    FRAUD_ALERT_RAISED,
    FRAUD_ALERT_REVIEWED
}
