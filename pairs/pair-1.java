package com.example.dq.sp4.consumer.rules.outstanding.days_past_due;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Outstanding;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.example.dq.sp4.consumer.rules.limit.LimitUtil.relatedRiskRatingHasProblems;

/**
 * Days Past Due is greater than 90 days but Risk Rating is performing.
 *
 * <p>Rationale: The ‘Methodology Standards for credit risk models’ (see Ref.[10]) states that </p>
 * <p>“for current account facilities a Basel II-default is triggered once the limit on a particular current account facility has </p>
 * <p>been breached for a period of 90 days and the overdraft on the facility limit amounts to EUR 250 (two-hundred-and-fifty) or more.”</p>
 *
 * <p>Predicate: Checks if:</p>
 * <ul>
 *     <li>Days Past Due is greater than 90 days</li>
 *     <li>Outstanding Nominal Amount is equal or greater than 250 Euro</li>
 *     <li>Limit Credit Risk Rating is not a Problem rating</li>
 * </ul>
 * <p>Transformation: Raises the EXP_DAYPSTDCHK1_IGNORED event.</p>
 */
public class EXP_DAYPSTDCHK1_IGNORED implements ConsumerTransformationRule<Outstanding> {
    @Override
    public Predicate<Outstanding> predicate(ConsumerDqContext context) {
        return daysPastDueGreaterThan90()
                .and(riskRatingDoesNotHaveProblems(context))
                .and(nominalAmountGreaterThan250Euro(context));
    }

    @Override
    public Function<Outstanding, DataQualityEvent> transformation(ConsumerDqContext context) {
        return exceptionRaisedEventTransformation(context);
    }

    @Override
    public String eventCode() {
        return "EXP_DAYPSTDCHK1_IGNORED";
    }

    private Predicate<Outstanding> daysPastDueGreaterThan90() {
        return outstanding -> outstanding.getDaysPastDue().toDays() > 90;
    }

    private Predicate<Outstanding> nominalAmountGreaterThan250Euro(ConsumerDqContext context) {
        return outstanding -> {
            Function<MonetaryValue, MonetaryValue> convertToEuro = context.getEnrichmentService()::convertToEuro;
            BigDecimal outstandingNominalAmount = convertToEuro.apply(outstanding.getOutstandingAmount()).value();
            return outstandingNominalAmount.compareTo(new BigDecimal(250)) >= 0;
        };
    }

    private Predicate<Outstanding> riskRatingDoesNotHaveProblems(ConsumerDqContext context) {
        return outstanding -> !relatedRiskRatingHasProblems(outstanding.getLimit(), context);
    }
}