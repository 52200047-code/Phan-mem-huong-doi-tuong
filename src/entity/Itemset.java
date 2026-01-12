package entity;

import java.util.*;

/**
 * Itemset (tập item) dùng trong WPFI-Apriori.
 *
 * - Weight của itemset X dùng công thức trung bình:
 * w(X) = (1/|X|) * sum_{i in X} w(i)
 * - Xác suất p(i|t) nằm trong Transaction, không nằm ở đây.
 */
public class Itemset implements Comparable<Itemset> {

    private final SortedSet<Item> items;

    public Itemset(Set<Item> items) {
        this.items = new TreeSet<>(Objects.requireNonNull(items));
        if (this.items.isEmpty())
            throw new IllegalArgumentException("itemset must be non-empty");
    }

    public SortedSet<Item> getItems() {
        return Collections.unmodifiableSortedSet(items);
    }

    public int size() {
        return items.size();
    }

    public boolean contains(Item i) {
        return items.contains(i);
    }

    public List<Item> asList() {
        return new ArrayList<>(items);
    }

    /** X ∪ {i} */
    public Itemset unionWith(Item i) {
        SortedSet<Item> s = new TreeSet<>(items);
        s.add(i);
        return new Itemset(s);
    }

    /** w(X) = average weight */
    public double avgWeight() {
        double sum = 0.0;
        for (Item i : items)
            sum += i.getWeight();
        return sum / items.size();
    }

    /** min weight trong X (phục vụ weight pruning) */
    public double minItemWeight() {
        double m = Double.POSITIVE_INFINITY;
        for (Item i : items)
            m = Math.min(m, i.getWeight());
        return m;
    }

    /**
     * In ra chỉ tên item để output.txt gọn:
     * ví dụ: {Milk, Fruit, Video}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Item i : items) {
            if (!first)
                sb.append(", ");
            sb.append(i.getName());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Itemset))
            return false;
        Itemset that = (Itemset) o;
        return this.items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public int compareTo(Itemset o) {
        Iterator<Item> a = this.items.iterator();
        Iterator<Item> b = o.items.iterator();
        while (a.hasNext() && b.hasNext()) {
            int c = a.next().compareTo(b.next());
            if (c != 0)
                return c;
        }
        return Integer.compare(this.size(), o.size());
    }
}
