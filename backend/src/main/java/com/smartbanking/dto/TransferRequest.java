package com.smartbanking.dto;

import com.smartbanking.entity.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Size(max = 34) String sourceAccountReference,
        @NotBlank @Size(max = 34) String destinationAccountReference,
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @DecimalMax(value = "1000000.00", message = "must not exceed 1,000,000.00")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,
        @NotNull Currency currency,
        @Size(max = 140) String reference) {
}
