package db;

import entity.Item;
import entity.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Cơ sở dữ liệu không chắc chắn (Uncertain Database).
 *
 * - Weight w(i): cố định theo item (weight table)
 * - Probability p(i|t): theo từng transaction (existential probability)
 *
 * Hỗ trợ:
 * 1) input.txt format:
 * #WEIGHTS
 * item <tab/space> weight
 * #DATA
 * item:prob item:prob ...
 *
 * 2) dataset dạng SPMF/FIMI:
 * mỗi dòng là list item (space/comma)
 * nếu không có ":prob" thì có thể gán random prob (fallback)
 */
public class UncertainDatabase {

    private final List<Transaction> transactions = new ArrayList<>();

    // weight table: itemName -> weight
    private final Map<String, Double> itemWeights = new HashMap<>();

    // registry to avoid creating Item objects repeatedly
    private final Map<String, Item> itemRegistry = new HashMap<>();

    /** Thêm giao dịch */
    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    /** Danh sách giao dịch */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    public int size() {
        return transactions.size();
    }

    /** Clear toàn bộ dữ liệu */
    public void clear() {
        transactions.clear();
        itemWeights.clear();
        itemRegistry.clear();
    }

    // =========================================================
    // Weight loading
    // =========================================================

    /** Load weight table từ file: "item weight" hoặc "item<TAB>weight" */
    public void loadWeights(String weightFilePath) throws IOException {
        Path path = resolvePathSmart(weightFilePath);
        if (!Files.exists(path)) {
            System.out.println("[Warning] File trọng số không tồn tại: " + path.toAbsolutePath().normalize()
                    + ". Sử dụng weight mặc định = 1.0");
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.startsWith("#") || line.startsWith("//"))
                    continue;

                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String item = parts[0].trim();
                    Double w = tryParseDouble(parts[1].trim(), null);
                    if (item.isEmpty() || w == null)
                        continue;
                    if (w <= 0)
                        w = 1e-6; // tránh 0
                    itemWeights.put(item, w);
                }
            }
        }
    }

    // =========================================================
    // Input.txt loader (recommended)
    // =========================================================

    /**
     * Load từ input.txt format:
     * #WEIGHTS
     * item weight
     * #DATA
     * item:prob item:prob ...
     */
    public void loadFromInputFile(String inputPath) throws IOException {
        Path path = resolvePathSmart(inputPath);
        if (!Files.exists(path)) {
            throw new IOException("Không tìm thấy input file: " + path.toAbsolutePath().normalize());
        }

        // reset current
        transactions.clear();
        itemWeights.clear();
        itemRegistry.clear();

        boolean inWeights = false;
        boolean inData = false;

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (line.startsWith("#META"))
                    continue;

                if (line.equalsIgnoreCase("#WEIGHTS")) {
                    inWeights = true;
                    inData = false;
                    continue;
                }
                if (line.equalsIgnoreCase("#DATA")) {
                    inWeights = false;
                    inData = true;
                    continue;
                }

                if (inWeights) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        String name = parts[0].trim();
                        Double w = tryParseDouble(parts[1].trim(), null);
                        if (name.isEmpty() || w == null)
                            continue;
                        if (w <= 0)
                            w = 1e-6;
                        itemWeights.put(name, w);
                    }
                    continue;
                }

                if (inData) {
                    // transaction: item:prob item:prob ...
                    String[] tokens = line.split("[,\\s]+");
                    Transaction t = new Transaction();

                    for (String tok : tokens) {
                        tok = tok.trim();
                        if (tok.isEmpty())
                            continue;

                        int colon = tok.lastIndexOf(':');
                        if (colon <= 0 || colon >= tok.length() - 1)
                            continue;

                        String name = tok.substring(0, colon).trim();
                        Double p = tryParseDouble(tok.substring(colon + 1).trim(), null);
                        if (name.isEmpty() || p == null)
                            continue;

                        double prob = clip01(p);

                        Item item = getOrCreateItem(name);
                        t.addItem(item, prob);
                    }

                    if (!t.isEmpty()) {
                        transactions.add(t);
                    }
                }
            }
        }
    }

    // =========================================================
    // Generic dataset loader (fallback)
    // =========================================================

    /**
     * Load dataset dạng:
     * - "a b c" hoặc "a,b,c"
     * - "a:0.8 b:0.5"
     *
     * Nếu token không có ":prob" thì fallback random prob trong [0.5,0.9]
     * (Khuyến nghị: dùng MainApp để generate input.txt chuẩn trước)
     */
    public void loadDatabase(String dataPath) throws IOException {
        Path path = resolvePathSmart(dataPath);
        if (!Files.exists(path)) {
            throw new IOException("Không tìm thấy dataset: " + path.toAbsolutePath().normalize());
        }

        // reset only transactions + registry (giữ weights nếu user đã loadWeights
        // trước)
        transactions.clear();
        itemRegistry.clear();

        Random rnd = new Random(20260106L);

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String[] tokens = line.split("[,\\s]+");
                Transaction t = new Transaction();

                for (String tok : tokens) {
                    tok = tok.trim();
                    if (tok.isEmpty())
                        continue;

                    String name;
                    double prob;

                    int colon = tok.lastIndexOf(':');
                    if (colon > 0 && colon < tok.length() - 1) {
                        name = tok.substring(0, colon).trim();
                        Double p = tryParseDouble(tok.substring(colon + 1).trim(), null);
                        prob = (p == null) ? (0.5 + rnd.nextDouble() * 0.4) : clip01(p);
                    } else {
                        name = tok;
                        prob = 0.5 + rnd.nextDouble() * 0.4; // fallback
                    }

                    if (name.isEmpty())
                        continue;
                    Item item = getOrCreateItem(name);
                    t.addItem(item, prob);
                }

                if (!t.isEmpty())
                    transactions.add(t);
            }
        }
    }

    /** Load đầy đủ: (weights + data) */
    public void loadAll(String dataPath, String weightPath) throws IOException {
        loadWeights(weightPath);
        loadDatabase(dataPath);
        System.out
                .println("[INFO] Đã nạp " + transactions.size() + " giao dịch và " + itemWeights.size() + " trọng số.");
    }

    // =========================================================
    // Internals
    // =========================================================

    private Item getOrCreateItem(String name) {
        Item ex = itemRegistry.get(name);
        if (ex != null)
            return ex;

        double w = itemWeights.getOrDefault(name, 1.0);
        if (w <= 0)
            w = 1e-6;

        // probability field trong Item KHÔNG dùng để tính p(i|t); p(i|t) nằm ở
        // Transaction
        // nên đặt 1.0 cho rõ ràng (hoặc 0.0 cũng được miễn trong [0,1])
        Item item = new Item(name, 1.0, w);
        itemRegistry.put(name, item);
        return item;
    }

    private static Path resolvePathSmart(String pathStr) {
        Path p = Paths.get(pathStr);
        if (p.isAbsolute())
            return p.normalize();
        return Paths.get("").toAbsolutePath().resolve(p).normalize();
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
}
