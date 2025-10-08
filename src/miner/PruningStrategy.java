package miner;

import entity.Item;
import entity.Itemset;

public interface PruningStrategy {
    boolean shouldPrune(Itemset X, Item i, double muX, double muI);
}
