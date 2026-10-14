package com.smartbanking.fraud;

import com.smartbanking.entity.FraudAlert;
import com.smartbanking.entity.Transfer;
import com.smartbanking.entity.TransferStatus;
import com.smartbanking.repository.FraudAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs every enabled rule against a completed transfer and stores one alert per triggered rule.
 * Monitoring is "flag, don't block": the transfer stays completed and is queued for review.
 */
@Service
public class FraudMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudMonitoringService.class);

    private final List<FraudRule> rules;
    private final TransferHistory history;
    private final FraudAlertRepository alertRepository;
    private final Clock clock;

    public FraudMonitoringService(List<FraudRule> rules, TransferHistory history,
                                  FraudAlertRepository alertRepository, Clock clock) {
        this.rules = List.copyOf(rules);
        this.history = history;
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    /** Must run inside the transfer's transaction so alerts commit or roll back with it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<FraudAlert> evaluate(Transfer transfer) {
        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            return List.of();
        }
        TransferSnapshot snapshot = TransferSnapshot.of(transfer);
        List<FraudAlert> alerts = new ArrayList<>();
        for (FraudRule rule : rules) {
            Optional<RuleHit> hit = rule.evaluate(snapshot, history);
            hit.ifPresent(h -> alerts.add(new FraudAlert(transfer, h.ruleCode(), h.explanation(), h.severity(), clock.instant())));
        }
        if (!alerts.isEmpty()) {
            log.info("Transfer {} triggered fraud rules {}", transfer.getId(),
                    alerts.stream().map(FraudAlert::getRuleCode).toList());
        }
        return alertRepository.saveAll(alerts);
    }
}
