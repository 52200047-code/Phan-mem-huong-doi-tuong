
import java.util.*;
import java.util.stream.Collectors;

/**
 * Thuật toán wPFI-Apriori với 3 lớp cắt tỉa:
 * 1) Trọng số nhỏ nhất trong X: chỉ mở rộng với I có w(I) < min_w(X) nếu I
 * không nằm trong I0.
 * 2) Ngưỡng μ̂ bằng Poisson: min(μ_X, μ_I) >= μ̂.
 * 3) Xấp xỉ μ_{X∪I} ~ μ_X * μ_I / n >= α μ̂.
 *
 * Xác minh cuối: EXACT (DP) hoặc APPROX (Poisson/Normal).
 */
public final class WeightedPFIMiner {

    private final UncertainDatabase db;
    private final Params params;
    private final FrequentnessCalculator freq;
    private final double maxItemWeight;
    private final Map<Item, Double> mu1 = new HashMap<>(); // μ cho 1-itemset

    public WeightedPFIMiner(UncertainDatabase db, Params params) {
        this.db = Objects.requireNonNull(db);
        this.params = Objects.requireNonNull(params);
        this.freq = (params.mode == Params.Mode.EXACT)
                ? new ExactDPFrequentness()
                : new ApproxFrequentness(params.approx);

        double m = 0;
        for (Item i : db.universe())
            m = Math.max(m, i.weight());
        this.maxItemWeight = (m <= 0 ? 1.0 : m);
    }

    /** Kết quả: Map<k, Set<Itemset>> theo kích thước. */
    public Map<Integer, Set<Itemset>> mine() {
        Map<Integer, Set<Itemset>> WPFI = new LinkedHashMap<>();
        // ---- Level 1 ----
        Set<Itemset> L1 = new LinkedHashSet<>();
        for (Item i : db.universe()) {
            Itemset X = new Itemset(Collections.singleton(i));
            double pAtLeast = freq.probAtLeast(db, X, params.msup);
            double score = X.avgWeight() * pAtLeast;
            if (score >= params.t) {
                double mu = freq.mu(db, X);
                X.setMu(mu);
                mu1.put(i, mu);
                L1.add(X);
            }
        }
        if (L1.isEmpty())
            return WPFI;
        WPFI.put(1, L1);

        // I0: tập item từng xuất hiện trong bất kỳ WPFI_{k-1}
        Set<Item> I0 = L1.stream().map(s -> s.items().first()).collect(Collectors.toCollection(LinkedHashSet::new));

        // ---- Higher levels ----
        int k = 2;
        Set<Itemset> Lprev = L1;

        // Pre-compute μ̂ cho candidate kiểm tra nhanh (dựa vào Poisson, như trong bài)
        // 1 - F(msup-1, μ̂) = t / mBound; mBound ta dùng max weight toàn cục cho đơn
        // giản & hiệu quả
        double muHat = solveMuHatPoisson(params.msup, params.t / maxItemWeight);

        while (!Lprev.isEmpty()) {
            Set<Itemset> Ck = generateCandidatesWithPruning(Lprev, I0, muHat); // gồm 3 cắt tỉa
            if (Ck.isEmpty())
                break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                double pAtLeast = freq.probAtLeast(db, X, params.msup);
                double score = X.avgWeight() * pAtLeast;
                if (score >= params.t) {
                    double mu = freq.mu(db, X);
                    X.setMu(mu);
                    Lk.add(X);
                }
            }

            if (Lk.isEmpty())
                break;
            WPFI.put(k, Lk);

            // cập nhật I0
            for (Itemset x : Lk)
                I0.addAll(x.items());

            Lprev = Lk;
            k++;
        }
        return WPFI;
    }

    /** Sinh ứng viên k từ L_{k-1} với 3 cắt tỉa. */
    private Set<Itemset> generateCandidatesWithPruning(Set<Itemset> Lprev, Set<Item> I0, double muHat) {
        Set<Itemset> Ck = new LinkedHashSet<>();
        int n = db.size();

        // index μ cho 1-itemset
        // (đã có trong mu1 cho các item xuất hiện ở L1; với item chưa vào L1 nhưng nằm
        // I0 cũng cần μ)
        for (Item i : db.universe())
            mu1.computeIfAbsent(i, it -> freq.mu(db, new Itemset(Collections.singleton(it))));

        for (Itemset X : Lprev) {
            // min weight trong X dùng cho cắt tỉa 1
            double minW = X.minItemWeight();
            // μ_X đã tính ở trước
            double muX = (X.getMu() != null) ? X.getMu() : freq.mu(db, X);

            // (A) mở rộng với item trong I0 \ X (không cần điều kiện w(I) < minW)
            for (Item I : I0) {
                if (X.items().contains(I))
                    continue;
                Itemset Y = X.unionWith(I);
                // Điều kiện w(X∪I) >= t (nhanh): nếu avg weight < t thì khỏi xác minh sâu
                if (avgWeightAfterUnion(X, I) < params.t)
                    continue;

                // Cắt tỉa 2: min(μ_X, μ_I) >= μ̂
                double muI = mu1.get(I);
                if (Math.min(muX, muI) < muHat)
                    continue;

                // Cắt tỉa 3: μ_X * μ_I / n >= α μ̂
                if (params.alpha > 0 && (muX * muI) < (params.alpha * n * muHat))
                    continue;

                Ck.add(Y);
            }
            // (B) mở rộng với item ngoài I0 (phải có w(I) < min_w(X))
            for (Item I : db.universe()) {
                if (X.items().contains(I))
                    continue;
                if (I0.contains(I))
                    continue;
                if (I.weight() >= minW)
                    continue; // cắt tỉa 1

                Itemset Y = X.unionWith(I);
                if (avgWeightAfterUnion(X, I) < params.t)
                    continue;

                double muI = mu1.get(I);
                if (Math.min(muX, muI) < muHat)
                    continue;
                if (params.alpha > 0 && (muX * muI) < (params.alpha * n * muHat))
                    continue;

                Ck.add(Y);
            }
        }
        return Ck;
    }

    /** avg weight cho X∪{I} (tính nhanh). */
    private static double avgWeightAfterUnion(Itemset X, Item I) {
        double sumW = X.avgWeight() * X.size() + I.weight();
        return sumW / (X.size() + 1);
    }

    /** Giải μ̂ từ 1 - F_Poisson(msup-1, μ̂) = rhs (dùng tìm kiếm nhị phân). */
    private static double solveMuHatPoisson(int msup, double rhs) {
        rhs = Math.max(1e-12, Math.min(1.0, rhs));
        double lo = 0.0, hi = Math.max(1.0, msup * 2.0);
        // nới hi đến khi tail >= rhs
        while (Probability.poissonTailAtLeast(msup, hi) < rhs)
            hi *= 2.0;
        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo + hi);
            double val = Probability.poissonTailAtLeast(msup, mid);
            if (val >= rhs)
                hi = mid;
            else
                lo = mid;
        }
        return 0.5 * (lo + hi);
    }
}
