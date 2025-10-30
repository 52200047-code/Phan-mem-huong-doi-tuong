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
 * Thuật toán Weighted Probabilistic Frequent Itemset (WPFI) – mở rộng Apriori.
 *
 * Các kỹ thuật cắt tỉa (Pruning):
 * (1) Weight-Pruning: với item ngoài I0, yêu cầu w(I) < min_w(X)
 * (2) μ̂-Pruning (Poisson bound): min(μ_X, μ_I) ≥ μ̂
 * (3) Approx μ-Pruning: μ_X * μ_I / n ≥ α * μ̂
 *
 * Xác minh cuối cùng:
 *     w(X) * Pr(Sup(X) ≥ msup) ≥ T
 */
public class WPFI_Apriori {

    private final UncertainDatabase db;

    public WPFI_Apriori(UncertainDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    public Set<Itemset> mine() {
        // 1️⃣ Thu thập vũ trụ item
        SortedSet<Item> universe = collectUniverse(db);

        // 2️⃣ Tính trước μ cho từng item đơn
        Map<Item, Double> mu1 = new HashMap<>();
        for (Item i : universe) {
            mu1.put(i, WPFI_Metrics.computeMu(new Itemset(Set.of(i)), db.getTransactions()));
        }

        // 3️⃣ Tính μ̂ (muHat) từ tham số Poisson
        double maxW = universe.stream().mapToDouble(Item::getWeight).max().orElse(1.0);
        if (maxW <= 0) maxW = 1.0;
        final double muHat = WPFI_Metrics.solveMuHatPoisson(Constants.MSUP, Constants.T / maxW);
        final int n = db.size();

        // 4️⃣ Bước 1: duyệt các 1-itemset
        Set<Itemset> Lprev = new LinkedHashSet<>();
        Map<Itemset, Double> muMap = new HashMap<>();

        for (Item i : universe) {
            Itemset X = new Itemset(Set.of(i));
            double mu = mu1.get(i);
            muMap.put(X, mu);

            double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
            double score = X.avgWeight() * pTail;

            if (score >= Constants.T) {
                Lprev.add(X);
            }
        }

        Set<Itemset> all = new LinkedHashSet<>(Lprev);
        if (Lprev.isEmpty()) return all;

        // I0: tập tất cả item từng xuất hiện trong các tập phổ biến trước đó
        Set<Item> I0 = Lprev.stream()
                .flatMap(s -> s.getItems().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int k = 2;
        while (!Lprev.isEmpty()) {
            Set<Itemset> Ck = generateCandidatesWithPruning(Lprev, universe, I0, muMap, mu1, muHat, n);
            if (Ck.isEmpty()) break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                muMap.put(X, mu);

                double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                double score = X.avgWeight() * pTail;

                if (score >= Constants.T) {
                    Lk.add(X);
                }
            }

            if (Lk.isEmpty()) break;

            all.addAll(Lk);
            for (Itemset x : Lk) I0.addAll(x.getItems());
            Lprev = Lk;
            k++;
        }

        return all;
    }

    /* ------------------ Helpers ------------------ */

    private static SortedSet<Item> collectUniverse(UncertainDatabase db) {
        SortedSet<Item> set = new TreeSet<>();
        for (Transaction t : db.getTransactions()) {
            set.addAll(t.getItems());
        }
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

            // (A) Mở rộng với item trong I0
            for (Item I : I0) {
                if (X.getItems().contains(I)) continue;
                if (avgWeightAfterUnion(X, I) < Constants.MIN_AVG_WEIGHT) continue;

                double muI = mu1.get(I);
                if (Math.min(muX, muI) < muHat) continue;
                if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;

                Ck.add(X.unionWith(I));
            }

            // (B) Mở rộng với item ngoài I0 (có weight nhỏ hơn)
            for (Item I : universe) {
                if (X.getItems().contains(I)) continue;
                if (I0.contains(I)) continue;
                if (I.getWeight() >= minW) continue;

                if (avgWeightAfterUnion(X, I) < Constants.MIN_AVG_WEIGHT) continue;

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
