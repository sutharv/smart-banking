package com.smartbanking.dto;

import com.smartbanking.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Analyst decision on an alert: CONFIRMED or DISMISSED. */
public record AlertReviewRequest(@NotNull ReviewStatus decision, @Size(max = 500) String note) {
}
