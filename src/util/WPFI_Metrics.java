package util;

import entity.Item;
import entity.Itemset;
import entity.Transaction;

import java.util.List;

public class WPFI_Metrics {

    /* μ, σ² */

    /** μ_X = sum_t Pr(X⊆t) */
    public static double computeMu(Itemset X, List<Transaction> db) {
        double mu = 0.0;
        for (Transaction t : db) mu += probOfItemsetInTransaction(t, X);
        return mu;
    }

    /** σ²_X = sum_t p(1-p) */
    public static double computeVar(Itemset X, List<Transaction> db) {
        double v = 0.0;
        for (Transaction t : db) {
            double p = probOfItemsetInTransaction(t, X);
            v += p * (1.0 - p);
        }
        return v;
    }

    /** Giả định độc lập item trong 1 giao dịch: Pr(X⊆t) = ∏ p(i|t) */
    public static double probOfItemsetInTransaction(Transaction t, Itemset X) {
        double p = 1.0;
        for (Item i : X.getItems()) {
            double pi = t.getProb(i);
            if (pi == 0.0) return 0.0;
            p *= pi;
        }
        return p;
    }

    /* Poisson */

    /** CDF Poisson F(k; μ) = P(X <= k) */
    public static double poissonCDF(int k, double mu) {
        if (k < 0) return 0.0;
        double sum = 0.0, term = Math.exp(-mu);
        sum += term; // k=0
        for (int i = 1; i <= k; i++) {
            term *= mu / i;
            sum += term;
        }
        return sum;
    }

    /** Tail P(X >= msup) ≈ 1 - F(msup-1; μ) */
    public static double poissonTailAtLeast(int msup, double mu) {
        if (msup <= 0) return 1.0;
        return 1.0 - poissonCDF(msup - 1, mu);
    }

    /** Tìm μ̂ sao cho 1 - F(msup-1; μ̂) = rhs (nhị phân) */
    public static double solveMuHatPoisson(int msup, double rhs) {
        rhs = Math.max(1e-12, Math.min(1.0, rhs));
        double lo = 0.0, hi = Math.max(1.0, msup * 2.0);
        while (poissonTailAtLeast(msup, hi) < rhs) hi *= 2.0;
        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo + hi);
            double val = poissonTailAtLeast(msup, mid);
            if (val >= rhs) hi = mid; else lo = mid;
        }
        return 0.5 * (lo + hi);
    }

    /* DP exact cho tail Poisson-Binomial */

    /** DP chính xác Pr(Sup(X) >= msup) từ mảng p[t] = Pr(X⊆T_t). O(n*msup) */
    public static double dpTailAtLeast(int msup, double[] probs) {
        int n = probs.length;
        msup = Math.min(msup, n);
        double[] dp = new double[msup + 1];
        dp[0] = 1.0;
        for (int t = 0; t < n; t++) {
            double p = probs[t];
            for (int s = msup; s >= 0; s--) {
                double stay = dp[s] * (1.0 - p);
                double go   = (s > 0 ? dp[s - 1] : 0.0) * p;
                dp[s] = stay + go;
            }
        }
        return dp[msup];
    }

    public static double[] probsPerTransaction(Itemset X, List<Transaction> db) {
        double[] arr = new double[db.size()];
        int idx = 0;
        for (Transaction t : db) arr[idx++] = probOfItemsetInTransaction(t, X);
        return arr;
    }
}
