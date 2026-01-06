package spmf_integration;

import db.UncertainDatabase;
import entity.Itemset;
import miner.WPFI_Apriori;
import util.Constants;
import util.WPFI_Metrics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Tích hợp thuật toán Weighted Probabilistic Frequent Itemset (WPFI)
 * vào framework SPMF.
 *
 * - Nếu input là dataset gốc (SPMF/FIMI): tự sinh input.txt chuẩn (WEIGHTS +
 * DATA item:prob)
 * - Nếu input đã là input.txt chuẩn: đọc trực tiếp
 * - output: TSV theo đề tài: itemset, k, mu, pTail, avgW, score
 */
public class AlgoWPFI {

    private long startTime;
    private long endTime;
    private int patterns;
    private String generatedInputFile = null;

    // Default generation params (theo paper: mean=0.5 var=0.125)
    private static final long DEFAULT_SEED = 20260106L;
    private static final double DEFAULT_MEAN = 0.5;
    private static final double DEFAULT_VAR = 0.125;

    /**
     * SPMF calls this signature.
     * minsup: giữ để tương thích SPMF; hiện ngưỡng thật dùng Constants.MSUP
     * (final).
     */
    public void runAlgorithm(String input, String output, double minsup) throws IOException {
        startTime = System.currentTimeMillis();
        patterns = 0;
        generatedInputFile = null;

        Path inputPath = resolvePathSmart(input);
        if (!Files.exists(inputPath)) {
            throw new IOException("Không tìm thấy input: " + inputPath.toAbsolutePath().normalize());
        }

        // 1) Chuẩn hoá input -> input.txt chuẩn
        Path normalizedInput;
        if (looksLikeInputFile(inputPath)) {
            normalizedInput = inputPath;
        } else {
            // generate a normalized input next to original file
            normalizedInput = Paths.get(inputPath.toString() + ".input.txt");
            generateInputFileFromSPMF(inputPath, normalizedInput, Integer.MAX_VALUE, DEFAULT_SEED, DEFAULT_MEAN,
                    DEFAULT_VAR);
            generatedInputFile = normalizedInput.toAbsolutePath().normalize().toString();
        }

        // 2) Load DB từ input chuẩn
        UncertainDatabase db = new UncertainDatabase();
        db.loadFromInputFile(normalizedInput.toString());

        // 3) Run miner
        WPFI_Apriori miner = new WPFI_Apriori(db);
        Set<Itemset> results = miner.mine();
        patterns = results.size();

        // 4) Write output theo đề tài
        writeOutput(output, inputPath, normalizedInput, db, results, minsup);

        endTime = System.currentTimeMillis();
    }

    private void writeOutput(
            String output,
            Path originalInput,
            Path normalizedInput,
            UncertainDatabase db,
            Set<Itemset> results,
            double minsup) throws IOException {

        Path out = resolvePathSmart(output);

        // sort by score desc
        List<Itemset> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Double.compare(scoreOf(b, db), scoreOf(a, db)));

        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("===== WPFI OUTPUT =====");
            bw.newLine();
            bw.write("original_input=" + originalInput.toAbsolutePath().normalize());
            bw.newLine();
            bw.write("normalized_input=" + normalizedInput.toAbsolutePath().normalize());
            bw.newLine();
            if (generatedInputFile != null) {
                bw.write("generated_input_file=" + generatedInputFile);
                bw.newLine();
            }
            bw.write(
                    "SPMF_minsup_param=" + minsup + " (note: threshold used is Constants.MSUP=" + Constants.MSUP + ")");
            bw.newLine();
            bw.write("T=" + Constants.T);
            bw.newLine();
            bw.write("transactions=" + db.getTransactions().size());
            bw.newLine();
            bw.write("----------------------------------------------");
            bw.newLine();
            bw.write("Itemset\tk\tmu\tpTail\tavgW\tscore");
            bw.newLine();

            for (Itemset X : sorted) {
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
                double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                double avgW = X.avgWeight();
                double score = avgW * pTail;

                bw.write(X.toString());
                bw.write("\t");
                bw.write(String.valueOf(X.size()));
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", mu));
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", pTail));
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", avgW));
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", score));
                bw.newLine();
            }

