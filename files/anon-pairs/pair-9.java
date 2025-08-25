package com.example.dq.sp4.consumer.rules.limit.forbearance_measure_1_start_date;

import com.example.dq.foundation.data.OptionalDate;
import com.example.dq.foundation.events.DataQualityEvent;
import com.example.dq.sp4.consumer.glue.ConsumerDqContext;
import com.example.dq.sp4.consumer.glue.ConsumerTransformationRule;
import itrf.vortex.spdi.entities.consumer.Limit;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Forbearance Measure 1 Start Date is delivered but Forbearance Measure 1 is not populated. Forbearance Measure 1 Start Date is defaulted to NULL (empty).
 *
 * <p><b>Rationale</b>: Forbearance Measure 1 Start Date is only expected when Forbearance Measure 1 is populated (either delivered or defaulted).</p>
 *
 * <p><b>Trigger</b>: This exception is raised if:</p>
 * <ul>
 *     <li>Forbearance Measure Start Date is delivered</li>
 *     <li>and Forbearance Measure 1 is not populated (final value).</li>
 * </ul>
 *
 * <p><b>Transformation</b>: Raises the LIM_FORMS1SDPOP_NIL event and sets the forbearance measure 1 start date to null</p>
 */
public class LIM_FORMS1SDPOP_NIL implements ConsumerTransformationRule<Limit> {
    @Override
    public Predicate<Limit> predicate(ConsumerDqContext context) {
        return forbMeasStartDateDelivered()
                .and(forbMeasNotPopulated());
    }

    public Predicate<Limit> forbMeasStartDateDelivered() {
        return limit -> limit.getForbMeasStartDate().isPresent();
    }

    public Predicate<Limit> forbMeasNotPopulated() {
        return limit -> limit.getForbMeas() == null;
    }

    @Override
    public Function<Limit, DataQualityEvent> transformation(ConsumerDqContext context) {
        return limit -> {
            OptionalDate previousValue = limit.getForbMeasStartDate();
            limit.setForbMeasStartDate(OptionalDate.empty());
            return context.getEventService().createFieldModifiedEvent(
                    eventCode(),
                    limit,
                    previousValue,
                    limit.getForbMeasStartDate()
            );
        };
    }

    @Override
    public String eventCode() {
        return "LIM_FORMS1SDPOP_NIL";
    }
}