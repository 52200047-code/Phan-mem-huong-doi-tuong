package miner;

import entity.Item;
import entity.Itemset;

/**
 * PruningStrategy:
 * - shouldPrune(X, i, muX, muI): giữ để tương thích code cũ.
 * - shouldPrune(X, i, muX, muI, isFromI0): dùng khi cần phân biệt item thuộc I0
 * hay không
 * (đặc biệt cho Corollary 1 / Weight pruning).
 */
public interface PruningStrategy {

    /** API cũ (compat) */
    boolean shouldPrune(Itemset X, Item i, double muX, double muI);

    /**
     * API mới:
     * 
     * @param isFromI0 true nếu i thuộc I0, false nếu i thuộc I - I0
     *
     *                 Default: nếu class chưa override thì dùng logic cũ.
     */
    default boolean shouldPrune(Itemset X, Item i, double muX, double muI, boolean isFromI0) {
        return shouldPrune(X, i, muX, muI);
    }
}
