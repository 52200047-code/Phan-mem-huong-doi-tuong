package miner;

import db.UncertainDatabase;
import entity.Item;
import entity.Itemset;
import entity.Transaction;
import util.Constants;
import util.WPFI_Metrics;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Thuật toán Weighted Probabilistic Frequent Itemset (WPFI) – mở rộng Apriori.
 * Giới hạn độ dài itemset tối đa bằng Constants.MAX_K:
 *   - MAX_K > 0: giới hạn
 *   - MAX_K <= 0: chạy đến khi hết pattern
 */
public class WPFI_Apriori {

    public enum PruningMode {
        NONE,           // baseline (không cắt tỉa)
        WEIGHT_ONLY,
        MUHAT_ONLY,
        APPROX_ONLY,
        ALL,            // WPFI ban đầu: weight + muhat + approx
        FAST            // ALL + tối ưu lossless (UB-score + TID-index)
    }

    public static class MiningReport {
        public long runtimeMs;
        public long peakMemoryMB;
        public int totalPatterns;
        public long totalCandidates;
        public final Map<Integer, Integer> candidatesByK = new LinkedHashMap<>();
        public final Map<Integer, Integer> patternsByK = new LinkedHashMap<>();

        @Override
        public String toString() {
            return "MiningReport{" +
                    "runtimeMs=" + runtimeMs +
                    ", peakMemoryMB=" + peakMemoryMB +
                    ", totalPatterns=" + totalPatterns +
                    ", totalCandidates=" + totalCandidates +
                    ", candidatesByK=" + candidatesByK +
                    ", patternsByK=" + patternsByK +
                    '}';
        }
    }

    /* RESUME SUPPORT */
    private final Set<String> existedResults = new HashSet<>();
    private BufferedWriter resultWriter;
    private String outputPath;

    /* CORE DATA */
    private final UncertainDatabase db;
    private final PruningMode pruningMode;

    // report cho experiment
    private MiningReport lastReport = new MiningReport();
    public MiningReport getLastReport() { return lastReport; }

    public WPFI_Apriori(UncertainDatabase db) {
        this(db, PruningMode.ALL);
    }

    public WPFI_Apriori(UncertainDatabase db, PruningMode mode) {
        this.db = Objects.requireNonNull(db);
        this.pruningMode = (mode == null) ? PruningMode.ALL : mode;
    }

