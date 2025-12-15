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
 *
 * Có hỗ trợ:
 *  - Ghi kết quả từng itemset ra file
 *  - Resume khi chạy lại (không ghi trùng)
 *  - Ctrl+C an toàn
 */
public class WPFI_Apriori {

    /* ================== RESUME SUPPORT ================== */
    private final Set<String> existedResults = new HashSet<>();
    private BufferedWriter resultWriter;
    private String outputPath;

    /* ================== CORE DATA ================== */
    private final UncertainDatabase db;

    public WPFI_Apriori(UncertainDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    /* ================== PUBLIC API ================== */

    public Set<Itemset> mine(String outputPath) {
        this.outputPath = outputPath;
        loadExistingResults();
        initWriter();

        Set<Itemset> all = new LinkedHashSet<>();

        /* ---------- 1️⃣ Thu thập vũ trụ item ---------- */
        SortedSet<Item> universe = collectUniverse(db);

        /* ---------- 2️⃣ Tính μ cho 1-itemset ---------- */
        Map<Item, Double> mu1 = new HashMap<>();
        for (Item i : universe) {
            mu1.put(i, WPFI_Metrics.computeMu(new Itemset(Set.of(i)), db.getTransactions()));
        }

        /* ---------- 3️⃣ Tính μ̂ ---------- */
        double maxW = universe.stream().mapToDouble(Item::getWeight).max().orElse(1.0);
        if (maxW <= 0) maxW = 1.0;

        final double muHat = WPFI_Metrics.solveMuHatPoisson(Constants.MSUP, Constants.T / maxW);
        final int n = db.size();

        /* ---------- 4️⃣ L1 ---------- */
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
                writeResult(X);
                all.add(X);
            }
        }

        if (Lprev.isEmpty()) {
            closeWriter();
            return all;
        }

        /* ---------- I0 ---------- */
        Set<Item> I0 = Lprev.stream()
                .flatMap(s -> s.getItems().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        /* ---------- 5️⃣ Apriori Loop ---------- */
        int k = 2;
        while (!Lprev.isEmpty()) {
            System.out.println("[INFO] Mining level k = " + k + ", |Lprev| = " + Lprev.size());

            Set<Itemset> Ck = generateCandidatesWithPruning(
                    Lprev, universe, I0, muMap, mu1, muHat, n
            );
            if (Ck.isEmpty()) break;

            Set<Itemset> Lk = new LinkedHashSet<>();
            for (Itemset X : Ck) {
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                muMap.put(X, mu);

                double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                double score = X.avgWeight() * pTail;

                if (score >= Constants.T) {
                    Lk.add(X);
                    writeResult(X);
                    all.add(X);
                }
            }

            if (Lk.isEmpty()) break;

            for (Itemset x : Lk) I0.addAll(x.getItems());
            Lprev = Lk;
            k++;
        }

        closeWriter();
        return all;
    }

    /* ================== RESUME METHODS ================== */

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
            resultWriter = new BufferedWriter(new FileWriter(outputPath, true)); // append
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeResult(Itemset X) {
        try {
            String key = X.toString();
            if (!existedResults.contains(key)) {
                resultWriter.write(key);
                resultWriter.newLine();
                resultWriter.flush(); // cực kỳ quan trọng
                existedResults.add(key);
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

    /* ================== HELPERS ================== */

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

            /* (A) item trong I0 */
            for (Item I : I0) {
                if (X.getItems().contains(I)) continue;
                if (avgWeightAfterUnion(X, I) < Constants.MIN_AVG_WEIGHT) continue;

                double muI = mu1.get(I);
                if (Math.min(muX, muI) < muHat) continue;
                if (Constants.ALPHA > 0 && (muX * muI) < (Constants.ALPHA * n * muHat)) continue;

                Ck.add(X.unionWith(I));
            }

            /* (B) item ngoài I0 */
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
