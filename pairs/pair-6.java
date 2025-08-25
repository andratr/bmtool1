package com.example.dq.sp4.consumer.rules.limit.exposure_at_default_for_ifrs9;

import com.example.dq.foundation.data.MonetaryValue;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Limit;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * EAD For IFRS9 Currency is defaulted to system default value.
 *
 * <p><b>Rationale</b>: Standard currency conversion mapping exception</p>
 *
 * <p><b>Trigger</b>: This exception is raised if EAD for IFRS9 Currency cannot be mapped to a Vortex code. It is then defaulted to the defined default value per system.</p>
 *
 * <p><b>Transformation</b>: Raises the LIM_EIFR9CCYRDF_DEFAULTED event and sets the EAD For IFRS9 Currency to default value</p>
 */
public class LIM_EIFR9CCYRDF_DEFAULTED implements ConsumerTransformationRule<Limit> {
    @Override
    public Predicate<Limit> predicate(ConsumerDqContext context) {
        return eadIfr9AmountDelivered()
                .and(eadIfr9CurrencyIsNotValid());
    }

    @Override
    public Function<Limit, DataQualityEvent> transformation(ConsumerDqContext context) {
        return limit -> {
            String originalValue = limit.getEadIfr9().currency();
            String defaultedValue = context.getEnrichmentService().getDefault(currency).vortexReference();
            limit.setEadIfr9(new MonetaryValue(defaultedValue, limit.getEadIfr9().value()));
            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    limit,
                    originalValue,
                    defaultedValue
            );
        };
    }

    @Override
    public String eventCode() {
        return LIM_EIFR9CCYRDF_DEFAULTED;
    }

    private Predicate<Limit> eadIfr9CurrencyIsNotValid() {
        return limit -> Optional.ofNullable(limit.getEadIfr9())
                .map(eadIfr9 -> eadIfr9.currency() == null)
                .orElse(false);
    }

    private Predicate<Limit> eadIfr9AmountDelivered() {
        return limit -> Optional.ofNullable(limit.getEadIfr9())
                .map(eadIfr9 -> eadIfr9.value() != null)
                .orElse(false);
    }
}