
import java.util.List;

/** Chiến lược tính Pr(Sup(X) >= msup). */
public interface FrequentnessCalculator {
    double probAtLeast(UncertainDatabase db, Itemset X, int msup);

    double mu(UncertainDatabase db, Itemset X); // μ_X

    double var(UncertainDatabase db, Itemset X); // σ²_X (cho Normal)
}
