package com.smartbanking.fraud;

import com.smartbanking.entity.Severity;

/** A triggered rule with a human-readable explanation of exactly why it fired. */
public record RuleHit(String ruleCode, Severity severity, String explanation) {
}
