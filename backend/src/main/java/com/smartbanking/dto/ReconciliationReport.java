package com.smartbanking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checks the two ledger invariants: every account's stored balance equals the sum of its
 * ledger entries, and every completed transfer's entries sum to zero.
 */
public record ReconciliationReport(boolean balanced, List<AccountLine> accounts, long unbalancedTransfers) {

    public record AccountLine(String reference, BigDecimal storedBalance, BigDecimal ledgerBalance, boolean matches) {
    }
}
