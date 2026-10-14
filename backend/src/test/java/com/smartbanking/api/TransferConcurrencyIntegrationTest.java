package com.smartbanking.api;

import com.smartbanking.dto.ReconciliationReport;
import com.smartbanking.dto.TransferRequest;
import com.smartbanking.entity.Currency;
import com.smartbanking.entity.Role;
import com.smartbanking.entity.TransferStatus;
import com.smartbanking.security.CurrentUser;
import com.smartbanking.service.ReconciliationService;
import com.smartbanking.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Hammers the transfer service from many threads against a real PostgreSQL. */
class TransferConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final CurrentUser ALICE = new CurrentUser(1L, "alice", Role.CUSTOMER);
    private static final CurrentUser BOB = new CurrentUser(2L, "bob", Role.CUSTOMER);

    @Autowired
    private TransferService transferService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void concurrentTransfersCannotSpendTheSameBalanceTwice() throws Exception {
        // 10 simultaneous transfers of 1,000.00 from an account holding 5,000.00.
        List<Callable<TransferStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transferService.createTransfer(ALICE,
                    request("SB-EUR-0001", "SB-EUR-0004", "1000.00"), null).transfer().status());
        }

        List<TransferStatus> outcomes = runConcurrently(tasks);

        assertThat(outcomes).filteredOn(s -> s == TransferStatus.COMPLETED).hasSize(5);
        assertThat(outcomes).filteredOn(s -> s == TransferStatus.REJECTED).hasSize(5);
        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("0.00");
        assertThat(balance("SB-EUR-0004")).isEqualByComparingTo("6500.00");
        assertBalanced();
    }

    @Test
    void opposingTransfersRunConcurrentlyWithoutDeadlockOrLostUpdates() throws Exception {
        List<Callable<TransferStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            tasks.add(() -> transferService.createTransfer(ALICE,
                    request("SB-EUR-0001", "SB-EUR-0004", "7.00"), null).transfer().status());
            tasks.add(() -> transferService.createTransfer(BOB,
                    request("SB-EUR-0004", "SB-EUR-0001", "3.00"), null).transfer().status());
        }

        List<TransferStatus> outcomes = runConcurrently(tasks);

        assertThat(outcomes).containsOnly(TransferStatus.COMPLETED);
        // Alice: 5000 - 15*7 + 15*3 = 4940; Bob: 1500 + 15*7 - 15*3 = 1560.
        assertThat(balance("SB-EUR-0001")).isEqualByComparingTo("4940.00");
        assertThat(balance("SB-EUR-0004")).isEqualByComparingTo("1560.00");
        assertBalanced();
    }

    private void assertBalanced() {
        ReconciliationReport report = reconciliationService.reconcile();
        assertThat(report.balanced()).as("ledger reconciliation: %s", report).isTrue();
    }

    private static TransferRequest request(String source, String destination, String amount) {
        return new TransferRequest(source, destination, new BigDecimal(amount), Currency.EUR, "concurrency test");
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
