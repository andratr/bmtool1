package com.example.dq.sp4.consumer.rules.outstanding.outstanding_end_date;

import com.example.dq.foundation.data.OptionalDate;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import com.example.dq.sp4.consumer.rules.limit.LimitUtil;
import itrf.vortex.spdi.entities.consumer.Outstanding;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.example.dq.sp4.consumer.rules.outstanding.OutstandingUtil.computeDefaultOutstandingEndDate;
import static itrf.vortex.spdi.util.OptionalDateUtil.hasExpired;

/**
 * Outstanding End Date plus Days Past Due has expired and is defaulted to (Outstanding End Date + offset period).
 *
 * <p>Rationale: Exposures with a Past Due Amount can have an expired Outstanding End Date.</p>
 *
 * <p>Predicate: Checks if</p>
 * <ul>
 * <li>Outstanding end date plus days past due has expired (i.e. reporting date is after this date)</li>
 * <li>Limit end date is on or after the date that would be calculated in the transformation</li>
 * <li>Limit is not derecognised</li>
 * </ul>
 *
 * <p>
 * Transformation: Default using the offset period (1 month for limits with limit type overdraft, 1 year otherwise). The
 * defaulting is done as follows:
 * </p>
 * <ul>
 * <li>Compute the number of days between the reporting date and the outstanding end date</li>
 * <li>Divide this number by the offset period in days (round up)</li>
 * <li>Multiply this number by the offset period in days</li>
 * </ul>
 */
public class EXP_ENDDATERDF3H_DEFAULTED implements ConsumerTransformationRule<Outstanding> {
    @Override
    public Predicate<Outstanding> predicate(ConsumerDqContext context) {
        return outstandingEndDatePlusDaysPastDueHasExpired(context)
                .and(limitEndDateIsOnOrAfterToBeDefaultedOutstandingEndDate(context))
                .and(limitIsNotDerecognised(context));
    }

    private Predicate<Outstanding> outstandingEndDatePlusDaysPastDueHasExpired(ConsumerDqContext context) {
        return outstanding -> outstanding.getDaysPastDue() != null && hasExpired(context).test(
                outstanding.getOutstandingEndDate()
                        .plusDays(outstanding.getDaysPastDue().toDays())
        );
    }

    private Predicate<Outstanding> limitEndDateIsOnOrAfterToBeDefaultedOutstandingEndDate(ConsumerDqContext context) {
        return outstanding -> outstanding
                .getLimit()
                .getLimitEndDate()
                .isAfterOrEquals(computeDefaultOutstandingEndDate(outstanding, context));
    }

    private Predicate<Outstanding> limitIsNotDerecognised(ConsumerDqContext context) {
        return outstanding -> LimitUtil.isNotDerecognised(context).test(outstanding.getLimit());
    }

    @Override
    public Function<Outstanding, DataQualityEvent> transformation(ConsumerDqContext context) {
        return outstanding -> {
            OptionalDate originalValue = outstanding.getOutstandingEndDate();
            outstanding.setOutstandingEndDate(new OptionalDate(computeDefaultOutstandingEndDate(outstanding, context)));

            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    outstanding,
                    originalValue,
                    outstanding.getOutstandingEndDate()
            );
        };
    }

    @Override
    public String eventCode() {
        return "EXP_ENDDATERDF3H_DEFAULTED";
    }
}