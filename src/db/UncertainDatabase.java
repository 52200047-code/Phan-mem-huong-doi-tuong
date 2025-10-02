package db;

import entity.Transaction;
import java.util.ArrayList;
import java.util.List;

public class UncertainDatabase {
    private List<Transaction> transactions = new ArrayList<>();

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public int size() { return transactions.size(); }
}
