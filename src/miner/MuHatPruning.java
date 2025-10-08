package miner;

import entity.Item;
import entity.Itemset;

public class MuHatPruning implements PruningStrategy {
    private final double muHat; // ngưỡng μ̂

    public MuHatPruning(double muHat) {
        this.muHat = muHat;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return Math.min(muX, muI) < muHat;
    }
}
