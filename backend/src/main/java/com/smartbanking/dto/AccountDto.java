package com.smartbanking.dto;

import com.smartbanking.entity.AccountStatus;
import com.smartbanking.entity.Currency;

import java.math.BigDecimal;

public record AccountDto(String reference, String displayName, Currency currency, BigDecimal balance,
                         AccountStatus status) {
}
