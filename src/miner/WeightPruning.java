package miner;

import entity.Item;
import entity.Itemset;
import util.Constants;

/**
 * Weight-based pruning.
 *
 * Theo paper (Corollary 1):
 * - Ràng buộc w(i) < min_w(X) chỉ áp dụng cho item i thuộc (I - I0).
 * - Với i thuộc I0 thì KHÔNG áp dụng ràng buộc này.
 *
 * Ngoài ra có ngưỡng MIN_AVG_WEIGHT để lọc ứng viên quá nhẹ.
 */
public class WeightPruning implements PruningStrategy {

    private final boolean debug;

    public WeightPruning() {
        this(false);
    }

    public WeightPruning(boolean debug) {
        this.debug = debug;
    }

    /**
     * API cũ: không biết i có thuộc I0 hay không, nên KHÔNG áp dụng Corollary 1 ở
     * đây
     * để tránh prune sai. Chỉ áp dụng MIN_AVG_WEIGHT.
     */
    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        double newAvgW = (X.avgWeight() * X.size() + i.getWeight()) / (X.size() + 1);
        if (newAvgW < Constants.MIN_AVG_WEIGHT) {
            if (debug) {
                System.out.printf("[PRUNE-WAVG] %s newAvgW=%.6f < MIN_AVG_WEIGHT%n", i.getName(), newAvgW);
            }
            return true;
        }
        return false;
    }

    /**
     * API mới: có cờ isFromI0.
     * - nếu isFromI0=false (i thuộc I - I0) => áp dụng Corollary 1: w(i) < min_w(X)
     * - nếu isFromI0=true (i thuộc I0) => bỏ qua điều kiện này
     */
    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI, boolean isFromI0) {

        // (1) Corollary 1: chỉ áp dụng cho item ngoài I0
        if (!isFromI0) {
            if (i.getWeight() >= X.minItemWeight()) {
                if (debug) {
                    System.out.printf("[PRUNE-W] %s w=%.6f >= minW(X)=%.6f (outside I0) => prune%n",
                            i.getName(), i.getWeight(), X.minItemWeight());
                }
                return true;
            }
        }

        // (2) MIN_AVG_WEIGHT: áp dụng cho tất cả
        double newAvgW = (X.avgWeight() * X.size() + i.getWeight()) / (X.size() + 1);
        if (newAvgW < Constants.MIN_AVG_WEIGHT) {
            if (debug) {
                System.out.printf("[PRUNE-WAVG] %s newAvgW=%.6f < MIN_AVG_WEIGHT%n", i.getName(), newAvgW);
            }
            return true;
        }

        return false;
    }
}
