package entity;

import java.util.HashMap;
import java.util.Map;

public class Transaction {
    // item -> xác suất xuất hiện trong giao dịch
    private Map<Item, Double> itemProb = new HashMap<>();

    public void addItem(Item item, double prob) {
        itemProb.put(item, prob);
    }

    public double getProb(Item item) {
        return itemProb.getOrDefault(item, 0.0);
    }

    public Map<Item, Double> getItems() {
        return itemProb;
    }
}
