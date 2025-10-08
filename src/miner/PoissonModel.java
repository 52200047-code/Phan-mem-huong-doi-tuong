package miner;

import db.UncertainDatabase;
import entity.Itemset;
import entity.Item;
import entity.Transaction;

public class PoissonModel implements ProbabilisticModel {

    @Override
    public double computeProbAtLeast(Itemset X, UncertainDatabase db, int msup) {
        double mu = computeMu(X, db);
        return 1.0 - Math.exp(-mu) * Math.pow(mu, msup - 1) / factorial(msup - 1);
    }

    @Override
    public double computeMu(Itemset X, UncertainDatabase db) {
        double mu = 0.0;
        for (Transaction t : db.getTransactions()) {
            double p = 1.0;
            for (Item i : X.getItems()) p *= t.getProb(i);
            mu += p;
        }
        return mu;
    }

    @Override
    public double computeVar(Itemset X, UncertainDatabase db) {
        double v = 0.0;
        for (Transaction t : db.getTransactions()) {
            double p = 1.0;
            for (Item i : X.getItems()) p *= t.getProb(i);
            v += p * (1.0 - p);
        }
        return v;
    }

    private double factorial(int n) { return (n <= 1) ? 1 : n * factorial(n - 1); }
}
