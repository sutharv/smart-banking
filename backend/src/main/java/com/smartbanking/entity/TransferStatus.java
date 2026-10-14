package com.smartbanking.entity;

/**
 * Transfer lifecycle. Allowed transitions: PENDING -> COMPLETED and PENDING -> REJECTED.
 * COMPLETED and REJECTED are terminal.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    REJECTED
}
