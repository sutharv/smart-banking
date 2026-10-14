package com.smartbanking.fraud;

import com.smartbanking.entity.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Tunable, illustrative rule settings (see application.yml). These are demo values for a
 * simulator, not real-world fraud thresholds.
 */
@ConfigurationProperties("app.fraud")
public record FraudProperties(HighValue highValue, RepeatedTransfers repeatedTransfers, Velocity velocity) {

    public FraudProperties {
        highValue = highValue != null ? highValue : new HighValue(false, Map.of());
        repeatedTransfers = repeatedTransfers != null ? repeatedTransfers : new RepeatedTransfers(false, 5, Duration.ofMinutes(2));
        velocity = velocity != null ? velocity : new Velocity(false, Duration.ofHours(1), Map.of());
    }

    /** Flag a single transfer whose amount is strictly above the threshold for its currency. */
    public record HighValue(boolean enabled, Map<Currency, BigDecimal> thresholds) {
        public HighValue {
            thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
        }
    }

    /** Flag when an account makes at least {@code maxTransfers} completed transfers within {@code window}. */
    public record RepeatedTransfers(boolean enabled, int maxTransfers, Duration window) {
    }

    /** Flag when the total sent by an account within {@code window} is strictly above the limit for its currency. */
    public record Velocity(boolean enabled, Duration window, Map<Currency, BigDecimal> limits) {
        public Velocity {
            limits = limits == null ? Map.of() : Map.copyOf(limits);
        }
    }
}
