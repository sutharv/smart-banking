package com.smartbanking.dto;

import com.smartbanking.entity.ReviewStatus;
import com.smartbanking.entity.Severity;

import java.util.UUID;

public record FraudAlertSummary(UUID id, String ruleCode, Severity severity, String explanation,
                                ReviewStatus reviewStatus) {
}
