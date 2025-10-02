package util;

import entity.Item;
import entity.Itemset;
import entity.Transaction;

import java.util.List;

public class WPFI_Metrics {

    /** Tính μ (expected support) cho một itemset */
    public static double computeMu(Itemset X, List<Transaction> db) {
        double mu = 0.0;
        for (Transaction t : db) {
            double p = 1.0;
            for (Item i : X.getItems()) {
                p *= t.getProb(i);
            }
            mu += p;
        }
        return mu;
    }

    /** Xác suất Sup(X) >= msup (dùng Poisson xấp xỉ để demo) */
    public static double poissonProbAtLeast(Itemset X, List<Transaction> db, int msup) {
        double mu = computeMu(X, db);
        // đơn giản hóa: P(X >= msup) ≈ 1 - F_Poisson(msup-1; mu)
        return 1.0 - Math.exp(-mu) * Math.pow(mu, msup-1) / factorial(msup-1);
    }

    private static double factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }
}