    public Set<Itemset> mine(String outputPath) {
        // reset report
        lastReport = new MiningReport();

        long startNs = System.nanoTime();
        Runtime rt = Runtime.getRuntime();
        long peakMemBytes = 0;

        this.outputPath = outputPath;
        loadExistingResults();
        initWriter();

        // Lưu ý: nếu output rất lớn, giữ "all" sẽ tốn RAM.
        // Ở mode FAST, mình không add vào "all" để tiết kiệm bộ nhớ.
        Set<Itemset> all = new LinkedHashSet<>();

        /* 1) Thu thập item */
        SortedSet<Item> universe = collectUniverse(db);

        /* 2) Tính μ cho 1-itemset (1 pass qua DB) */
        Map<Item, Double> mu1 = new HashMap<>();
        for (Transaction t : db.getTransactions()) {
            for (Item i : t.getItems()) {
                mu1.merge(i, t.getProb(i), Double::sum);
            }
        }

        /* 3) Tính μ̂  */
        double maxW = universe.stream().mapToDouble(Item::getWeight).max().orElse(1.0);
        if (maxW <= 0) maxW = 1.0;

        final double muHat = WPFI_Metrics.solveMuHatPoisson(Constants.MSUP, Constants.T / maxW);
        final int n = db.size();

        // FAST: build TID-index để computeMu nhanh (lossless)
        TidIndex tidIndex = null;
        if (pruningMode == PruningMode.FAST) {
            tidIndex = TidIndex.build(db);
        }

        /* 4) L1 */
        Set<Itemset> Lprev = new LinkedHashSet<>();
        Map<Itemset, Double> muMap = new HashMap<>();

        int totalPatterns = 0;

        for (Item i : universe) {
            Itemset X = new Itemset(Set.of(i));
            double mu = mu1.getOrDefault(i, 0.0);
            muMap.put(X, mu);

            double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
            double score = X.avgWeight() * pTail;

            if (score >= Constants.T) {
                Lprev.add(X);
                writeResult(X);

                totalPatterns++;
                if (pruningMode != PruningMode.FAST) all.add(X);
            }
        }

        lastReport.patternsByK.put(1, Lprev.size());

        if (Lprev.isEmpty()) {
            closeWriter();
            lastReport.totalPatterns = totalPatterns;
            lastReport.runtimeMs = (System.nanoTime() - startNs) / 1_000_000;
            lastReport.peakMemoryMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            return all;
        }

        /* I0 */
        Set<Item> I0 = Lprev.stream()
                .flatMap(s -> s.getItems().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        /* 5) Apriori Loop */
        int k = 2;
        while (!Lprev.isEmpty()) {

            // GIỚI HẠN K: MAX_K <= 0 nghĩa là KHÔNG GIỚI HẠN
            int MAX_K = Constants.MAX_K;
            if (MAX_K > 0 && k > MAX_K) {
                System.out.println("[INFO] Stop: reached MAX_K = " + MAX_K);
                break;
            }

            System.out.println("[INFO] Mining level k = " + k + ", |Lprev| = " + Lprev.size());

            Set<Itemset> Ck = generateCandidatesWithPruning(
                    Lprev, universe, I0, muMap, mu1, muHat, n, pruningMode, maxW
            );
            lastReport.candidatesByK.put(k, Ck.size());
            lastReport.totalCandidates += Ck.size();

            if (Ck.isEmpty()) break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                double mu;
                if (pruningMode == PruningMode.FAST && tidIndex != null) {
                    mu = tidIndex.computeMu(X); // nhanh hơn, lossless
                } else {
                    mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                }
                muMap.put(X, mu);

                double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                double score = X.avgWeight() * pTail;

                if (score >= Constants.T) {
                    Lk.add(X);
                    writeResult(X);

                    totalPatterns++;
                    if (pruningMode != PruningMode.FAST) all.add(X);
                }
            }

            lastReport.patternsByK.put(k, Lk.size());

            // peak memory (ước lượng)
            long usedBytes = rt.totalMemory() - rt.freeMemory();
            if (usedBytes > peakMemBytes) peakMemBytes = usedBytes;

            if (Lk.isEmpty()) break;

            for (Itemset x : Lk) I0.addAll(x.getItems());
            Lprev = Lk;
            k++;
        }

        closeWriter();

        lastReport.totalPatterns = totalPatterns;
        lastReport.runtimeMs = (System.nanoTime() - startNs) / 1_000_000;
        lastReport.peakMemoryMB = peakMemBytes / (1024 * 1024);

        return all;
    }

    /* RESUME METHODS */

