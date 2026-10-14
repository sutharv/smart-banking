package com.smartbanking.exception;

import com.smartbanking.dto.TransferDto;

/**
 * Raised by the controller (after the service transaction has committed) when a well-formed
 * transfer was declined by a business rule. The rejected attempt is already persisted.
 */
public class TransferRejectedException extends RuntimeException {

    private final transient TransferDto transfer;

    public TransferRejectedException(TransferDto transfer) {
        super("Transfer rejected: " + transfer.rejectionReason());
        this.transfer = transfer;
    }

    public TransferDto getTransfer() {
        return transfer;
    }
}
