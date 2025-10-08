package miner;

import entity.Item;
import entity.Itemset;

public class ApproxMuPruning implements PruningStrategy {
    private final double alpha;
    private final int n; // số giao dịch
    private final double muHat;

    public ApproxMuPruning(double alpha, int n, double muHat) {
        this.alpha = alpha;
        this.n = n;
        this.muHat = muHat;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return (muX * muI) < (alpha * n * muHat);
    }
}
