package miner;

import db.UncertainDatabase;
import entity.Item;
import entity.Itemset;
import entity.Transaction;
import util.Constants;
import util.WPFI_Metrics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thuật toán wPFI-Apriori với 3 cắt tỉa:
 *  (1) Weight-pruning: nếu I ∉ I0 thì yêu cầu w(I) < min_w(X)
 *  (2) μ̂-pruning (Poisson): min(μ_X, μ_I) >= μ̂
 *  (3) Approx μ pruning: μ_X * μ_I / n >= α * μ̂
 * Xác minh cuối: w(X) * Pr(Sup>=msup) >= t (Poisson tail hoặc DP nếu muốn).
 */
public class WPFI_Apriori {

    private final UncertainDatabase db;

    public WPFI_Apriori(UncertainDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    public Set<Itemset> mine() {
        // vũ trụ item
        SortedSet<Item> universe = collectUniverse(db);

        // μ cho từng 1-item để dùng nhanh ở các bước
        Map<Item, Double> mu1 = new HashMap<>();
        for (Item i : universe) mu1.put(i, WPFI_Metrics.computeMu(new Itemset(Set.of(i)), db.getTransactions()));

        // max weight để tính mBound trong μ̂
        double maxW = universe.stream().mapToDouble(Item::getWeight).max().orElse(1.0);
        if (maxW <= 0) maxW = 1.0;

        // μ̂ từ 1 - F(msup-1; μ̂) = t / maxW
        final double muHat = WPFI_Metrics.solveMuHatPoisson(Constants.MSUP, Constants.T / maxW);
        final int n = db.size();

        // ---- Level 1 ----
        Set<Itemset> Lprev = new LinkedHashSet<>();
        Map<Itemset, Double> muMap = new HashMap<>();

        for (Item i : universe) {
            Itemset X = new Itemset(Set.of(i));
            double mu = mu1.get(i);
            muMap.put(X, mu);

            // xác minh cuối (Poisson tail nhanh)
            double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
            double score = X.avgWeight() * pTail;
            if (score >= Constants.T) Lprev.add(X);
        }

        // kết quả
        Set<Itemset> all = new LinkedHashSet<>(Lprev);
        if (Lprev.isEmpty()) return all;

        // I0: các item xuất hiện trong bất kỳ WPFI_{k-1}
        Set<Item> I0 = Lprev.stream()
                .flatMap(s -> s.getItems().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int k = 2;
        while (!Lprev.isEmpty()) {
            Set<Itemset> Ck = generateCandidatesWithPruning(Lprev, universe, I0, muMap, mu1, muHat, n);
            if (Ck.isEmpty()) break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                // xác minh cuối
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                muMap.put(X, mu);
                double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                double score = X.avgWeight() * pTail;
                if (score >= Constants.T) Lk.add(X);
            }

            if (Lk.isEmpty()) break;

            all.addAll(Lk);
            // cập nhật I0
            for (Itemset x : Lk) I0.addAll(x.getItems());
            Lprev = Lk;
            k++;
        }

        return all;
    }

    /* ------------------ helpers ------------------ */

    private static SortedSet<Item> collectUniverse(UncertainDatabase db) {
        SortedSet<Item> set = new TreeSet<>();
        for (Transaction t : db.getTransactions()) set.addAll(t.getItems().keySet());
        return set;
    }

    private static Set<Itemset> generateCandidatesWithPruning(
            Set<Itemset> Lprev,
            SortedSet<Item> universe,
            Set<Item> I0,
            Map<Itemset, Double> muMap,
            Map<Item, Double> mu1,
            double muHat,
            int n
    ) {
        Set<Itemset> Ck = new LinkedHashSet<>();

        for (Itemset X : Lprev) {
            double muX = muMap.getOrDefault(X, 0.0);
            double minW = X.minItemWeight();

            // (A) mở rộng với item trong I0 \ X (không cần điều kiện w(I) < minW)
            for (Item I : I0) {
                if (X.getItems().contains(I)) continue;
                if (avgWeightAfterUnion(X, I) < Constants.T) continue;

                double muI = mu1.get(I);
                // prune 2: min(μ_X, μ_I) >= μ̂
                if (Math.min(muX, muI) < muHat) continue;
                // prune 3: μ_X * μ_I / n >= α μ̂
                if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;

                Ck.add(X.unionWith(I));
            }

            // (B) mở rộng với item ngoài I0: yêu cầu w(I) < min_w(X)
            for (Item I : universe) {
                if (X.getItems().contains(I)) continue;
                if (I0.contains(I)) continue;
                if (I.getWeight() >= minW) continue; // prune 1

                if (avgWeightAfterUnion(X, I) < Constants.T) continue;

                double muI = mu1.get(I);
                if (Math.min(muX, muI) < muHat) continue;
                if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;

                Ck.add(X.unionWith(I));
            }
        }
        return Ck;
    }

    private static double avgWeightAfterUnion(Itemset X, Item I) {
        double sum = X.avgWeight() * X.size() + I.getWeight();
        return sum / (X.size() + 1);
    }
}
