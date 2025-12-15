package db;

import entity.Item;
import entity.Transaction;

import java.io.*;
import java.util.*;

/**
 * Cơ sở dữ liệu không chắc chắn – phiên bản ổn định cho file fruithut_original.txt.
 * Không dùng random, mỗi item có:
 *  - prob mặc định = 0.8
 *  - weight mặc định = 1.0
 */
public class UncertainDatabase {

    private final List<Transaction> transactions = new ArrayList<>();

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

    /**
     * Load file có dạng:
     *   apple banana orange
     *   milk bread butter
     */
    public void loadDatabase(String dataPath) throws IOException {

        File f = new File(dataPath);
        if (!f.exists()) {
            throw new FileNotFoundException("Không tìm thấy file: " + dataPath);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;

            while ((line = br.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("Member") || line.startsWith("Date")) continue;

                // Tách theo khoảng trắng
                String[] tokens = line.split("\\s+");

                Transaction t = new Transaction();

                for (String token : tokens) {
                    token = token.trim();
                    if (token.isEmpty()) continue;

                    // Không có prob → gán mặc định
                    double prob = 0.8;
                    double weight = 1.0;

                    Item item = new Item(token, prob, weight);
                    t.addItem(item, prob);
                }

                transactions.add(t);
            }
        }
    }
}
