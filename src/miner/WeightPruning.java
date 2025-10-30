package miner;

import entity.Item;
import entity.Itemset;
import util.Constants;

/**
 * Chiến lược cắt tỉa theo trọng số (Weight-based pruning).
 * 
 * Nguyên lý:
 *  - Nếu item mới có trọng số >= min_w(X) thì bỏ qua (theo WPFI-Apriori).
 *  - Hoặc nếu trọng số trung bình sau khi thêm nhỏ hơn MIN_AVG_WEIGHT thì bỏ qua.
 */
public class WeightPruning implements PruningStrategy {

    private final boolean debug;

    public WeightPruning() {
        this(false);
    }

    public WeightPruning(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        // (1) Quy tắc weight pruning cơ bản
        if (i.getWeight() >= X.minItemWeight()) {
            if (debug) {
                System.out.printf("[PRUNE-W] %s weight >= min(X) => prune%n", i.getName());
            }
            return true;
        }

        // (2) Quy tắc trọng số trung bình
        double newAvgW = (X.avgWeight() * X.size() + i.getWeight()) / (X.size() + 1);
        if (newAvgW < Constants.MIN_AVG_WEIGHT) {
            if (debug) {
                System.out.printf("[PRUNE-WAVG] %s avgW=%.3f < MIN_AVG_WEIGHT%n", i.getName(), newAvgW);
            }
            return true;
        }

        return false;
    }
}
