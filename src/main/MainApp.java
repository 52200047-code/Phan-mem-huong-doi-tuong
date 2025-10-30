import db.UncertainDatabase;
import entity.*;
import miner.WPFI_Apriori;
import util.WPFI_Metrics;
import util.Constants;

import java.util.*;

/**
 * Demo chạy thuật toán WPFI-Apriori trên dữ liệu mẫu nhỏ.
 */
public class MainApp {
    public static void main(String[] args) {
        // === 1️⃣ Tạo các Item có trọng số (weight) riêng ===
        Item milk  = new Item("Milk", 0.7, 0.4);    // tên, xác suất, trọng số
        Item fruit = new Item("Fruit", 0.9, 0.9);
        Item video = new Item("Video", 0.5, 0.6);
        Item bread = new Item("Bread", 0.8, 0.5);

        // === 2️⃣ Tạo các Transaction không chắc chắn ===
        Transaction t1 = new Transaction();
        t1.addItem(milk,  0.6);
        t1.addItem(fruit, 1.0);
        t1.addItem(video, 0.3);

        Transaction t2 = new Transaction();
        t2.addItem(milk,  1.0);
        t2.addItem(fruit, 0.8);
        t2.addItem(bread, 0.7);

        Transaction t3 = new Transaction();
        t3.addItem(fruit, 0.9);
        t3.addItem(video, 0.4);
        t3.addItem(bread, 0.6);

        // === 3️⃣ Gom lại thành cơ sở dữ liệu không chắc chắn ===
        UncertainDatabase db = new UncertainDatabase();
        db.addTransaction(t1);
        db.addTransaction(t2);
        db.addTransaction(t3);

        // === 4️⃣ Chạy thuật toán chính ===
        WPFI_Apriori miner = new WPFI_Apriori(db);
        Set<Itemset> results = miner.mine();

        // === 5️⃣ In kết quả chi tiết ===
        System.out.println("========== KẾT QUẢ KHAI THÁC WPFI ==========");
        for (Itemset X : results) {
            double mu = WPFI_Metrics.computeMu(X, db.getTransactions());
            double pTail = WPFI_Metrics.poissonTailAtLeast(Constants.MSUP, mu);
            double score = X.avgWeight() * pTail;

            System.out.printf(Locale.US,
                    "Tập: %-30s | μ(X)=%.4f | pTail=%.4f | avgW=%.3f | score=%.4f%n",
                    X.toString(), mu, pTail, X.avgWeight(), score);
        }

        System.out.println("==============================================");
        System.out.println("Số tập phổ biến xác suất có trọng số: " + results.size());
    }
}