            bw.write("----------------------------------------------");
            bw.newLine();
            bw.write("TOTAL_WPFI=" + results.size());
            bw.newLine();
        }
    }

    private double scoreOf(Itemset X, UncertainDatabase db) {
        double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
        double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
        return X.avgWeight() * pTail;
    }

    /**
     * Generate input file:
     * #WEIGHTS
     * item<TAB>weight
     * #DATA
     * item:prob item:prob ...
     *
     * Dataset input can be:
     * - "1 2 3"
     * - "1 2 3 -1" (ignore -1/-2)
     * - "1:0.7 2:0.9" (keep prob if provided)
     */
    private void generateInputFileFromSPMF(
            Path datasetPath,
            Path outInputPath,
            int maxTx,
            long seed,
            double meanProb,
            double varProb) throws IOException {

        Random rnd = new Random(seed);

        // collect unique items
        LinkedHashSet<String> uniqueItems = new LinkedHashSet<>();
        int count = 0;

        try (BufferedReader br = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (count >= maxTx)
                    break;
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] tokens = line.split("[,\\s]+");
                boolean hasAny = false;

                for (String tok : tokens) {
                    tok = tok.trim();
                    if (tok.isEmpty())
                        continue;

                    // ignore SPMF terminators
                    if (tok.equals("-1") || tok.equals("-2"))
                        continue;

                    String itemName = tok;
                    int colon = tok.lastIndexOf(':');
                    if (colon > 0)
                        itemName = tok.substring(0, colon).trim();

                    if (itemName.isEmpty())
                        continue;
                    uniqueItems.add(itemName);
                    hasAny = true;
                }

                if (hasAny)
                    count++;
            }
        }

        // assign weights w(i) ~ Uniform(0,1]
        Map<String, Double> weightMap = new LinkedHashMap<>();
        for (String it : uniqueItems) {
            double w = 0.0001 + rnd.nextDouble() * 0.9999;
            weightMap.put(it, w);
        }

        // write
        try (BufferedWriter bw = Files.newBufferedWriter(outInputPath, StandardCharsets.UTF_8)) {
            bw.write("#META dataset=" + datasetPath.toAbsolutePath().normalize());
            bw.newLine();
            bw.write("#META seed=" + seed + " meanProb=" + meanProb + " varProb=" + varProb + " maxTx="
                    + (maxTx == Integer.MAX_VALUE ? "ALL" : maxTx));
            bw.newLine();

            bw.write("#WEIGHTS");
            bw.newLine();
            for (Map.Entry<String, Double> e : weightMap.entrySet()) {
                bw.write(e.getKey());
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", e.getValue()));
                bw.newLine();
            }

            bw.write("#DATA");
            bw.newLine();

            int tx = 0;
            try (BufferedReader br = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (tx >= maxTx)
                        break;
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    String[] tokens = line.split("[,\\s]+");
                    List<String> outTokens = new ArrayList<>();

                    for (String tok : tokens) {
                        tok = tok.trim();
                        if (tok.isEmpty())
                            continue;
                        if (tok.equals("-1") || tok.equals("-2"))
                            continue;

                        String itemName = tok;
                        Double pOccur = null;

                        int colon = tok.lastIndexOf(':');
                        if (colon > 0 && colon < tok.length() - 1) {
                            itemName = tok.substring(0, colon).trim();
                            pOccur = tryParseDouble(tok.substring(colon + 1).trim(), null);
                        }

                        if (itemName.isEmpty())
                            continue;

                        double p = (pOccur != null) ? clip01(pOccur) : sampleGaussianClipped(rnd, meanProb, varProb);
                        outTokens.add(itemName + ":" + String.format(Locale.US, "%.6f", p));
                    }

                    if (!outTokens.isEmpty()) {
                        bw.write(String.join(" ", outTokens));
                        bw.newLine();
                        tx++;
                    }
                }
            }
        }
    }

    private boolean looksLikeInputFile(Path path) {
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 30; i++) {
                String line = br.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.equalsIgnoreCase("#WEIGHTS") || line.equalsIgnoreCase("#DATA"))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Path resolvePathSmart(String pathStr) {
        Path p = Paths.get(pathStr);
        if (p.isAbsolute())
            return p.normalize();
        return Paths.get("").toAbsolutePath().resolve(p).normalize();
    }

    private static double sampleGaussianClipped(Random rnd, double mean, double var) {
        double sd = Math.sqrt(Math.max(var, 0.0));
        double v = mean + rnd.nextGaussian() * sd;
        return clip01(v);
    }

    private static double clip01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v))
            return 1e-6;
        if (v <= 0.0)
            return 1e-6;
        if (v > 1.0)
            return 1.0;
        return v;
    }

    private static Double tryParseDouble(String s, Double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public void printStatistics() {
        System.out.println("===== AlgoWPFI Statistics =====");
        System.out.println("Runtime (ms): " + (endTime - startTime));
        System.out.println("Patterns: " + patterns);
        if (generatedInputFile != null) {
            System.out.println("Generated input file: " + generatedInputFile);
        }
        System.out.println("================================");
    }
}
