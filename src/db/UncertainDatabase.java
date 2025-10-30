package db;

import entity.Item;
import entity.Transaction;

import java.io.*;
import java.util.*;

/**
 * Lớp biểu diễn cơ sở dữ liệu không chắc chắn.
 * Hỗ trợ đọc dữ liệu từ file CSV hoặc uncertain (item:prob),
 * và nạp trọng số từ file weights.txt.
 */
public class UncertainDatabase {

    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Double> itemWeights = new HashMap<>();

    /** Thêm giao dịch vào cơ sở dữ liệu */
    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    /** Lấy danh sách giao dịch */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    /** Lấy kích thước cơ sở dữ liệu */
    public int size() {
        return transactions.size();
    }

    /** Đọc file trọng số (item weight) */
    public void loadWeights(String weightFilePath) throws IOException {
        File file = new File(weightFilePath);
        if (!file.exists()) {
            System.out.println("[Warning] File trọng số không tồn tại. Sử dụng weight mặc định = 1.0");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(weightFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    String item = parts[0];
                    double weight = Double.parseDouble(parts[1]);
                    itemWeights.put(item, weight);
                }
            }
        }
    }

    /**
     * Đọc dữ liệu chính (uncertain_db.txt hoặc groceries_uncertain.txt)
     * Hỗ trợ cả file dạng: "milk:0.8 bread:0.6" hoặc "milk,bread"
     */
    public void loadDatabase(String dataPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(dataPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Phân tách theo dấu phẩy hoặc khoảng trắng
                String[] tokens = line.contains(",") ? line.split(",") : line.split(" ");
                Transaction t = new Transaction();

                for (String token : tokens) {
                    token = token.trim();
                    if (token.isEmpty()) continue;

                    String itemName;
                    double prob;

                    // Nếu có định dạng item:prob
                    if (token.contains(":")) {
                        String[] parts = token.split(":");
                        itemName = parts[0].trim();
                        prob = Double.parseDouble(parts[1]);
                    } else {
                        itemName = token.trim();
                        prob = 0.5 + Math.random() * 0.4; // random 0.5–0.9 nếu không có xác suất
                    }

                    double weight = itemWeights.getOrDefault(itemName, 1.0);
                    Item item = new Item(itemName, prob, weight);
                    t.addItem(item, prob);
                }

                transactions.add(t);
            }
        }
    }

    /** Hàm load đầy đủ (dữ liệu + trọng số) */
    public void loadAll(String dataPath, String weightPath) throws IOException {
        loadWeights(weightPath);
        loadDatabase(dataPath);
        System.out.println("[INFO] Đã nạp " + transactions.size() + " giao dịch và " + itemWeights.size() + " trọng số.");
    }
}
