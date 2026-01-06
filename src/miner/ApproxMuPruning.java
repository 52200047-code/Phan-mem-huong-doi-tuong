package miner;

import entity.Item;
import entity.Itemset;

/**
 * Approx μ-pruning:
 * prune nếu μX * μI < alpha * n * muHat
 *
 * Không phụ thuộc I0.
 */
public class ApproxMuPruning implements PruningStrategy {

    private final double alpha;
    private final int n;
    private final double muHat;
    private final boolean debug;

    public ApproxMuPruning(double alpha, int n, double muHat) {
        this(alpha, n, muHat, false);
    }

    public ApproxMuPruning(double alpha, int n, double muHat, boolean debug) {
        this.alpha = alpha;
        this.n = n;
        this.muHat = muHat;
        this.debug = debug;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return shouldPrune(X, i, muX, muI, true);
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI, boolean isFromI0) {
        if (alpha <= 0)
            return false; // tắt pruning nếu alpha<=0

        if (Double.isNaN(muX) || muX < 0)
            muX = 0.0;
        if (Double.isNaN(muI) || muI < 0)
            muI = 0.0;

        double left = muX * muI;
        double right = alpha * n * muHat;

        if (left < right) {
            if (debug) {
                System.out.printf(
                        "[PRUNE-APPROXMU] add %s to %s: muX*muI=%.6f < alpha*n*muHat=%.6f (alpha=%.4f, n=%d, muHat=%.6f)%n",
                        i.getName(), X.toString(), left, right, alpha, n, muHat);
            }
            return true;
        }
        return false;
    }

    public double getAlpha() {
        return alpha;
    }

    public int getN() {
        return n;
    }

    public double getMuHat() {
        return muHat;
    }
}
