package com.greenops.innovation.federated;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** In-memory differentially private gradient aggregation; production deployments should persist encrypted model state. */
@Component
public final class FederatedGradientAggregator {
    private final SecureRandom random = new SecureRandom();
    private final double clipNorm;
    private final double noiseSigma;
    private final AtomicReference<double[]> globalWeights = new AtomicReference<>(new double[0]);

    public FederatedGradientAggregator() { this(1.0, 0.15); }
    public FederatedGradientAggregator(double clipNorm, double noiseSigma) {
        if (!Double.isFinite(clipNorm) || clipNorm <= 0 || !Double.isFinite(noiseSigma) || noiseSigma < 0)
            throw new IllegalArgumentException("invalid DP configuration");
        this.clipNorm = clipNorm; this.noiseSigma = noiseSigma;
    }

    /** Accepts already-trained gradients only; callers must not send raw behavioral vectors. */
    public synchronized AggregateResult aggregate(String clientUpdateId, double[] gradient) {
        if (clientUpdateId == null || clientUpdateId.isBlank() || gradient == null || gradient.length == 0 || gradient.length > 100_000)
            throw new IllegalArgumentException("invalid client update");
        double normSquared = 0;
        for (double value : gradient) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("gradient values must be finite");
            normSquared += value * value;
        }
        double scale = Math.min(1, clipNorm / Math.sqrt(normSquared));
        double[] current = globalWeights.get();
        if (current.length != 0 && current.length != gradient.length) throw new IllegalArgumentException("gradient dimension mismatch");
        if (current.length == 0) current = new double[gradient.length];
        double[] next = Arrays.copyOf(current, current.length);
        for (int i = 0; i < next.length; i++) next[i] += gradient[i] * scale + gaussian() * noiseSigma * clipNorm;
        globalWeights.set(next);
        return new AggregateResult(clientUpdateId, next.length, noiseSigma > 0, Arrays.hashCode(next));
    }
    public double[] snapshot() { return globalWeights.get().clone(); }
    private double gaussian() { return random.nextGaussian(); }
    public record AggregateResult(String updateId, int dimensions, boolean noiseApplied, int modelVersion) {}
}
