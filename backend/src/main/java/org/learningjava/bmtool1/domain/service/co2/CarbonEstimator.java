// src/main/java/.../service/CarbonEstimator.java
package org.learningjava.bmtool1.domain.service.co2;

import org.learningjava.bmtool1.config.CarbonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CarbonEstimator {
    private static final Logger log = LoggerFactory.getLogger(CarbonEstimator.class);
    private final CarbonProperties props;

    public CarbonEstimator(CarbonProperties props) { this.props = props; }

    public double estimateGramsCO2(
            Integer promptTokens, Integer completionTokens,
            Long totalLatencyMs,
            String providerId, String modelId
    ) {
        double wh;
        Integer totalTok = safeAdd(promptTokens, completionTokens);

        if (totalTok != null && totalTok > 0) {
            double whPerKTkn = resolveWhPerKTkn(providerId, modelId);
            wh = (totalTok / 1000.0) * whPerKTkn;
            log.debug("CO2 via tokens: totalTok={}, whPerKTkn={}, Wh={}", totalTok, whPerKTkn, wh);
        } else {
            // fallback: time × watts
            long ms = totalLatencyMs == null ? 0L : totalLatencyMs;
            wh = (props.getLocalAvgWatts() * (ms / 1000.0)) / 3600.0;
            log.debug("CO2 via time: ms={}, avgWatts={}, Wh={}", ms, props.getLocalAvgWatts(), wh);
        }

        // Apply datacenter overhead (PUE) and grid intensity
        double kWh = (wh / 1000.0) * props.getPue();
        double kg = kWh * props.getGridKgPerKwh();
        double grams = kg * 1000.0;
        return Math.max(grams, 0.0);
    }

    private static Integer safeAdd(Integer a, Integer b) {
        if (a == null && b == null) return null;
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    private double resolveWhPerKTkn(String providerId, String modelId) {
        // exact override first
        if (providerId != null && modelId != null) {
            Double exact = props.getWhPerKtkn().get(providerId + ":" + modelId);
            if (exact != null) return exact;
        }

        String bucket = "default";
        String prov = (providerId == null ? "" : providerId.toLowerCase());
        String mid  = (modelId == null ? "" : modelId.toLowerCase());

        if (prov.contains("ollama")) {
            bucket = "llama";
        } else if (prov.contains("openrouter")) {
            if (mid.contains("gpt") || mid.startsWith("openai/")) {
                bucket = "gpt";
            }
        }
        return props.getWhPerKtkn().getOrDefault(bucket, props.getWhPerKtkn().getOrDefault("default", 2.0));
    }
}
