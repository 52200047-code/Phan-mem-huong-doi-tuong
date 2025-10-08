package spmf_integration;

import ca.pfv.spmf.algorithms.AbstractAlgorithm;
import db.UncertainDatabase;
import miner.*;
import entity.Itemset;

import java.io.*;
import java.util.*;

/** 
 * Thuật toán WPFI tích hợp vào framework SPMF 
 */
public class AlgoWPFI extends AbstractAlgorithm {

    @Override
    public void runAlgorithm(String input, String output, double minsup) throws IOException {
        UncertainDatabase db = SPMFReader.load(input, 0.8, 1.0);
        ProbabilisticModel model = new PoissonModel();

        WPFI_Apriori algo = new WPFI_Apriori(db, model);
        Set<Itemset> results = algo.mine();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            for (Itemset X : results) writer.write(X.toString() + "\n");
        }
    }

    @Override
    public void printStatistics() {
        System.out.println("AlgoWPFI run successfully");
    }
}