    private void loadExistingResults() {
        File file = new File(outputPath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                existedResults.add(line.trim());
            }
            System.out.println("[INFO] Loaded " + existedResults.size() + " existing itemsets.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initWriter() {
        try {
            resultWriter = new BufferedWriter(new FileWriter(outputPath, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long writeCount = 0;

    private void writeResult(Itemset X) {
        try {
            String key = X.toString();
            if (!existedResults.contains(key)) {
                resultWriter.write(key);
                resultWriter.newLine();
                existedResults.add(key);

                writeCount++;
                if (writeCount % 5000 == 0) resultWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeWriter() {
        try {
            if (resultWriter != null) resultWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
            int n,
            PruningMode mode,
            double maxW
    ) {
        Set<Itemset> Ck = new LinkedHashSet<>();

        final boolean useWeight = (mode == PruningMode.WEIGHT_ONLY || mode == PruningMode.ALL || mode == PruningMode.FAST);
        final boolean useMuHat  = (mode == PruningMode.MUHAT_ONLY  || mode == PruningMode.ALL || mode == PruningMode.FAST);
        final boolean useApprox = (mode == PruningMode.APPROX_ONLY || mode == PruningMode.ALL || mode == PruningMode.FAST);
        final boolean useUbBranch = (mode == PruningMode.FAST);

        for (Itemset X : Lprev) {
            double muX = muMap.getOrDefault(X, 0.0);

            // UB-score branch pruning (LOSSLESS) - chỉ bật ở FAST
            if (useUbBranch) {
                double ubTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, muX);
                double ubScore = maxW * ubTail;
                if (ubScore < Constants.T) continue; // prune cả nhánh mở rộng của X
            }

            double minW = X.minItemWeight();

            /* (A) item trong I0 */
            for (Item I : I0) {
                if (X.getItems().contains(I)) continue;

                if (useWeight) {
                    if (avgWeightAfterUnion(X, I) < Constants.MIN_AVG_WEIGHT) continue;
                }

                double muI = mu1.getOrDefault(I, 0.0);

                if (useMuHat) {
                    if (Math.min(muX, muI) < muHat) continue;
                }

                if (useApprox) {
                    if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;
                }

                Ck.add(X.unionWith(I));
            }

            /* (B) item ngoài I0 */
            for (Item I : universe) {
                if (X.getItems().contains(I)) continue;
                if (I0.contains(I)) continue;

                if (useWeight) {
                    if (I.getWeight() >= minW) continue;
                    if (avgWeightAfterUnion(X, I) < Constants.MIN_AVG_WEIGHT) continue;
                }

                double muI = mu1.getOrDefault(I, 0.0);

                if (useMuHat) {
                    if (Math.min(muX, muI) < muHat) continue;
                }

                if (useApprox) {
                    if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;
                }

                Ck.add(X.unionWith(I));
            }
        }
        return Ck;
    }

    private static double avgWeightAfterUnion(Itemset X, Item I) {
        double sum = X.avgWeight() * X.size() + I.getWeight();
        return sum / (X.size() + 1);
    }

    /* FAST: TID INDEX (LOSSLESS) */

    static class TidIndex {
        final Map<Item, int[]> tids = new HashMap<>();
        final Map<Item, double[]> probs = new HashMap<>();

        static TidIndex build(UncertainDatabase db) {
            TidIndex idx = new TidIndex();

            Map<Item, ArrayList<Integer>> tidList = new HashMap<>();
            Map<Item, ArrayList<Double>>  pList  = new HashMap<>();

            int tid = 0;
            for (Transaction t : db.getTransactions()) {
                for (Item i : t.getItems()) {
                    tidList.computeIfAbsent(i, k -> new ArrayList<>()).add(tid);
                    pList.computeIfAbsent(i, k -> new ArrayList<>()).add(t.getProb(i));
                }
                tid++;
            }

            for (Item i : tidList.keySet()) {
                ArrayList<Integer> tl = tidList.get(i);
                ArrayList<Double> pl = pList.get(i);

                int[] tarr = new int[tl.size()];
                double[] parr = new double[pl.size()];
                for (int j = 0; j < tl.size(); j++) {
                    tarr[j] = tl.get(j);
                    parr[j] = pl.get(j);
                }
                idx.tids.put(i, tarr);
                idx.probs.put(i, parr);
            }
            return idx;
        }

        double computeMu(Itemset X) {
            List<Item> items = new ArrayList<>(X.getItems());
            if (items.isEmpty()) return 0.0;
            Collections.sort(items);

            // base item = item có TID-list ngắn nhất
            Item base = items.get(0);
            int minLen = getLen(base);
            for (Item it : items) {
                int len = getLen(it);
                if (len < minLen) { minLen = len; base = it; }
            }

            int[] baseTids = tids.getOrDefault(base, new int[0]);
            double[] basePs = probs.getOrDefault(base, new double[0]);

            double mu = 0.0;
            for (int i = 0; i < baseTids.length; i++) {
                int tid = baseTids[i];
                double prod = basePs[i];

                boolean ok = true;
                for (Item it : items) {
                    if (it.equals(base)) continue;
                    int[] tlist = tids.getOrDefault(it, new int[0]);
                    int pos = Arrays.binarySearch(tlist, tid);
                    if (pos < 0) { ok = false; break; }
                    prod *= probs.get(it)[pos];
                }
                if (ok) mu += prod;
            }
            return mu;
        }

        private int getLen(Item it) {
            return tids.getOrDefault(it, new int[0]).length;
        }
    }
}