
import java.util.*;

public final class ExactDPFrequentness implements FrequentnessCalculator {

    @Override
    public double probAtLeast(UncertainDatabase db, Itemset X, int msup) {
        double[] probs = probsPerTransaction(db, X);
        return Probability.dpTailAtLeast(msup, probs);
    }

    @Override
    public double mu(UncertainDatabase db, Itemset X) {
        double sum = 0;
        for (Transaction t : db.transactions())
            sum += probOfItemsetInTransaction(t, X);
        return sum;
    }

    @Override
    public double var(UncertainDatabase db, Itemset X) {
        double var = 0;
        for (Transaction t : db.transactions()) {
            double p = probOfItemsetInTransaction(t, X);
            var += p * (1.0 - p);
        }
        return var;
    }

    private static double[] probsPerTransaction(UncertainDatabase db, Itemset X) {
        int n = db.size();
        double[] arr = new double[n];
        int idx = 0;
        for (Transaction t : db.transactions()) {
            arr[idx++] = probOfItemsetInTransaction(t, X);
        }
        return arr;
    }

    /** Với giả định độc lập item trong 1 transaction: Pr(X⊆t) = Π_i p(i|t). */
    static double probOfItemsetInTransaction(Transaction t, Itemset X) {
        double p = 1.0;
        for (Item i : X.items()) {
            double pi = t.probOf(i);
            if (pi == 0.0)
                return 0.0;
            p *= pi;
        }
        return p;
    }
}
