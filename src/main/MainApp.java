import db.UncertainDatabase;
import entity.Itemset;
import miner.WPFI_Apriori;
import util.Constants;
import util.WPFI_Metrics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import miner.WPFI_Apriori;
import miner.WPFI_Apriori.Stats;

/**
 * MainApp:
 * 1) Đọc dataset gốc (SPMF/FIMI style: mỗi dòng là 1 transaction, items cách
 * nhau bởi space/comma)
 * 2) Sinh weight table w(i) (cố định theo item)
 * 3) Sinh xác suất xuất hiện p(i|t) cho từng item trong từng transaction
 * 4) Ghi ra input.txt (WEIGHTS + DATA)
 * 5) Đọc lại input.txt -> chạy WPFI -> ghi output.txt
 *
 * args:
 * args[0] datasetPath (default .\data\chess.txt)
 * args[1] inputPath (default input.txt)
 * args[2] outputPath (default output.txt)
 * args[3] maxTx (default: read all)
 * args[4] seed (default: 20260106)
 * args[5] meanProb (default: 0.5)
 * args[6] varProb (default: 0.125)
 * args[7] useExactTail (default: false) // true = DP exact, false = Poisson
 * approx
 *
 * Run (PowerShell):
 * java -Dfile.encoding=UTF-8 -cp ".\main;." MainApp ".\data\chess.txt"
 * "input.txt" "output.txt" 10000 20260106 0.5 0.125 false
 */
public class MainApp {

