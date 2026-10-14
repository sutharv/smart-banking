package com.smartbanking.fraud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read access to an account's recent outgoing activity. Both methods consider only COMPLETED
 * transfers created at or after {@code since} and exclude the transfer currently being evaluated.
 */
public interface TransferHistory {

    long countCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId);

    BigDecimal sumCompletedOutgoingSince(Long accountId, Instant since, UUID excludeTransferId);
}
