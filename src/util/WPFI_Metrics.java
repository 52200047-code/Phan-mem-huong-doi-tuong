package util;

import entity.Item;
import entity.Itemset;
import entity.Transaction;

import java.util.List;

public class WPFI_Metrics {

    /* ---------- μ, σ² ---------- */

    /** μ_X = sum_t Pr(X⊆t) */
    public static double computeMu(Itemset X, List<Transaction> db) {
        double mu = 0.0;
        for (Transaction t : db) {
            mu += probOfItemsetInTransaction(t, X);
        }
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
            double pi = t.getProb(i); // p(i|t)
            if (pi <= 0.0)
                return 0.0;
            p *= pi;
            if (p == 0.0)
                return 0.0;
        }
        return p;
    }

    public static double[] probsPerTransaction(Itemset X, List<Transaction> db) {
        double[] arr = new double[db.size()];
        int idx = 0;
        for (Transaction t : db)
            arr[idx++] = probOfItemsetInTransaction(t, X);
        return arr;
    }

    /* ---------- Poisson Approximation ---------- */

    /** CDF Poisson F(k; μ) = P(X <= k) */
    public static double poissonCDF(int k, double mu) {
        if (k < 0)
            return 0.0;
        if (mu <= 0.0)
            return 1.0; // nếu μ=0 thì X=0 chắc chắn => P(X<=k)=1 với k>=0

        // exp(-mu) có thể underflow về 0 khi mu rất lớn -> khi đó CDF cho k nhỏ ~ 0
        double term = Math.exp(-mu);
        double sum = term; // i=0

        // nếu term=0 vì underflow, CDF gần như 0 cho k không quá lớn -> return 0
        if (term == 0.0)
            return 0.0;

        for (int i = 1; i <= k; i++) {
            term *= mu / i;
            sum += term;

            // nếu term quá nhỏ, cộng thêm không đáng kể nữa
            if (term < 1e-16 * sum)
                break;
        }
        return Math.min(1.0, Math.max(0.0, sum));
    }

    /** Tail P(X >= msup) ≈ 1 - F(msup-1; μ) */
    public static double poissonTailAtLeast(int msup, double mu) {
        if (msup <= 0)
            return 1.0;
        if (mu <= 0.0)
            return 0.0; // μ=0 => support luôn 0 => tail=0 nếu msup>0
        double cdf = poissonCDF(msup - 1, mu);
        double tail = 1.0 - cdf;
        if (tail < 0.0)
            tail = 0.0;
        if (tail > 1.0)
            tail = 1.0;
        return tail;
    }

    /** Tìm μ̂ sao cho 1 - F(msup-1; μ̂) = rhs (nhị phân) */
    public static double solveMuHatPoisson(int msup, double rhs) {
        rhs = Math.max(1e-12, Math.min(1.0, rhs));
        double lo = 0.0, hi = Math.max(1.0, msup * 2.0);

        while (poissonTailAtLeast(msup, hi) < rhs)
            hi *= 2.0;

        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo + hi);
            double val = poissonTailAtLeast(msup, mid);
            if (val >= rhs)
                hi = mid;
            else
                lo = mid;
        }
        return 0.5 * (lo + hi);
    }

    /* ---------- DP exact Poisson-Binomial tail ---------- */

    /**
     * DP chính xác Pr(Sup(X) >= msup) từ mảng p[t] = Pr(X⊆T_t).
     *
     * Ta dùng dp[0..msup-1] là P(S=s), và dp[msup] là P(S>=msup).
     *
     * Complexity: O(n * msup)
     */
    public static double dpTailAtLeast(int msup, double[] probs) {
        int n = probs.length;
        if (msup <= 0)
            return 1.0;
        msup = Math.min(msup, n);

        double[] dp = new double[msup + 1];
        dp[0] = 1.0;

        for (int t = 0; t < n; t++) {
            double p = probs[t];
            if (p <= 0.0)
                continue;
            if (p >= 1.0)
                p = 1.0;

            // cập nhật tail trước: newTail = oldTail + P(S=msup-1)*p
            dp[msup] = dp[msup] + dp[msup - 1] * p;

            // cập nhật các state còn lại từ msup-1 -> 1
            for (int s = msup - 1; s >= 1; s--) {
                dp[s] = dp[s] * (1.0 - p) + dp[s - 1] * p;
            }

            // s=0
            dp[0] = dp[0] * (1.0 - p);
        }

        // dp[msup] chính là P(S>=msup)
        if (dp[msup] < 0.0)
            dp[msup] = 0.0;
        if (dp[msup] > 1.0)
            dp[msup] = 1.0;
        return dp[msup];
    }

    /**
     * Helper: lấy Pr(Sup(X) >= msup) theo lựa chọn:
     * - useExact=true -> DP Poisson-Binomial
     * - useExact=false -> Poisson approximation
     */
    public static double tailAtLeast(int msup, Itemset X, List<Transaction> db, boolean useExact) {
        double mu = computeMu(X, db);
        if (!useExact)
            return poissonTailAtLeast(msup, mu);

        double[] probs = probsPerTransaction(X, db);
        return dpTailAtLeast(msup, probs);
    }
}
