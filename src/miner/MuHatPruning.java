package miner;

import entity.Item;
import entity.Itemset;

/**
 * μ̂-pruning (theo Poisson bound):
 * prune nếu min(μX, μI) < μ̂
 *
 * Không phụ thuộc I0 hay không (cùng logic).
 */
public class MuHatPruning implements PruningStrategy {

    private final double muHat;
    private final boolean debug;

    public MuHatPruning(double muHat) {
        this(muHat, false);
    }

    public MuHatPruning(double muHat, boolean debug) {
        this.muHat = muHat;
        this.debug = debug;
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI) {
        return shouldPrune(X, i, muX, muI, true);
    }

    @Override
    public boolean shouldPrune(Itemset X, Item i, double muX, double muI, boolean isFromI0) {
        // normalize
        if (Double.isNaN(muX) || muX < 0)
            muX = 0.0;
        if (Double.isNaN(muI) || muI < 0)
            muI = 0.0;

        double m = Math.min(muX, muI);
        if (m < muHat) {
            if (debug) {
                System.out.printf("[PRUNE-MUHAT] add %s to %s: min(muX=%.6f, muI=%.6f)=%.6f < muHat=%.6f%n",
                        i.getName(), X.toString(), muX, muI, m, muHat);
            }
            return true;
        }
        return false;
    }

    public double getMuHat() {
        return muHat;
    }
}
