// src/main/java/org/learningjava/bmtool1/config/CarbonProperties.java
package org.learningjava.bmtool1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "carbon")
public class CarbonProperties {
    private double gridKgPerKwh = 0.35;
    private double pue = 1.2;
    private double localAvgWatts = 200.0;
    private Map<String, Double> whPerKtkn = Map.of(
            "default", 2.0,
            "llama",   1.5,
            "gpt",     3.0
    );

    public double getGridKgPerKwh() { return gridKgPerKwh; }
    public void setGridKgPerKwh(double v) { this.gridKgPerKwh = v; }
    public double getPue() { return pue; }
    public void setPue(double v) { this.pue = v; }
    public double getLocalAvgWatts() { return localAvgWatts; }
    public void setLocalAvgWatts(double v) { this.localAvgWatts = v; }
    public Map<String, Double> getWhPerKtkn() { return whPerKtkn; }
    public void setWhPerKtkn(Map<String, Double> m) { this.whPerKtkn = m; }
}
