package com.smartbanking.fraud;

import java.util.Optional;

/** An explainable, deterministic monitoring rule. Rules never block a transfer; they only raise alerts. */
public interface FraudRule {

    String code();

    Optional<RuleHit> evaluate(TransferSnapshot transfer, TransferHistory history);
}
