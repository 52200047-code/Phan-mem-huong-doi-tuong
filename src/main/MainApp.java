import db.UncertainDatabase;
import miner.WPFI_Apriori;
import util.Constants;

import java.io.File;

public class MainApp {

    public static void main(String[] args) {

        try {
            /*
             * Cú pháp chạy:
             * 1) Chạy 1 mode theo kiểu cũ:
             *    java -Xmx4g -cp bin MainApp [algo] [dataPath] [outputPath] [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
             *    algo:
             *      0 = NONE (baseline)
             *      1 = WEIGHT_ONLY
             *      2 = MUHAT_ONLY
             *      3 = APPROX_ONLY
             *      4 = ALL
             *      5 = FAST
             *
             * 2) Chạy experiment (chạy tất cả mode để so sánh):
             *    java -Xmx4g -cp bin MainApp exp [dataPath] [outputDir] [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
             *
             * Note: outputDir là thư mục, mỗi mode sẽ sinh 1 file riêng.
             */

            boolean isExperiment = (args.length >= 1 && args[0].equalsIgnoreCase("exp"));

            String dataPath;
            String outputBase; // file (single mode) hoặc folder (experiment)
            int argOffset;

            if (isExperiment) {
                // exp [dataPath] [outputDir] [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
                dataPath = (args.length >= 2) ? args[1] : "src/data/fruithut_original.txt";
                outputBase = (args.length >= 3) ? args[2] : "src/out/exp";
                argOffset = 3;
            } else {
                // [algo] [dataPath] [outputPath] [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
                dataPath = (args.length >= 2) ? args[1] : "src/data/fruithut_original.txt";
                int algo = (args.length >= 1) ? Integer.parseInt(args[0]) : 4;
                outputBase = (args.length >= 3) ? args[2] : ("src/out/result_algo" + algo + ".txt");
                argOffset = 3;

                // set mode theo algo
                WPFI_Apriori.PruningMode mode = mapAlgoToMode(algo);

                // set params
                applyParams(args, argOffset);

                // tạo folder output
                ensureParentFolder(outputBase);

                // load DB
                UncertainDatabase db = new UncertainDatabase();
                db.loadDatabase(dataPath);

                System.out.println("\n========== DATABASE LOADED ==========");
                System.out.println("Dataset : " + dataPath);
                System.out.println("Transactions : " + db.size());
                System.out.println("====================================\n");

                // run single mode
                System.out.println("Bat dau khai thac WPFI...");
                System.out.println("Mode: " + mode);
                System.out.println("Output: " + outputBase);
                System.out.println("MSUP=" + Constants.MSUP + " | T=" + Constants.T + " | ALPHA=" + Constants.ALPHA + " | MIN_W=" + Constants.MIN_AVG_WEIGHT + " | MAX_K=" + Constants.MAX_K);

                WPFI_Apriori miner = new WPFI_Apriori(db, mode);
                miner.mine(outputBase);

                // report
                WPFI_Apriori.MiningReport r = miner.getLastReport();
                System.out.println("\n[REPORT] " + mode);
                System.out.println("runtime_ms=" + r.runtimeMs + ", peak_mem_mb=" + r.peakMemoryMB + ", total_candidates=" + r.totalCandidates + ", total_patterns=" + r.totalPatterns);
                System.out.println("patterns_by_k=" + r.patternsByK);
                System.out.println("\nFINISHED");
                return;
            }

            // ========== EXPERIMENT MODE ==========
            // set params cho experiment
            applyParams(args, argOffset);

            // đảm bảo output dir tồn tại
            ensureDir(outputBase);

            // load DB
            UncertainDatabase db = new UncertainDatabase();
            db.loadDatabase(dataPath);

            System.out.println("\n========== DATABASE LOADED ==========");
            System.out.println("Dataset : " + dataPath);
            System.out.println("Transactions : " + db.size());
            System.out.println("OutputDir : " + outputBase);
            System.out.println("MSUP=" + Constants.MSUP + " | T=" + Constants.T + " | ALPHA=" + Constants.ALPHA + " | MIN_W=" + Constants.MIN_AVG_WEIGHT + " | MAX_K=" + Constants.MAX_K);
            System.out.println("====================================\n");

            WPFI_Apriori.PruningMode[] modes = new WPFI_Apriori.PruningMode[]{
                    WPFI_Apriori.PruningMode.NONE,
                    WPFI_Apriori.PruningMode.WEIGHT_ONLY,
                    WPFI_Apriori.PruningMode.MUHAT_ONLY,
                    WPFI_Apriori.PruningMode.APPROX_ONLY,
                    WPFI_Apriori.PruningMode.ALL,
                    WPFI_Apriori.PruningMode.FAST
            };

            System.out.println("mode,runtime_ms,peak_mem_mb,total_candidates,total_patterns,patterns_by_k,output_file");

            for (WPFI_Apriori.PruningMode m : modes) {
                String outFile = outputBase + File.separator + ("result_" + m.name() + ".txt");
                ensureParentFolder(outFile);

                System.out.println("[RUN] " + m + " -> " + outFile);

                WPFI_Apriori miner = new WPFI_Apriori(db, m);
                miner.mine(outFile);

                WPFI_Apriori.MiningReport r = miner.getLastReport();
                System.out.println(
                        m.name() + "," +
                                r.runtimeMs + "," +
                                r.peakMemoryMB + "," +
                                r.totalCandidates + "," +
                                r.totalPatterns + "," +
                                r.patternsByK.toString().replace(" ", "") + "," +
                                outFile.replace(",", "_")
                );
            }

            System.out.println("\nFINISHED EXPERIMENT");

        } catch (Exception e) {
            System.err.println("ERROR");
            e.printStackTrace();
        }
    }

    private static WPFI_Apriori.PruningMode mapAlgoToMode(int algo) {
        return switch (algo) {
            case 0 -> WPFI_Apriori.PruningMode.NONE;
            case 1 -> WPFI_Apriori.PruningMode.WEIGHT_ONLY;
            case 2 -> WPFI_Apriori.PruningMode.MUHAT_ONLY;
            case 3 -> WPFI_Apriori.PruningMode.APPROX_ONLY;
            case 5 -> WPFI_Apriori.PruningMode.FAST;
            default -> WPFI_Apriori.PruningMode.ALL; // 4 hoặc khác
        };
    }

    private static void applyParams(String[] args, int offset) {
        // [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
        // offset là vị trí bắt đầu của MSUP
        Constants.MSUP = (args.length >= offset + 1) ? Integer.parseInt(args[offset]) : Constants.MSUP;
        Constants.T = (args.length >= offset + 2) ? Double.parseDouble(args[offset + 1]) : Constants.T;
        Constants.ALPHA = (args.length >= offset + 3) ? Double.parseDouble(args[offset + 2]) : Constants.ALPHA;
        Constants.MIN_AVG_WEIGHT = (args.length >= offset + 4) ? Double.parseDouble(args[offset + 3]) : Constants.MIN_AVG_WEIGHT;
        // Constants.MAX_K giữ nguyên theo file Constants.java (bạn set 0 để không giới hạn)
    }

    private static void ensureParentFolder(String path) {
        File outFile = new File(path);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    private static void ensureDir(String dir) {
        File f = new File(dir);
        if (!f.exists()) f.mkdirs();
    }
}
