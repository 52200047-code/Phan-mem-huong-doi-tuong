package spmf_integration;

import db.UncertainDatabase;
import miner.WPFI_Apriori;
import util.Constants;

import java.io.IOException;

/**
 * Tích hợp thuật toán Weighted Probabilistic Frequent Itemset (WPFI)
 * vào framework SPMF.
 */
public class AlgoWPFI {

    /**
     * @param input   đường dẫn dataset
     * @param output  file xuất kết quả (có resume)
     * @param minsup  minsup (sẽ map sang Constants.MSUP)
     */
    public void runAlgorithm(String input, String output, double minsup) throws IOException {

        /* 1️⃣ Ánh xạ minsup SPMF → hệ thống WPFI */
        Constants.MSUP = (int) Math.ceil(minsup);

        System.out.println("[SPMF] MSUP = " + Constants.MSUP);
        System.out.println("[SPMF] Input  = " + input);
        System.out.println("[SPMF] Output = " + output);

        /* 2️⃣ Load database */
        UncertainDatabase db = SPMFReader.load(input, 0.8, 1.0);

        /* 3️⃣ Chạy thuật toán (auto write + resume) */
        WPFI_Apriori algo = new WPFI_Apriori(db);
        algo.mine(output);

        System.out.println("[SPMF] WPFI mining finished.");
    }

    public void printStatistics() {
        System.out.println("✅ AlgoWPFI run successfully!");
    }
}
