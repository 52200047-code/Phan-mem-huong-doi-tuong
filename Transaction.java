
import java.util.*;

/** Giao dịch bất định: mỗi item có xác suất xuất hiện p(i|T). */
public final class Transaction {
    private final Map<Item, Double> pByItem = new HashMap<>();

    public void put(Item item, double prob) {
        if (prob < 0 || prob > 1)
            throw new IllegalArgumentException("prob in [0,1]");
        pByItem.put(item, prob);
    }

    public double probOf(Item item) {
        return pByItem.getOrDefault(item, 0.0);
    }

    public Set<Item> items() {
        return Collections.unmodifiableSet(pByItem.keySet());
    }
}
