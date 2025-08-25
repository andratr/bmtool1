package com.example.dq.sp4.consumer.rules.limit.limit_amount;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.enrichment.entities.FunctionalParameter;
import itrf.vortex.spdi.entities.consumer.Limit;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.example.dq.sp4.consumer.rules.limit.LimitUtil.relatedRiskRatingHasProblems;

/**
 * Limit Amount is less than sum of Outstanding Nominal Amounts.
 *
 * <p><b>Rationale</b>: Outstanding Nominal Amount cannot be greater than the Limit Amount.</p>
 *
 * <p><b>Trigger</b>: This exception is raised if:</p>
 * <ul>
 *     <li>Limit Amount is not zero</li>
 *     <li>and sum of Outstanding Nominal Amounts for all outstandings is greater than the Limit Amount</li>
 *     <li>and the Risk Rating of the related borrower is Performing (automatic transfers may cause an excess. Exposure will be then be in default and excess is known and accepted)</li>
 *     <li>and excess (i.e. Outstanding – Limit) => 250 Euro  (for very small exposures no exception should be raised).</li>
 * </ul>
 *
 * <p><b>Transformation</b>: Raises LIM_LIMAMTCHK5_IGNORED event</p>
 */
public class LIM_LIMAMTCHK5_IGNORED implements ConsumerTransformationRule<Limit> {
    @Override
    public Predicate<Limit> predicate(ConsumerDqContext context) {
        return limitAmountNotZero(context)
                .and(outstandingNominalAmountsSumGreaterThanLimitAmount(context))
                .and(riskRatingOfRelatedBorrowerIsPerforming(context))
                .and(outstandingNominalAmountExcess(context));
    }

    @Override
    public Function<Limit, DataQualityEvent> transformation(ConsumerDqContext context) {
        return exceptionRaisedEventTransformation(context);
    }

    @Override
    public String eventCode() {
        return "LIM_LIMAMTCHK5_IGNORED";
    }

    private Predicate<Limit> limitAmountNotZero(ConsumerDqContext context) {
        return limit -> amountIsNotNull(limit.getLimitAmount())
                && !Objects.equals(convertToEuro(context, limit.getLimitAmount()), BigDecimal.ZERO);
    }

    private Predicate<Limit> outstandingNominalAmountsSumGreaterThanLimitAmount(ConsumerDqContext context) {
        return limit -> {
            BigDecimal outstandingNominalAmount = outstandingNominalAmountsSum(context, limit);
            BigDecimal limitAmount = convertToEuro(context, limit.getLimitAmount());
            return outstandingNominalAmount != null
                    && limitAmount != null
                    && outstandingNominalAmount.compareTo(limitAmount) > 0;
        };
    }

    private Predicate<Limit> riskRatingOfRelatedBorrowerIsPerforming(ConsumerDqContext context) {
        return limit -> !relatedRiskRatingHasProblems(limit, context);
    }

    private Predicate<Limit> outstandingNominalAmountExcess(ConsumerDqContext context) {
        return limit -> {
            BigDecimal outstandingNominalAmount = outstandingNominalAmountsSum(context, limit);
            BigDecimal limitAmount = convertToEuro(context, limit.getLimitAmount());

            BigDecimal maxAllowedExcess = context.getEnrichmentService()
                    .getFunctionalParameters("MAX_ALLOWED_EXCESS")
                    .stream()
                    .filter(fp -> fp.recordValidUntil().isEmpty())
                    .map(FunctionalParameter::numericValue)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            return outstandingNominalAmount != null
                    && limitAmount != null
                    && outstandingNominalAmount.subtract(limitAmount)
                    .compareTo(maxAllowedExcess) >= 0;
        };
    }

    private boolean amountIsNotNull(MonetaryValue amount) {
        return amount != null && amount.value() != null;
    }

    private BigDecimal convertToEuro(ConsumerDqContext context, MonetaryValue amount) {
        if (!amountIsNotNull(amount)) {
            return null;
        }
        MonetaryValue eur = context.getEnrichmentService().convertToEuro(amount);
        return eur == null ? null : eur.value();
    }

    private BigDecimal outstandingNominalAmountsSum(ConsumerDqContext context, Limit limit) {
        return limit.getOutstandings()
                .stream()
                .map(o -> convertToEuro(context, o.getOutstandingAmount()))
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }
}