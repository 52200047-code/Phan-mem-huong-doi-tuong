package entity;

import java.util.Objects;

/**
 * Đại diện cho một mục dữ liệu (Item) trong cơ sở dữ liệu không chắc chắn.
 * Mỗi item có:
 *  - name: tên mục
 *  - probability: xác suất xuất hiện trong một transaction
 *  - weight: trọng số tầm quan trọng
 */
public class Item implements Comparable<Item> {
    private final String name;
    private final double probability; // P(i)
    private final double weight;      // w(i)

    /** Constructor có đủ xác suất và trọng số */
    public Item(String name, double probability, double weight) {
        this.name = Objects.requireNonNull(name);
        if (probability < 0 || probability > 1)
            throw new IllegalArgumentException("probability phải nằm trong [0,1]");
        if (weight < 0)
            throw new IllegalArgumentException("weight >= 0");

        this.probability = probability;
        this.weight = weight;
    }

    /** Constructor mặc định (nếu chưa có xác suất cụ thể) */
    public Item(String name, double weight) {
        this(name, 0.5 + Math.random() * 0.4, weight); // random xác suất 0.5–0.9
    }

    public String getName() { return name; }
    public double getProbability() { return probability; }
    public double getWeight() { return weight; }

    @Override
    public String toString() {
        return String.format("%s[p=%.2f, w=%.2f]", name, probability, weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item item = (Item) o;
        return name.equals(item.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(Item o) {
        return this.name.compareTo(o.name);
    }
}
