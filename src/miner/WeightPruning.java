package miner;

import entity.Item;
import entity.Itemset;

public class WeightPruning implements PruningStrategy {
    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return i.getWeight() >= X.minItemWeight();
    }
}
