package com.example.dq.sp4.consumer.rules.limit.exposure_at_default_for_regulatory_capital;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Limit;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * EAD For Regcap Currency is defaulted to system default value.
 *
 * <p><b>Rationale</b>: Standard currency conversion mapping exception</p>
 *
 * <p><b>Trigger</b>: This exception is raised if EAD for Regcap Currency cannot be mapped to a Vortex code.
 * It is then defaulted to the defined default value per system.</p>
 *
 * <p><b>Transformation</b>: Raises the LIM_ERCAPCCYRDF_DEFAULTED event and sets the default value to EAD For regcap currency</p>
 */
public class LIM_ERCAPCCYRDF_DEFAULTED implements ConsumerTransformationRule<Limit> {
    @Override
    public Predicate<Limit> predicate(ConsumerDqContext context) {
        return eadRegcapIsNotNull()
                .and(eadRegcapCurrencyIsNull());
    }

    @Override
    public Function<Limit, DataQualityEvent> transformation(ConsumerDqContext context) {
        return limit -> {
            MonetaryValue originalValue = limit.getEadRegcap();
            MonetaryValue defaultedValue = new MonetaryValue(
                    context.getEnrichmentService().getDefault("currency").vortexReference(),
                    originalValue.value()
            );
            limit.setEadRegcap(defaultedValue);
            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    limit,
                    originalValue.currency(),
                    defaultedValue.currency()
            );
        };
    }

    @Override
    public String eventCode() {
        return "LIM_ERCAPCCYRDF_DEFAULTED";
    }

    private Predicate<Limit> eadRegcapCurrencyIsNull() {
        return limit -> Optional.ofNullable(limit.getEadRegcap())
                .map(regCap -> regCap.currency() == null)
                .orElse(false);
    }

    private Predicate<Limit> eadRegcapIsNotNull() {
        return limit -> limit.getEadRegcap() != null;
    }
}