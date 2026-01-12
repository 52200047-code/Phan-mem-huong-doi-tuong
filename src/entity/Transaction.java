package entity;

import java.util.*;

/**
 * Đại diện cho một giao dịch không chắc chắn (uncertain transaction).
 * Mỗi giao dịch chứa nhiều Item, mỗi Item có:
 * - xác suất xuất hiện trong giao dịch: p(i|t) (existential probability)
 * - trọng số riêng của item: w(i) (weight table, cố định theo item)
 */
public class Transaction {

    // Map<Item, probability> : Xác suất xuất hiện của từng item trong giao dịch
    private final Map<Item, Double> itemProb = new LinkedHashMap<>();

    public Transaction() {
    }

    /** Thêm một item vào giao dịch với xác suất xuất hiện cụ thể */
    public void addItem(Item item, double prob) {
        if (item == null)
            throw new IllegalArgumentException("item is null");
        if (prob < 0 || prob > 1)
            throw new IllegalArgumentException("prob phải nằm trong [0,1]");
        itemProb.put(item, prob);
    }

    /** Lấy xác suất của một item trong giao dịch */
    public double getProb(Item item) {
        return itemProb.getOrDefault(item, 0.0);
    }

    /** Trả về danh sách tất cả các item trong giao dịch */
    public Set<Item> getItems() {
        return itemProb.keySet();
    }

    /** Trả về map item -> prob (dùng trong thuật toán Apriori) */
    public Map<Item, Double> getItemProbMap() {
        return Collections.unmodifiableMap(itemProb);
    }

    /** Số item trong transaction */
    public int size() {
        return itemProb.size();
    }

    public boolean isEmpty() {
        return itemProb.isEmpty();
    }

    /** Tính trọng số trung bình của giao dịch (phục vụ debug/quan sát) */
    public double getAverageWeight() {
        if (itemProb.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (Item i : itemProb.keySet())
            sum += i.getWeight();
        return sum / itemProb.size();
    }

    /** Tính xác suất trung bình (debug/heuristic) */
    public double getAverageProb() {
        if (itemProb.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (double p : itemProb.values())
            sum += p;
        return sum / itemProb.size();
    }

    /** Trả về chuỗi mô tả (dùng để debug hoặc log dữ liệu) */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{ ");
        for (Map.Entry<Item, Double> e : itemProb.entrySet()) {
            sb.append(e.getKey().getName())
                    .append(":p=").append(String.format(Locale.US, "%.4f", e.getValue()))
                    .append(",w=").append(String.format(Locale.US, "%.4f", e.getKey().getWeight()))
                    .append(" ");
        }
        sb.append("}");
        return sb.toString();
    }
}
