package spmf_integration;

import db.UncertainDatabase;
import miner.WPFI_Apriori;
import entity.Itemset;

import java.io.*;
import java.util.*;

/**
 * Tích hợp thuật toán Weighted Probabilistic Frequent Itemset (WPFI)
 * vào framework SPMF.
 */
public class AlgoWPFI {

    public void runAlgorithm(String input, String output, double minsup) throws IOException {
        // 1️⃣ Đọc cơ sở dữ liệu không chắc chắn từ file
        UncertainDatabase db = SPMFReader.load(input, 0.8, 1.0); // defaultProb=0.8, weight=1.0

        // 2️⃣ Chạy thuật toán
        WPFI_Apriori algo = new WPFI_Apriori(db);
        Set<Itemset> results = algo.mine();

        // 3️⃣ Xuất kết quả
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            for (Itemset X : results) {
                writer.write(X.toString());
                writer.newLine();
            }
        }
    }

    public void printStatistics() {
        System.out.println("✅ AlgoWPFI run successfully!");
    }
}
