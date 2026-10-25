package com.greenops.innovation.swarm;

import com.greenops.workflow.telemetry.TelemetryEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class BiometricAgent {
    public RiskAssessment assess(TelemetryEvent event) {
        var mouse = event.mouseTelemetry();
        double speed = Math.min(1, mouse.maxSpeedPixelsPerSecond() / 6000d);
        double sparse = mouse.sampleCount() == 0 ? 0.7 : 0;
        double score = Math.min(1, speed * .45 + mouse.directionChangeRate() * .3 + mouse.idleRatio() * .15 + sparse * .1);
        return new RiskAssessment("biometric", score, 0.35, Map.of("sampleCount", Integer.toString(mouse.sampleCount())));
    }
}
