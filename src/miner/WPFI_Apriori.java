package miner;

import db.UncertainDatabase;
import entity.Item;
import entity.Itemset;
import entity.Transaction;
import util.Constants;
import util.WPFI_Metrics;

import java.util.*;
import java.util.stream.Collectors;

public class WPFI_Apriori {

    // false = Poisson approx (nhanh) ; true = DP exact (chậm hơn)
    private static final boolean USE_EXACT_TAIL = false;

    private final UncertainDatabase db;

    // ====== Stats for comparison ======
    public static class Stats {
        public long runtimeMs;
        public long peakMemMB;

        public long expansions; // số lần thử mở rộng (X + i)
        public long candidatesKept; // số ứng viên pass pruning được giữ lại
        public int patterns; // số itemset đạt score >= T
        public final Map<Integer, Integer> patternsByK = new LinkedHashMap<>();

        public void addPattern(Itemset x) {
            int k = x.size();
            patternsByK.put(k, patternsByK.getOrDefault(k, 0) + 1);
        }
    }

    private final Stats stats = new Stats();

    public WPFI_Apriori(UncertainDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    public Stats getStats() {
        return stats;
    }

    public Set<Itemset> mine() {
        long t0 = System.currentTimeMillis();

        SortedSet<Item> universe = collectUniverse(db);

        // precompute mu for 1-items
        Map<Item, Double> mu1 = new HashMap<>();
        for (Item i : universe) {
            Itemset one = new Itemset(Collections.singleton(i));
            mu1.put(i, WPFI_Metrics.computeMu(one, db.getTransactions()));
        }

        // compute muHat
        double maxW = universe.stream().mapToDouble(Item::getWeight).max().orElse(1.0);
        if (maxW <= 0)
            maxW = 1.0;
        final double muHat = WPFI_Metrics.solveMuHatPoisson(Constants.MSUP, Constants.T / maxW);
        final int n = db.size();

        // pruning strategies
        WeightPruning weightPruning = new WeightPruning(false);
        MuHatPruning muHatPruning = new MuHatPruning(muHat, false);
        ApproxMuPruning approxMuPruning = new ApproxMuPruning(Constants.ALPHA, n, muHat, false);
        UBScorePruning ubScorePruning = new UBScorePruning(Constants.MSUP, Constants.T, false);

        // L1
        Set<Itemset> Lprev = new LinkedHashSet<>();
        Map<Itemset, Double> muMap = new HashMap<>();

        for (Item i : universe) {
            Itemset X = new Itemset(Collections.singleton(i));
            double mu = mu1.getOrDefault(i, 0.0);
            muMap.put(X, mu);

            double pTail = tailAtLeast(Constants.MSUP, X, mu);
            double score = X.avgWeight() * pTail;

            if (score >= Constants.T) {
                Lprev.add(X);
            }
        }

        Set<Itemset> all = new LinkedHashSet<>(Lprev);
        for (Itemset x : Lprev)
            stats.addPattern(x);

        if (Lprev.isEmpty()) {
            finishStats(t0);
            stats.patterns = 0;
            return all;
        }

        // I0
        Set<Item> I0 = Lprev.stream()
                .flatMap(s -> s.getItems().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // iterate k
        int k = 1; // current Lprev is k=1
        while (!Lprev.isEmpty() && k < Constants.KMAX) {

            Set<Itemset> Ck = generateCandidates(
                    Lprev, universe, I0,
                    muMap, mu1,
                    weightPruning, muHatPruning, ubScorePruning, approxMuPruning);

            if (Ck.isEmpty())
                break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                muMap.put(X, mu);

                double pTail = tailAtLeast(Constants.MSUP, X, mu);
                double score = X.avgWeight() * pTail;

                if (score >= Constants.T) {
                    Lk.add(X);
                }
            }

            if (Lk.isEmpty())
                break;

            all.addAll(Lk);
            for (Itemset x : Lk)
                stats.addPattern(x);

            for (Itemset x : Lk)
                I0.addAll(x.getItems());
            Lprev = Lk;
            k++;
        }

        stats.patterns = all.size();
        finishStats(t0);
        return all;
    }

    private void finishStats(long t0) {
        stats.runtimeMs = System.currentTimeMillis() - t0;
        stats.peakMemMB = getUsedMemMB();
    }

    private static long getUsedMemMB() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        return used / (1024L * 1024L);
    }

