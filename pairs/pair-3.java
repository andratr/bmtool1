package com.example.dq.sp4.consumer.rules.outstanding.days_past_due;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Outstanding;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Days Past Due is zero but Past Due Amount is greater than 100 Euro and is defaulted to the 20 days.
 *
 * <p>Rationale: Past Due Amount is above the past due threshold.</p>
 *
 * <p>Predicate: Checks if Days Past Due is zero and Defaulted Past Due Amount is greater than 100 Euro</p>
 *
 * <p>Transformation: Raises the EXP_DAYPSTDRDF7_DEFAULTED event and Days Past Due is defaulted.</p>
 */
public class EXP_DAYPSTDRDF7_DEFAULTED implements ConsumerTransformationRule<Outstanding> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public Predicate<Outstanding> predicate(ConsumerDqContext context) {
        return daysPastDueIsZero()
                .and(defaultedPastDueAmountIsGreaterThan100Euro(context));
    }

    private Predicate<Outstanding> daysPastDueIsZero() {
        return outstanding -> outstanding.getDaysPastDue() != null && outstanding.getDaysPastDue().toDays() == 0;
    }

    private Predicate<Outstanding> defaultedPastDueAmountIsGreaterThan100Euro(ConsumerDqContext context) {
        return outstanding -> {
            MonetaryValue pastDueAmountInEuro = context.getEnrichmentService().convertToEuro(outstanding.getPastDueAmount());
            return pastDueAmountInEuro.value().compareTo(HUNDRED) > 0;
        };
    }

    @Override
    public Function<Outstanding, DataQualityEvent> transformation(ConsumerDqContext context) {
        return outstanding -> {
            Duration old = outstanding.getDaysPastDue();
            outstanding.setDaysPastDue(getDefaultedDaysPastDue(context));
            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    outstanding,
                    old,
                    outstanding.getDaysPastDue()
            );
        };
    }

    @Override
    public String eventCode() {
        return "EXP_DAYPSTDRDF7_DEFAULTED";
    }

    private Duration getDefaultedDaysPastDue(ConsumerDqContext context) {
        String nrDays = context.getEnrichmentService().getDefault("days past due more 100EUR").vortexReference();
        return Duration.ofDays(Long.parseLong(nrDays));
    }
}