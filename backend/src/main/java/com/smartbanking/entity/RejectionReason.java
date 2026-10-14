package com.smartbanking.entity;

/** Business-rule reasons a well-formed transfer request was declined and recorded. */
public enum RejectionReason {
    INSUFFICIENT_FUNDS,
    SOURCE_ACCOUNT_INACTIVE,
    DESTINATION_ACCOUNT_INACTIVE,
    CURRENCY_MISMATCH
}
