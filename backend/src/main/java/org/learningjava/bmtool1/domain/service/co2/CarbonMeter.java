// src/main/java/org/learningjava/bmtool1/domain/service/co2/CarbonMeter.java
package org.learningjava.bmtool1.domain.service.co2;

public interface CarbonMeter {
    /**
     * Estimate CO₂e (grams) for one prompt/response exchange.
     */
    double estimateGramsCO2e(String provider,
                             String model,
                             int promptTokens,
                             int completionTokens,
                             long latencyMs);
}