    public static void main(String[] args) throws IOException {
        String datasetPath = getArg(args, 0, ".\\data\\chess.txt");
        String inputPath = getArg(args, 1, "input.txt");
        String outputPath = getArg(args, 2, "output.txt");

        int maxTx = tryParseInt(getArg(args, 3, null), Integer.MAX_VALUE);
        long seed = tryParseLong(getArg(args, 4, null), 20260106L);

        double meanProb = tryParseDouble(getArg(args, 5, null), 0.5);
        double varProb = tryParseDouble(getArg(args, 6, null), 0.125);

        boolean useExactTail = Boolean.parseBoolean(getArg(args, 7, "false"));
        Constants.applyArgs(args);
        // Nếu bật benchmark pruning: chạy nhiều mode và so sánh
        if (Constants.BENCH_PRUNE) {
            runPruningBenchmark(datasetPath, inputPath, outputPath, maxTx, seed, meanProb, varProb, useExactTail);
            return;
        }

        try {
            // A) Tạo input.txt (thể hiện rõ trọng số + xác suất)
            generateInputFile(datasetPath, inputPath, maxTx, seed, meanProb, varProb);

            // B) Đọc lại input.txt -> build UncertainDatabase (dùng loader chuẩn)
            UncertainDatabase db = new UncertainDatabase();
            db.loadFromInputFile(inputPath);

            // C) Mine WPFI
            WPFI_Apriori miner = new WPFI_Apriori(db);
            Set<Itemset> results = miner.mine();

            // D) Ghi output.txt
            writeOutputFile(outputPath, datasetPath, inputPath, db, results, useExactTail);

            // E) In nhanh ra console
            System.out.println("OK!");
            System.out.println("  dataset = " + resolvePathSmart(datasetPath).toAbsolutePath().normalize());
            System.out.println("  input   = " + resolvePathSmart(inputPath).toAbsolutePath().normalize());
            System.out.println("  output  = " + resolvePathSmart(outputPath).toAbsolutePath().normalize());
            System.out.println("  MSUP=" + Constants.MSUP + "  T=" + Constants.T + "  useExactTail=" + useExactTail);
            System.out.println("  #transactions = " + db.getTransactions().size());
            System.out.println("  #WPFI = " + results.size());

        } catch (Exception e) {
            System.err.println("Lỗi khi đọc/ghi/chạy miner: " + e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * input.txt format:
     * #META ...
     * #WEIGHTS
     * item<TAB>weight
     * ...
     * #DATA
     * item:prob item:prob ...
     */
    private static void generateInputFile(
            String datasetPathStr,
            String inputPathStr,
            int maxTx,
            long seed,
            double meanProb,
            double varProb) throws IOException {

        Path datasetPath = resolvePathSmart(datasetPathStr);
        if (!Files.exists(datasetPath)) {
            throw new IOException("Không tìm thấy dataset: " + datasetPath.toAbsolutePath().normalize());
        }

        Random rnd = new Random(seed);

        // Pass 1: collect unique items (up to maxTx lines)
        LinkedHashSet<String> uniqueItems = collectUniqueItems(datasetPath, maxTx);

        // Assign weights w(i) ~ Uniform(0,1], fixed per item
        Map<String, Double> weightMap = new LinkedHashMap<String, Double>();
        for (String it : uniqueItems) {
            double w = 0.0001 + rnd.nextDouble() * 0.9999; // (0,1]
            weightMap.put(it, w);
        }

        Path inputPath = resolvePathSmart(inputPathStr);

        // Pass 2: write file
        BufferedWriter bw = null;
        try {
            bw = Files.newBufferedWriter(inputPath, StandardCharsets.UTF_8);

            bw.write("#META dataset=" + datasetPath.toAbsolutePath().normalize());
            bw.newLine();
            bw.write("#META seed=" + seed + " meanProb=" + meanProb + " varProb=" + varProb
                    + " maxTx=" + (maxTx == Integer.MAX_VALUE ? "ALL" : maxTx));
            bw.newLine();

            // WEIGHTS
            bw.write("#WEIGHTS");
            bw.newLine();
            for (Map.Entry<String, Double> e : weightMap.entrySet()) {
                bw.write(e.getKey());
                bw.write("\t");
                bw.write(String.format(Locale.US, "%.6f", e.getValue()));
                bw.newLine();
            }

            // DATA
            bw.write("#DATA");
            bw.newLine();

            int count = 0;
            BufferedReader br = null;
            try {
                br = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8);
                String line;
                while ((line = br.readLine()) != null) {
                    if (count >= maxTx)
                        break;
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    String[] tokens = line.split("[,\\s]+");
                    List<String> outTokens = new ArrayList<String>();

                    for (String tok : tokens) {
                        if (tok == null)
                            continue;
                        tok = tok.trim();
                        if (tok.isEmpty())
                            continue;

                        // ignore SPMF terminators if any
                        if (tok.equals("-1") || tok.equals("-2"))
                            continue;

                        String itemName = tok;
                        Double pOccur = null;

                        // Nếu dataset đã là uncertain "item:prob" thì giữ prob đó
                        int colon = tok.lastIndexOf(':');
                        if (colon > 0 && colon < tok.length() - 1) {
                            itemName = tok.substring(0, colon).trim();
                            pOccur = tryParseDoubleObj(tok.substring(colon + 1).trim(), null);
                        }

                        if (itemName.isEmpty())
                            continue;

                        double p = (pOccur != null) ? clip01(pOccur.doubleValue())
                                : sampleGaussianClipped(rnd, meanProb, varProb);

                        outTokens.add(itemName + ":" + String.format(Locale.US, "%.6f", p));
                    }

                    if (!outTokens.isEmpty()) {
                        bw.write(joinBySpace(outTokens));
                        bw.newLine();
                        count++;
                    }
                }
            } finally {
                if (br != null)
                    br.close();
            }

        } finally {
            if (bw != null)
                bw.close();
        }
    }

    private static LinkedHashSet<String> collectUniqueItems(Path datasetPath, int maxTx) throws IOException {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        int count = 0;

        BufferedReader br = null;
        try {
            br = Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8);
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
                    if (tok == null)
                        continue;
                    tok = tok.trim();
                    if (tok.isEmpty())
                        continue;

                    if (tok.equals("-1") || tok.equals("-2"))
                        continue;

                    int colon = tok.lastIndexOf(':');
                    String itemName = (colon > 0) ? tok.substring(0, colon).trim() : tok;

                    if (!itemName.isEmpty()) {
                        set.add(itemName);
                        hasAny = true;
                    }
                }

                if (hasAny)
                    count++;
            }
        } finally {
            if (br != null)
                br.close();
        }