    /*
     * ------------------ Candidate generation with pruning switches
     * ------------------
     */

    private Set<Itemset> generateCandidates(
            Set<Itemset> Lprev,
            SortedSet<Item> universe,
            Set<Item> I0,
            Map<Itemset, Double> muMap,
            Map<Item, Double> mu1,
            WeightPruning weightPruning,
            MuHatPruning muHatPruning,
            UBScorePruning ubScorePruning,

            ApproxMuPruning approxMuPruning) {
        Set<Itemset> Ck = new LinkedHashSet<>();

        for (Itemset X : Lprev) {
            double muX = muMap.getOrDefault(X, 0.0);

            // (A) extend with items in I0: isFromI0=true
            for (Item I : I0) {
                if (X.getItems().contains(I))
                    continue;

                stats.expansions++;

                double muI = mu1.getOrDefault(I, 0.0);

                if (shouldPrune(X, I, muX, muI, true, weightPruning, muHatPruning, approxMuPruning, ubScorePruning)) {
                    continue;
                }

                Ck.add(X.unionWith(I));
                stats.candidatesKept++;
            }

            // (B) extend with items outside I0: isFromI0=false (Corollary 1 active)
            for (Item I : universe) {
                if (X.getItems().contains(I))
                    continue;
                if (I0.contains(I))
                    continue;

                stats.expansions++;

                double muI = mu1.getOrDefault(I, 0.0);

                if (shouldPrune(X, I, muX, muI, false, weightPruning, muHatPruning, approxMuPruning, ubScorePruning)) {
                    continue;
                }

                Ck.add(X.unionWith(I));
                stats.candidatesKept++;
            }
        }

        return Ck;
    }

    private boolean shouldPrune(
            Itemset X, Item I, double muX, double muI, boolean isFromI0,
            WeightPruning weightPruning,
            MuHatPruning muHatPruning,
            ApproxMuPruning approxMuPruning,
            UBScorePruning ubScorePruning) {
        Constants.PruneMode mode = Constants.PRUNE_MODE;

        // NONE: không prune gì (nhưng vẫn nên lọc MIN_AVG_WEIGHT để tránh nổ cực mạnh?)
        if (mode == Constants.PruneMode.NONE) {
            // vẫn cho chạy đúng "none": không prune
            return false;
        }
        if (mode == Constants.PruneMode.UBSCORE_ONLY) {
            return ubScorePruning.shouldPrune(X, I, muX, muI, isFromI0);
        }

        // WEIGHT_ONLY
        if (mode == Constants.PruneMode.WEIGHT_ONLY) {
            return weightPruning.shouldPrune(X, I, muX, muI, isFromI0);
        }

        // MUHAT_ONLY
        if (mode == Constants.PruneMode.MUHAT_ONLY) {
            return muHatPruning.shouldPrune(X, I, muX, muI, isFromI0);
        }

        // APPROXMU_ONLY
        if (mode == Constants.PruneMode.APPROXMU_ONLY) {
            return approxMuPruning.shouldPrune(X, I, muX, muI, isFromI0);
        }

        // ALL: áp cả 3
        if (weightPruning.shouldPrune(X, I, muX, muI, isFromI0))
            return true;
        if (muHatPruning.shouldPrune(X, I, muX, muI, isFromI0))
            return true;
        if (approxMuPruning.shouldPrune(X, I, muX, muI, isFromI0))
            return true;
        if (ubScorePruning.shouldPrune(X, I, muX, muI, isFromI0))
            return true;

        return false;
    }

    /* ------------------ Tail helper ------------------ */

    private double tailAtLeast(int msup, Itemset X, double mu) {
        if (!USE_EXACT_TAIL) {
            return WPFI_Metrics.poissonTailAtLeast(msup, mu);
        }
        double[] probs = WPFI_Metrics.probsPerTransaction(X, db.getTransactions());
        return WPFI_Metrics.dpTailAtLeast(msup, probs);
    }

    /* ------------------ Universe helper ------------------ */

    private static SortedSet<Item> collectUniverse(UncertainDatabase db) {
        SortedSet<Item> set = new TreeSet<>();
        for (Transaction t : db.getTransactions()) {
            set.addAll(t.getItems());
        }
        return set;
    }
}
