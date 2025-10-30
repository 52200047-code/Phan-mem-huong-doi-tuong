package spmf_integration;

import db.UncertainDatabase;
import entity.Item;
import entity.Transaction;

import java.io.*;
import java.util.*;

/**
 * Adapter đọc dữ liệu từ file dạng SPMF/FIMI (mỗi dòng = 1 transaction).
 * Mỗi item chỉ có ID, xác suất mặc định, trọng số mặc định.
 */
public class SPMFReader {

    public static UncertainDatabase load(String filepath, double defaultProb, double defaultWeight) throws IOException {
        UncertainDatabase db = new UncertainDatabase();
        Map<Integer, Item> itemMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                Transaction t = new Transaction();
                String[] tokens = line.trim().split(" ");

                for (String token : tokens) {
                    int id = Integer.parseInt(token);
                    Item item = itemMap.get(id);

                    if (item == null) {
                        String itemName = "I" + id;
                        item = new Item(itemName, defaultProb, defaultWeight); // constructor mới
                        itemMap.put(id, item);
                    }

                    // thêm item vào transaction
                    t.addItem(item, defaultProb);
                }

                db.addTransaction(t);
            }
        }
        return db;
    }
}
