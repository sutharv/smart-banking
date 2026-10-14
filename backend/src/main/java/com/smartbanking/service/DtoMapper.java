package com.smartbanking.service;

import com.smartbanking.dto.AccountDto;
import com.smartbanking.dto.FraudAlertDto;
import com.smartbanking.dto.FraudAlertSummary;
import com.smartbanking.dto.TransactionDto;
import com.smartbanking.dto.TransferDto;
import com.smartbanking.entity.Account;
import com.smartbanking.entity.FraudAlert;
import com.smartbanking.entity.Transfer;

import java.util.List;

/** Converts entities to API DTOs. Internal database ids never leave the backend. */
final class DtoMapper {

    private DtoMapper() {
    }

    static AccountDto toDto(Account account) {
        return new AccountDto(account.getReference(), account.getDisplayName(), account.getCurrency(),
                account.getBalance(), account.getStatus());
    }

    static TransferDto toDto(Transfer transfer, List<FraudAlert> alerts) {
        return new TransferDto(transfer.getId(), transfer.getSourceAccount().getReference(),
                transfer.getDestinationAccount().getReference(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getStatus(), transfer.getRejectionReason(), transfer.getReference(),
                transfer.getCreatedAt(), transfer.getCompletedAt(),
                alerts.stream().map(DtoMapper::toSummary).toList());
    }

    static FraudAlertSummary toSummary(FraudAlert alert) {
        return new FraudAlertSummary(alert.getId(), alert.getRuleCode(), alert.getSeverity(),
                alert.getExplanation(), alert.getReviewStatus());
    }

    static FraudAlertDto toDto(FraudAlert alert) {
        Transfer transfer = alert.getTransfer();
        return new FraudAlertDto(alert.getId(), transfer.getId(), transfer.getSourceAccount().getReference(),
                transfer.getDestinationAccount().getReference(), transfer.getAmount(), transfer.getCurrency(),
                alert.getRuleCode(), alert.getSeverity(), alert.getExplanation(), alert.getReviewStatus(),
                alert.getReviewNote(), alert.getReviewedAt(), alert.getCreatedAt());
    }

    static TransactionDto toTransaction(Transfer transfer, Long viewpointAccountId, boolean flagged) {
        boolean outgoing = transfer.getSourceAccount().getId().equals(viewpointAccountId);
        Account counterparty = outgoing ? transfer.getDestinationAccount() : transfer.getSourceAccount();
        return new TransactionDto(transfer.getId(),
                outgoing ? TransactionDto.Direction.OUTGOING : TransactionDto.Direction.INCOMING,
                counterparty.getReference(), transfer.getAmount(), transfer.getCurrency(), transfer.getStatus(),
                transfer.getRejectionReason(), transfer.getReference(), transfer.getCreatedAt(), flagged);
    }
}
