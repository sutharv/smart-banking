package com.smartbanking.service;

import com.smartbanking.dto.TransferDto;

/** Outcome of a transfer request. {@code replayed} is true when an idempotency key matched an earlier request. */
public record TransferResult(TransferDto transfer, boolean replayed) {
}
