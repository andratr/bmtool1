package com.example.dq.sp4.consumer.rules.outstanding.days_past_due;

import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Outstanding;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.example.dq.foundation.data.NumberUtil.isPositive;
import static itrf.vortex.spdi.util.DurationUtil.daysBetween;

/**
 * If there are overdue payments, but the days past due is not delivered, this rule tries to calculate the days past
 * due based on the number of overdue payments.
 *
 * <p>Rationale: Days Past Due can be calculated based on the number of overdue payments.</p>
 *
 * <p>Predicate: This exception is raised if:</p>
 * <ul>
 *     <li>Days Past Due is not delivered</li>
 *     <li>and Payments Overdue is greater than zero</li>
 *     <li>and Next Payment Date is not empty</li>
 *     <li>and Next Payment Date is before or equal to (Reporting Date + minimum of Interest Payment Frequency and
 *         Repayment Frequency)</li>
 * </ul>
 *
 * <p>Transformation: Defaults Days Past Due to
 * {@code (Payments Overdue x min(Interest Payment Frequency days, Repayment Frequency days)) – (Next Payment Date – Reporting Date)}
 * and raises the EXP_DAYPSTDRDF3C_DEFAULTED event.</p>
 */
public class EXP_DAYPSTDRDF3C_DEFAULTED implements ConsumerTransformationRule<Outstanding> {
    @Override
    public Predicate<Outstanding> predicate(ConsumerDqContext context) {
        return daysPastDueIsNotDelivered()
                .and(paymentsOverdueIsGreaterThanZero())
                .and(nextPaymentDateIsNotEmpty())
                .and(nextPaymentDateIsBeforeOrEqualToReportingDatePlusMinimumOfInterestPaymentFrequencyAndRepaymentFrequency(context));
    }

    private Predicate<Outstanding> daysPastDueIsNotDelivered() {
        return outstanding -> outstanding.getDaysPastDue() == null;
    }

    private Predicate<Outstanding> paymentsOverdueIsGreaterThanZero() {
        return outstanding -> outstanding.getPaymentsOverdue() != null && isPositive(outstanding.getPaymentsOverdue());
    }

    private Predicate<Outstanding> nextPaymentDateIsBeforeOrEqualToReportingDatePlusMinimumOfInterestPaymentFrequencyAndRepaymentFrequency(
            ConsumerDqContext context
    ) {
        return outstanding -> {
            long numberOfDays = Objects.requireNonNull(outstanding.getNumberOfDaysUntilNextPayment());
            LocalDate nextPaymentDateThreshold = context.getReportingDate().plusDays(numberOfDays);

            return outstanding.getNextPaymentDate().isBeforeOrEquals(nextPaymentDateThreshold);
        };
    }

    private Predicate<Outstanding> nextPaymentDateIsNotEmpty() {
        return outstanding -> !outstanding.getNextPaymentDate().isEmpty();
    }

    @Override
    public Function<Outstanding, DataQualityEvent> transformation(ConsumerDqContext context) {
        return outstanding -> {
            Duration originalValue = outstanding.getDaysPastDue();

            long numberOfDays = Objects.requireNonNull(outstanding.getNumberOfDaysUntilNextPayment());
            long paymentsOverdue = outstanding.getPaymentsOverdue().longValue();
            LocalDate reportingDate = context.getReportingDate();
            LocalDate nextPaymentDate = outstanding.getNextPaymentDate().localDate();

            outstanding.setDaysPastDue(daysBetween(reportingDate, nextPaymentDate)
                    .plusDays(paymentsOverdue * numberOfDays)
            );

            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    outstanding,
                    originalValue,
                    outstanding.getDaysPastDue()
            );
        };
    }

    @Override
    public String eventCode() {
        return "EXP_DAYPSTDRDF3C_DEFAULTED";
    }
}