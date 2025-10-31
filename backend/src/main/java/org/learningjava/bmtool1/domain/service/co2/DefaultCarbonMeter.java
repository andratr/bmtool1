// src/main/java/org/learningjava/bmtool1/domain/service/co2/DefaultCarbonMeter.java
package org.learningjava.bmtool1.domain.service.co2;

import org.learningjava.bmtool1.config.CarbonProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultCarbonMeter implements CarbonMeter {

    private final CarbonProperties cfg;

    public DefaultCarbonMeter(CarbonProperties cfg) {
        this.cfg = cfg;
    }

    @Override
    public double estimateGramsCO2e(String provider,
                                    String model,
                                    int promptTokens,
                                    int completionTokens,
                                    long latencyMs) {
        double kWh = energyKWh(provider, model, promptTokens + completionTokens, latencyMs);
        double kg  = kWh * cfg.getGridKgPerKwh();
        return kg * 1000.0; // grams
    }

    private double energyKWh(String provider, String model, int totalTokens, long latencyMs) {
        if (totalTokens > 0) {
            String fam = family(provider, model);
            double whPerK = cfg.getWhPerKtkn().getOrDefault(
                    fam, cfg.getWhPerKtkn().getOrDefault("default", 2.0)
            );
            double wh = (totalTokens / 1000.0) * whPerK;
            return (wh / 1000.0) * cfg.getPue(); // Wh→kWh, include PUE
        }
        double hours = Math.max(1, latencyMs) / 3_600_000.0;
        return ((cfg.getLocalAvgWatts() * hours) / 1000.0) * cfg.getPue();
    }

    private String family(String provider, String model) {
        String mid = model == null ? "" : model.toLowerCase();
        String pid = provider == null ? "" : provider.toLowerCase();

        if (pid.contains("ollama") || mid.contains("llama") || mid.contains("mistral")
                || mid.contains("qwen") || mid.contains("gemma")) return "llama";
        if (pid.contains("openrouter") && (mid.contains("gpt") || mid.startsWith("openai/"))) return "gpt";

        return "default";
    }
}
