package miner;

import db.UncertainDatabase;
import entity.Itemset;
import util.Constants;
import util.WPFI_Metrics;

import java.util.*;

public class WPFI_Apriori {

    private UncertainDatabase db;

    public WPFI_Apriori(UncertainDatabase db) {
        this.db = db;
    }

    public Set<Itemset> mine() {
        Set<Itemset> results = new HashSet<>();

        // TODO: Sinh ứng viên từ 1-itemset, mở rộng theo Apriori
        // + Áp dụng 3 pruning (trọng số min, μ̂, xấp xỉ alpha)

        return results;
    }
}
