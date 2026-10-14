package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;

final class Formatting {

    private Formatting() {
    }

    static String money(BigDecimal amount, Currency currency) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount.setScale(2, RoundingMode.HALF_EVEN)) + " " + currency.name();
    }

    static String duration(Duration window) {
        long seconds = window.toSeconds();
        if (seconds % 3600 == 0) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        if (seconds % 60 == 0) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
