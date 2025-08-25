package com.example.dq.sp4.consumer.rules.outstanding.past_due_amount;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Outstanding;
import itrf.vortex.spdi.util.MonetaryValueUtil;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Past Due Amount is larger than (Principal + Interest + Fees Past Due Amount).
 * This rule raises the EXP_PSTDUAMTCHK2_IGNORED event.
 *
 * <p>Rationale: Past Due Amount must not be higher than the sum of its underlying components. </p>
 * <p>If the split in principal, interest and fees past due amount is not available, the exception will not be raised.</p>
 *
 * <p>Predicate: Checks if any of the related fields are delivered and if the total sum is greater than {@code PastDueAmount}.</p>
 *
 * <p>Transformation: Raises the EXP_PSTDUAMTCHK2_IGNORED event.</p>
 */
public class EXP_PSTDUAMTCHK2_IGNORED implements ConsumerTransformationRule<Outstanding> {

    @Override
    public Predicate<Outstanding> predicate(ConsumerDqContext context) {
        return anyRelatedFieldsAreDelivered()
                .and(pastDueAmountIsGreaterThanTotalSumOfRelatedFieldsAndAreNotZero(context));
    }

    private Predicate<Outstanding> anyRelatedFieldsAreDelivered() {
        return outstanding -> outstanding.getPrincipalPastDueAmount() != null
                || outstanding.getInterestPastDueAmount() != null
                || outstanding.getFeesPastDueAmount() != null;
    }

    private Predicate<Outstanding> pastDueAmountIsGreaterThanTotalSumOfRelatedFieldsAndAreNotZero(
            ConsumerDqContext context
    ) {
        Function<MonetaryValue, BigDecimal> convertToEuro = MonetaryValueUtil
                .convertToEuro(context, MonetaryValue.EUR_ZERO)
                .andThen(MonetaryValue::value);

        return outstanding -> {
            BigDecimal sumRelatedFields = convertToEuro.apply(outstanding.getPrincipalPastDueAmount())
                    .add(convertToEuro.apply(outstanding.getInterestPastDueAmount()))
                    .add((convertToEuro.apply(outstanding.getFeesPastDueAmount())));

            BigDecimal pastDueAmount = convertToEuro.apply(outstanding.getPastDueAmount());

            return pastDueAmount.compareTo(sumRelatedFields) > 0 && sumRelatedFields.compareTo(BigDecimal.ZERO) != 0;
        };
    }

    @Override
    public Function<Outstanding, DataQualityEvent> transformation(ConsumerDqContext context) {
        return exceptionRaisedEventTransformation(context);
    }

    @Override
    public String eventCode() {
        return "EXP_PSTDUAMTCHK2_IGNORED";
    }
}