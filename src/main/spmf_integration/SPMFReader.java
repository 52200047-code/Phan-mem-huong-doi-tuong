package spmf_integration;

import db.UncertainDatabase;
import entity.Item;
import entity.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Adapter đọc dữ liệu:
 * 1) input.txt (format #WEIGHTS/#DATA) -> loadFromInputFile
 * 2) SPMF/FIMI deterministic (mỗi dòng = list itemID) -> sinh w(i) và p(i|t)
 */
public class SPMFReader {

    /**
     * Backward compatible method (giống code cũ):
     * - mọi item weight=defaultWeight
     * - mọi occurrence prob=defaultProb
     */
    public static UncertainDatabase load(String filepath, double defaultProb, double defaultWeight) throws IOException {
        Path path = resolvePathSmart(filepath);
        if (!Files.exists(path))
            throw new IOException("Không tìm thấy file: " + path.toAbsolutePath().normalize());

        // Nếu là input.txt thì ưu tiên đọc theo format chuẩn
        if (looksLikeInputFile(path)) {
            UncertainDatabase db = new UncertainDatabase();
            db.loadFromInputFile(path.toString());
            return db;
        }

        UncertainDatabase db = new UncertainDatabase();
        Map<Integer, Item> itemMap = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                Transaction t = new Transaction();
                String[] tokens = line.split("\\s+");

                for (String token : tokens) {
                    if (token == null || token.trim().isEmpty())
                        continue;

                    int id = Integer.parseInt(token.trim());
                    Item item = itemMap.get(id);

                    if (item == null) {
                        String itemName = "I" + id;
                        item = new Item(itemName, 1.0, defaultWeight);
                        itemMap.put(id, item);
                    }

                    t.addItem(item, clip01(defaultProb));
                }

                if (!t.isEmpty())
                    db.addTransaction(t);
            }
        }
        return db;
    }

    /**
     * Load SPMF/FIMI deterministic nhưng sinh đúng mô hình:
     * - weight w(i): cố định theo item
     * - prob p(i|t): theo từng transaction (Gaussian clipped)
     */
    public static UncertainDatabase loadDeterministicSPMF(
            String filepath,
            int maxTransactions,
            long seed,
            double meanProb,
            double varProb) throws IOException {

        Path path = resolvePathSmart(filepath);
        if (!Files.exists(path))
            throw new IOException("Không tìm thấy file: " + path.toAbsolutePath().normalize());

        // Nếu là input.txt thì đọc thẳng
        if (looksLikeInputFile(path)) {
            UncertainDatabase db = new UncertainDatabase();
            db.loadFromInputFile(path.toString());
            return db;
        }

        Random rnd = new Random(seed);

        UncertainDatabase db = new UncertainDatabase();

        // registry: id -> Item (weight fixed)
        Map<Integer, Item> itemMap = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int tx = 0;

            while ((line = br.readLine()) != null) {
                if (tx >= maxTransactions)
                    break;

                line = line.trim();
                if (line.isEmpty())
                    continue;

                Transaction t = new Transaction();
                String[] tokens = line.split("\\s+");

                for (String token : tokens) {
                    if (token == null)
                        continue;
                    token = token.trim();
                    if (token.isEmpty())
                        continue;

                    int id = Integer.parseInt(token);
                    Item item = itemMap.get(id);

                    if (item == null) {
                        // weight cố định theo item: uniform (0,1]
                        double w = rnd.nextDouble();
                        if (w <= 0.0)
                            w = 1e-6;

                        String itemName = "I" + id;
                        item = new Item(itemName, 1.0, w);
                        itemMap.put(id, item);
                    }

                    // p(i|t): theo từng giao dịch
                    double p = sampleGaussianClipped(rnd, meanProb, varProb);
                    t.addItem(item, p);
                }

                if (!t.isEmpty()) {
                    db.addTransaction(t);
                    tx++;
                }
            }
        }

        return db;
    }

    // ---------------- helpers ----------------

    private static boolean looksLikeInputFile(Path path) {
        // kiểm tra nhanh vài dòng đầu có "#WEIGHTS" hoặc "#DATA"
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 20; i++) {
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
}
