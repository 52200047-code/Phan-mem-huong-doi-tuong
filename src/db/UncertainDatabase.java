package db;

import entity.Item;
import entity.Transaction;

import java.io.*;
import java.util.*;

/**
 * UncertainDatabase:
 * - 2-pass để gán p (uncertainty) và w (weight) có quy luật dựa trên tần suất item
 * - Có seed để kết quả reproducible
 * - Hỗ trợ token dạng: "23" hoặc "23(0.65)"
 */
public class UncertainDatabase {

    private final List<Transaction> transactions = new ArrayList<>();

    // Cấu hình gán p/w
    private static final long SEED = 42L;
    private static final double NOISE = 0.08; // biên độ nhiễu cho p (0.0 -> 0.15)

    // p sẽ được clamp vào [P_MIN, P_MAX]
    private static final double P_MIN = 0.05;
    private static final double P_MAX = 0.99;

    // w sẽ nằm trong [W_MIN, W_MAX]
    private static final double W_MIN = 1.0;
    private static final double W_MAX = 10.0;

    /** Thêm transaction */
    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    /** Lấy danh sách transaction */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    /** Số lượng transaction */
    public int size() {
        return transactions.size();
    }

    public void loadDatabase(String dataPath) throws IOException {
        transactions.clear();

        File f = new File(dataPath);
        if (!f.exists()) throw new FileNotFoundException("Không tìm thấy file: " + dataPath);

        // PASS 1: Đếm freq(item) theo số transaction chứa item
        Map<String, Integer> freq = new HashMap<>();
        int nTransactions = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!isDataLine(line)) continue;

                String[] tokens = line.split("\\s+");
                // dùng set để đảm bảo mỗi item chỉ tính 1 lần/transaction
                Set<String> seen = new HashSet<>();

                for (String token : tokens) {
                    token = token.trim();
                    if (token.isEmpty()) continue;

                    ParsedToken pt = parseToken(token);
                    if (pt.itemId.isEmpty()) continue;

                    seen.add(pt.itemId);
                }

                for (String id : seen) {
                    freq.merge(id, 1, Integer::sum);
                }

                nTransactions++;
            }
        }

        if (nTransactions == 0) return;

        // maxFreq để chuẩn hoá
        int maxFreq = freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        // Tính weight map: w(i) = W_MIN + (W_MAX-W_MIN)*log(1+freq)/log(1+maxFreq)
        Map<String, Double> weightMap = new HashMap<>();
        double denom = Math.log(1.0 + maxFreq);
        if (denom <= 0) denom = 1.0;

        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            int fi = e.getValue();
            double w = W_MIN + (W_MAX - W_MIN) * (Math.log(1.0 + fi) / denom);
            weightMap.put(e.getKey(), w);
        }

        // PASS 2: Tạo transactions + gán p,w
        Random rng = new Random(SEED);

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!isDataLine(line)) continue;

                String[] tokens = line.split("\\s+");
                Transaction t = new Transaction();

                for (String token : tokens) {
                    token = token.trim();
                    if (token.isEmpty()) continue;

                    ParsedToken pt = parseToken(token);
                    if (pt.itemId.isEmpty()) continue;

                    // Weight theo item (ổn định)
                    double w = weightMap.getOrDefault(pt.itemId, W_MIN);

                    // Prob:
                    // - Nếu file có sẵn p dạng "id(p)" => dùng p đó
                    // - Nếu không có => gán p theo độ phổ biến + noise nhỏ (có seed)
                    double p;
                    if (pt.hasProb) {
                        p = clamp(pt.prob, P_MIN, P_MAX);
                    } else {
                        int fi = freq.getOrDefault(pt.itemId, 1);
                        double ratio = (double) fi / (double) maxFreq;      // 0..1
                        double base = 0.20 + 0.75 * Math.sqrt(ratio);        // ~[0.20..0.95]
                        double noise = (rng.nextDouble() * 2 - 1) * NOISE;   // [-NOISE..+NOISE]
                        p = clamp(base + noise, P_MIN, P_MAX);
                    }

                    Item item = new Item(pt.itemId, p, w);
                    t.addItem(item, p);
                }

                // tránh add transaction rỗng
                if (!t.getItems().isEmpty()) {
                    transactions.add(t);
                }
            }
        }
    }

    private static boolean isDataLine(String line) {
        if (line == null) return false;
        if (line.isEmpty()) return false;
        if (line.startsWith("Member") || line.startsWith("Date")) return false;
        if (line.startsWith("@")) return false;
        return true;
    }

    private static double clamp(double x, double lo, double hi) {
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }

    private static class ParsedToken {
        final String itemId;
        final boolean hasProb;
        final double prob;

        ParsedToken(String itemId, boolean hasProb, double prob) {
            this.itemId = itemId;
            this.hasProb = hasProb;
            this.prob = prob;
        }
    }

    private static ParsedToken parseToken(String token) {
        int l = token.indexOf('(');
        int r = token.indexOf(')');

        if (l > 0 && r > l) {
            String id = token.substring(0, l).trim();
            String ps = token.substring(l + 1, r).trim();
            try {
                double p = Double.parseDouble(ps);
                return new ParsedToken(id, true, p);
            } catch (NumberFormatException ex) {
                // Nếu parse lỗi, fallback coi như deterministic token
                return new ParsedToken(token.trim(), false, 0.0);
            }
        }
        return new ParsedToken(token.trim(), false, 0.0);
    }
}