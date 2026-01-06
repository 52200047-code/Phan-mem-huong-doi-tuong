package miner;

import entity.Item;
import entity.Itemset;
import util.WPFI_Metrics;

/**
 * UB-Score pruning (Upper Bound Score):
 * Prune Y = X ∪ {i} nếu:
 * upperScore = avgW(Y) * pTail(msup, min(muX, muI)) < T
 *
 * Lưu ý: đây là "safe" theo cách tính score dùng Poisson tail (approx).
 */
public class UBScorePruning implements PruningStrategy {

    private final int msup;
    private final double thresholdT;
    private final boolean debug;

    public UBScorePruning(int msup, double thresholdT) {
        this(msup, thresholdT, false);
    }

    public UBScorePruning(int msup, double thresholdT, boolean debug) {
        this.msup = msup;
        this.thresholdT = thresholdT;
        this.debug = debug;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return shouldPrune(X, i, muX, muI, true);
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI, boolean isFromI0) {
        if (Double.isNaN(muX) || muX < 0)
            muX = 0.0;
        if (Double.isNaN(muI) || muI < 0)
            muI = 0.0;

        // muUpper = min(muX, muI)
        double muUpper = Math.min(muX, muI);

        // pTail upper (theo Poisson approx đang dùng trong score)
        double pUpper = WPFI_Metrics.poissonTailAtLeast(msup, muUpper);

        // avgW(Y) tính được ngay
        double avgWY = (X.avgWeight() * X.size() + i.getWeight()) / (X.size() + 1);

        double upperScore = avgWY * pUpper;

        if (upperScore < thresholdT) {
            if (debug) {
                System.out.printf("[PRUNE-UBSCORE] add %s to %s: upperScore=%.6f < T=%.6f%n",
                        i.getName(), X.toString(), upperScore, thresholdT);
            }
            return true;
        }

        return false;
    }
}
