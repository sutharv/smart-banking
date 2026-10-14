package com.smartbanking.fraud;

import com.smartbanking.entity.TransferStatus;
import com.smartbanking.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
class JpaTransferHistory implements TransferHistory {

    private final TransferRepository transferRepository;

    JpaTransferHistory(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Override
    public long countCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId) {
        return transferRepository.countOutgoingSince(accountId, TransferStatus.COMPLETED, since, excludeTransferId);
    }

    @Override
    public BigDecimal sumCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId) {
        BigDecimal sum = transferRepository.sumOutgoingSince(accountId, TransferStatus.COMPLETED, since, excludeTransferId);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
