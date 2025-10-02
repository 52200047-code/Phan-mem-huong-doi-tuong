
import java.util.*;

/** Ước lượng Pr(Sup >= msup) bằng Poisson hoặc Normal, có cache μ, σ². */
public final class ApproxFrequentness implements FrequentnessCalculator {
    private final Params.ApproxFamily family;

    // cache để tránh tính lặp lại
    private final Map<Itemset, Double> muCache = new HashMap<>();
    private final Map<Itemset, Double> varCache = new HashMap<>();

    public ApproxFrequentness(Params.ApproxFamily family) {
        this.family = family;
    }

    @Override
    public double probAtLeast(UncertainDatabase db, Itemset X, int msup) {
        double mu = mu(db, X);
        if (family == Params.ApproxFamily.POISSON) {
            return Probability.poissonTailAtLeast(msup, mu);
        } else {
            double var = var(db, X);
            return Probability.normalTailAtLeast(msup, mu, var);
        }
    }

    @Override
    public double mu(UncertainDatabase db, Itemset X) {
        return muCache.computeIfAbsent(X, k -> {
            double sum = 0;
            for (Transaction t : db.transactions()) {
                double p = 1.0;
                for (Item i : X.items()) {
                    double pi = t.probOf(i);
                    if (pi == 0) {
                        p = 0;
                        break;
                    }
                    p *= pi;
                }
                sum += p;
            }
            return sum;
        });
    }

    @Override
    public double var(UncertainDatabase db, Itemset X) {
        return varCache.computeIfAbsent(X, k -> {
            double v = 0;
            for (Transaction t : db.transactions()) {
                double p = 1.0;
                for (Item i : X.items()) {
                    double pi = t.probOf(i);
                    if (pi == 0) {
                        p = 0;
                        break;
                    }
                    p *= pi;
                }
                v += p * (1.0 - p);
            }
            return v;
        });
    }
}
