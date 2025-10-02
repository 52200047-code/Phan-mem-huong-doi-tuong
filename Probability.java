
import java.util.*;

/** Hàm xác suất: Poisson, Normal, DP tính Pr(Sup(X) >= msup). */
public final class Probability {
    private Probability() {
    }

    // ----- Poisson -----
    /** CDF Poisson F(k; mu) = P(X <= k). */
    public static double poissonCDF(int k, double mu) {
        if (k < 0)
            return 0.0;
        double sum = 0.0, term = Math.exp(-mu);
        sum += term; // k=0
        for (int i = 1; i <= k; i++) {
            term *= mu / i;
            sum += term;
        }
        return sum;
    }

    /** P(X >= msup) under Poisson ~ 1 - F(msup-1). */
    public static double poissonTailAtLeast(int msup, double mu) {
        if (msup <= 0)
            return 1.0;
        return 1.0 - poissonCDF(msup - 1, mu);
    }

    // ----- Normal -----
    /** CDF chuẩn (0,1). */
    public static double normalCDF(double z) {
        // Abramowitz-Stegun approximation
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = Math.exp(-0.5 * z * z) / Math.sqrt(2 * Math.PI);
        double p = 1.0 - d * (0.319381530 * t - 0.356563782 * t * t + 1.781477937 * Math.pow(t, 3)
                - 1.821255978 * Math.pow(t, 4) + 1.330274429 * Math.pow(t, 5));
        return z >= 0 ? p : 1.0 - p;
    }

    /** Ước lượng Pr(Sup >= msup) bằng Normal(μ,σ²) + continuity correction. */
    public static double normalTailAtLeast(int msup, double mu, double var) {
        if (msup <= 0)
            return 1.0;
        if (var <= 0) {
            // thoái hoá: nếu μ >= msup thì 1 else 0
            return mu >= msup ? 1.0 : 0.0;
        }
        double sigma = Math.sqrt(var);
        double z = ((msup - 0.5) - mu) / sigma; // continuity correction
        return 1.0 - normalCDF(z);
    }

    // ----- DP chính xác cho Poisson-Binomial: Pr(Sup >= msup) -----
    /**
     * DP tính chính xác Pr(Sup(X) >= msup) từ mảng p[t] = Pr(X ⊆ T_t).
     * Time: O(n*msup).
     */
    public static double dpTailAtLeast(int msup, double[] probs) {
        int n = probs.length;
        msup = Math.min(msup, n);
        // dp[s] = Pr(sum == s) sau khi duyệt t giao dịch (rolling)
        double[] dp = new double[msup + 1];
        dp[0] = 1.0;

        double prReached = 0.0; // tích lũy Pr(sum >= msup)
        for (int t = 0; t < n; t++) {
            double p = probs[t];
            // cập nhật ngược để không đè
            for (int s = msup; s >= 0; s--) {
                double stay = dp[s] * (1.0 - p);
                double go = (s > 0 ? dp[s - 1] : 0.0) * p;
                dp[s] = stay + go;
            }
        }
        // tail
        for (int s = msup; s <= msup; s++)
            prReached += dp[s]; // dp chỉ tới msup
        // thêm phần > msup (không lưu), nhưng ta “dồn” vào msup khi cập nhật =>
        // dp[msup] là tail
        return dp[msup];
    }
}
