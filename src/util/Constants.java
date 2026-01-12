package util;

import java.util.Locale;

public class Constants {

    public static int MSUP = 2;
    public static double T = 0.10;
    public static double ALPHA = 0.60;
    public static double MIN_AVG_WEIGHT = 0.00;

    // ===== NEW: pruning mode + benchmark flag =====
    public enum PruneMode {
        NONE, // không cắt tỉa (baseline)
        WEIGHT_ONLY, // chỉ weight pruning
        MUHAT_ONLY, // chỉ μ^ pruning
        APPROXMU_ONLY, // chỉ approx-μ pruning
        ALL // bật cả 3 pruning
        , UBSCORE_ONLY
    }

    public static PruneMode PRUNE_MODE = PruneMode.ALL;

    /** Nếu true: MainApp sẽ chạy 3 mode và in bảng so sánh */
    public static boolean BENCH_PRUNE = false;

    /** Giới hạn k để chạy nhanh (rất nên dùng khi dataset lớn) */
    public static int KMAX = 2;

    public static void applyArgs(String[] args) {
        if (args == null)
            return;

        for (String a : args) {
            if (a == null)
                continue;
            a = a.trim();
            if (!a.startsWith("--"))
                continue;

            int eq = a.indexOf('=');
            if (eq < 0)
                continue;

            String key = a.substring(2, eq).trim().toLowerCase(Locale.ROOT);
            String val = a.substring(eq + 1).trim();

            try {
                switch (key) {
                    case "msup": {
                        int ms = Integer.parseInt(val);
                        if (ms < 0)
                            ms = 0;
                        MSUP = ms;
                        break;
                    }
                    case "t": {
                        double tt = Double.parseDouble(val);
                        if (tt < 0)
                            tt = 0;
                        T = tt;
                        break;
                    }
                    case "alpha": {
                        double al = Double.parseDouble(val);
                        if (al < 0)
                            al = 0;
                        ALPHA = al;
                        break;
                    }
                    case "minavgw": {
                        double mw = Double.parseDouble(val);
                        if (mw < 0)
                            mw = 0;
                        MIN_AVG_WEIGHT = mw;
                        break;
                    }
                    case "kmax": {
                        int km = Integer.parseInt(val);
                        if (km < 1)
                            km = 1;
                        KMAX = km;
                        break;
                    }
                    case "prune": {
                        // NONE | WEIGHT_ONLY | MUHAT_ONLY | APPROXMU_ONLY | ALL
                        String up = val.trim().toUpperCase(Locale.ROOT);
                        PRUNE_MODE = PruneMode.valueOf(up);
                        break;
                    }
                    case "benchprune": {
                        BENCH_PRUNE = Boolean.parseBoolean(val);
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static String describe() {
        return "MSUP=" + MSUP
                + " T=" + T
                + " ALPHA=" + ALPHA
                + " MIN_AVG_WEIGHT=" + MIN_AVG_WEIGHT
                + " KMAX=" + KMAX
                + " PRUNE_MODE=" + PRUNE_MODE
                + " BENCH_PRUNE=" + BENCH_PRUNE;
    }
}
