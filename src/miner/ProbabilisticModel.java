package miner;

import db.UncertainDatabase;
import entity.Itemset;

public interface ProbabilisticModel {
    double computeProbAtLeast(Itemset X, UncertainDatabase db, int msup);
    double computeMu(Itemset X, UncertainDatabase db);
    double computeVar(Itemset X, UncertainDatabase db);
}
