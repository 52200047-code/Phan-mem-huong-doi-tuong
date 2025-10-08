package spmf_integration;

import db.UncertainDatabase;
import entity.Item;
import entity.Transaction;

import java.io.*;
import java.util.*;

/**
 * Adapter đọc dữ liệu từ file dạng SPMF/FIMI (mỗi dòng = 1 transaction).
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
                for (String token : line.trim().split(" ")) {
                    int id = Integer.parseInt(token);
                    Item item = itemMap.computeIfAbsent(id, k -> new Item("I" + k, defaultWeight));
                    t.addItem(item, defaultProb);
                }
                db.addTransaction(t);
            }
        }
        return db;
    }
}