        return set;
    }

    private static void writeOutputFile(
            String outputPathStr,
            String datasetPath,
            String inputPath,
            UncertainDatabase db,
            Set<Itemset> results,
            boolean useExactTail) throws IOException {

        Path out = resolvePathSmart(outputPathStr);

        // sort by score desc
        List<Itemset> sorted = new ArrayList<Itemset>(results);
        Collections.sort(sorted, new Comparator<Itemset>() {
            @Override
            public int compare(Itemset a, Itemset b) {
                double sa = scoreOf(a, db, useExactTail);
                double sb = scoreOf(b, db, useExactTail);
                return Double.compare(sb, sa);
            }
        });

        BufferedWriter bw = null;
        try {
            bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8);

            bw.write("===== WPFI OUTPUT =====");
            bw.newLine();
            bw.write("dataset = " + resolvePathSmart(datasetPath).toAbsolutePath().normalize());
            bw.newLine();
            bw.write("input   = " + resolvePathSmart(inputPath).toAbsolutePath().normalize());
            bw.newLine();
            bw.write("MSUP=" + Constants.MSUP + "  T=" + Constants.T + "  useExactTail=" + useExactTail);
            bw.newLine();
            bw.write("transactions=" + db.getTransactions().size());
            bw.newLine();
            bw.write("----------------------------------------------");
            bw.newLine();
            bw.write("Itemset\tk\tmu\tpTail\tavgW\tscore");
            bw.newLine();

            for (Itemset X : sorted) {
                double mu = WPFI_Metrics.computeMu(X, db.getTransactions());

                double pTail;
                if (useExactTail) {
                    double[] probs = WPFI_Metrics.probsPerTransaction(X, db.getTransactions());
                    pTail = WPFI_Metrics.dpTailAtLeast(Constants.MSUP, probs);
                } else {
                    pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
                }

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

        } finally {
            if (bw != null)
                bw.close();
        }
    }

    private static double scoreOf(Itemset X, UncertainDatabase db, boolean useExactTail) {
        double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
        double pTail;
        if (useExactTail) {
            double[] probs = WPFI_Metrics.probsPerTransaction(X, db.getTransactions());
            pTail = WPFI_Metrics.dpTailAtLeast(Constants.MSUP, probs);
        } else {
            pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
        }
        return X.avgWeight() * pTail;
    }

    // -------- utils --------

    private static String joinBySpace(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
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

    private static String getArg(String[] args, int idx, String def) {
        if (args == null)
            return def;
        if (idx < 0 || idx >= args.length)
            return def;
        String s = args[idx];
        if (s == null)
            return def;
        s = s.trim();
        return s.isEmpty() ? def : s;
    }

    private static int tryParseInt(String s, int def) {
        if (s == null)
            return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long tryParseLong(String s, long def) {
        if (s == null)
            return def;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double tryParseDouble(String s, double def) {
        if (s == null)
            return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static Double tryParseDoubleObj(String s, Double def) {
        if (s == null)
            return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void runPruningBenchmark(
            String datasetPath,
            String inputPath,
            String outputPath,
            int maxTx,
            long seed,
            double meanProb,
            double varProb,
            boolean useExactTail) throws IOException {

        // 1) generate input.txt 1 lần (dùng chung)
        System.out.println("=== BENCH: generating input once ===");
        generateInputFile(datasetPath, inputPath, maxTx, seed, meanProb, varProb);

        // 2) load DB 1 lần (dùng chung)
        UncertainDatabase db = new UncertainDatabase();
        db.loadFromInputFile(inputPath);

        // 3) Chạy các mode cần so sánh
        Constants.PruneMode[] modes = new Constants.PruneMode[] {
                Constants.PruneMode.WEIGHT_ONLY,
                Constants.PruneMode.MUHAT_ONLY,
                Constants.PruneMode.APPROXMU_ONLY,
                Constants.PruneMode.UBSCORE_ONLY,
                Constants.PruneMode.ALL
        };

        List<BenchRow> rows = new ArrayList<>();

        for (Constants.PruneMode mode : modes) {
            Constants.PRUNE_MODE = mode;

            System.out.println();
            System.out.println("=== RUN MODE: " + mode + " | " + Constants.describe() + " ===");

            WPFI_Apriori miner = new WPFI_Apriori(db);
            Set<Itemset> results = miner.mine();

            Stats st = miner.getStats();

            // output file theo mode
            String outMode = appendModeToFileName(outputPath, mode.name().toLowerCase(Locale.ROOT));
            writeOutputFile(outMode, datasetPath, inputPath, db, results, useExactTail);

            rows.add(new BenchRow(mode, st, results.size(), outMode));
        }

        // 4) in bảng và ghi file comparison
        printComparison(rows);
        writeComparisonFile("pruning_comparison.txt", datasetPath, inputPath, rows);
    }

    private static class BenchRow {
        Constants.PruneMode mode;
        long runtimeMs;
        long peakMemMB;
        long expansions;
        long candidatesKept;
        int patterns;
        Map<Integer, Integer> patternsByK;
        String outputFile;

        BenchRow(Constants.PruneMode mode, Stats st, int patterns, String outputFile) {
            this.mode = mode;
            this.runtimeMs = st.runtimeMs;
            this.peakMemMB = st.peakMemMB;
            this.expansions = st.expansions;
            this.candidatesKept = st.candidatesKept;
            this.patterns = patterns;
            this.patternsByK = new LinkedHashMap<>(st.patternsByK);
            this.outputFile = outputFile;
        }
    }

    private static void printComparison(List<BenchRow> rows) {
        System.out.println();
        System.out.println("========== PRUNING COMPARISON ==========");
        System.out.printf("%-14s | %10s | %10s | %12s | %14s | %10s | %s%n",
                "MODE", "Runtime(ms)", "PeakMemMB", "Expansions", "CandidatesKept", "Patterns", "PatternsByK");
        System.out.println(
                "----------------------------------------------------------------------------------------------");

        for (BenchRow r : rows) {
            System.out.printf("%-14s | %10d | %10d | %12d | %14d | %10d | %s%n",
                    r.mode.name(),
                    r.runtimeMs,
                    r.peakMemMB,
                    r.expansions,
                    r.candidatesKept,
                    r.patterns,
                    r.patternsByK.toString());
            System.out.println("  -> output: " + r.outputFile);
        }

        System.out.println("========================================");
    }

    private static void writeComparisonFile(
            String fileName,
            String datasetPath,
            String inputPath,
            List<BenchRow> rows) throws IOException {
        Path out = resolvePathSmart(fileName);

        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("===== PRUNING COMPARISON =====");
            bw.newLine();
            bw.write("dataset=" + resolvePathSmart(datasetPath).toAbsolutePath().normalize());
            bw.newLine();
            bw.write("input=" + resolvePathSmart(inputPath).toAbsolutePath().normalize());
            bw.newLine();
            bw.write("config=" + Constants.describe());
            bw.newLine();
            bw.write("------------------------------------------------------------");
            bw.newLine();

            bw.write("MODE\tRuntimeMs\tPeakMemMB\tExpansions\tCandidatesKept\tPatterns\tPatternsByK\tOutputFile");
            bw.newLine();

            for (BenchRow r : rows) {
                bw.write(r.mode.name());
                bw.write("\t");
                bw.write(String.valueOf(r.runtimeMs));
                bw.write("\t");
                bw.write(String.valueOf(r.peakMemMB));
                bw.write("\t");
                bw.write(String.valueOf(r.expansions));
                bw.write("\t");
                bw.write(String.valueOf(r.candidatesKept));
                bw.write("\t");
                bw.write(String.valueOf(r.patterns));
                bw.write("\t");
                bw.write(r.patternsByK.toString());
                bw.write("\t");
                bw.write(r.outputFile);
                bw.newLine();
            }
        }

        System.out.println("Wrote comparison: " + out.toAbsolutePath().normalize());
    }

    private static String appendModeToFileName(String outputPath, String mode) {
        // output.txt -> output_weight_only.txt
        int dot = outputPath.lastIndexOf('.');
        if (dot <= 0)
            return outputPath + "_" + mode + ".txt";
        return outputPath.substring(0, dot) + "_" + mode + outputPath.substring(dot);
    }

}
