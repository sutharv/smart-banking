package com.smartbanking.service;

import com.smartbanking.dto.ReconciliationReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Verifies that cached balances agree with the immutable ledger. */
@Service
public class ReconciliationService {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // REPEATABLE READ gives both queries the same snapshot of the database.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport reconcile() {
        List<ReconciliationReport.AccountLine> accounts = jdbcTemplate.query("""
                        SELECT a.account_reference, a.balance, COALESCE(SUM(l.amount), 0) AS ledger_balance
                        FROM account a
                        LEFT JOIN ledger_entry l ON l.account_id = a.id
                        GROUP BY a.id, a.account_reference, a.balance
                        ORDER BY a.account_reference
                        """,
                (rs, rowNum) -> {
                    var stored = rs.getBigDecimal("balance");
                    var ledger = rs.getBigDecimal("ledger_balance");
                    return new ReconciliationReport.AccountLine(rs.getString("account_reference"), stored, ledger,
                            stored.compareTo(ledger) == 0);
                });
        Long unbalanced = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT t.id
                    FROM transfer t
                    LEFT JOIN ledger_entry l ON l.transfer_id = t.id
                    WHERE t.status = 'COMPLETED'
                    GROUP BY t.id
                    HAVING COALESCE(SUM(l.amount), 0) <> 0 OR COUNT(l.id) <> 2
                ) bad
                """, Long.class);
        long unbalancedTransfers = unbalanced == null ? 0 : unbalanced;
        boolean balanced = unbalancedTransfers == 0 && accounts.stream().allMatch(ReconciliationReport.AccountLine::matches);
        return new ReconciliationReport(balanced, accounts, unbalancedTransfers);
    }
}
