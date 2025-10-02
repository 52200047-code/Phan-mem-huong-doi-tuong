import java.util.*;

/** Tập mục có trọng số trung bình + các thống kê (μ, v.v.) cache được. */
public final class Itemset implements Comparable<Itemset> {
    private final SortedSet<Item> items;
    private Double avgWeight = null;   // w(X) = avg w(i)
    private Double mu = null;          // μ_X = sum_t P(X⊆t)
    private Double minItemWeight = null;

    public Itemset(Collection<Item> items) {
        SortedSet<Item> s = new TreeSet<>(Objects.requireNonNull(items));
        if (s.isEmpty()) throw new IllegalArgumentException("itemset must be non-empty");
        this.items = Collections.unmodifiableSortedSet(s);
    }

    public SortedSet<Item> items() { return items; }

    public Itemset unionWith(Item i) {
        SortedSet<Item> s = new TreeSet<>(items);
        s.add(i);
        return new Itemset(s);
    }

    public int size() { return items.size(); }

    public double avgWeight() {
        if (avgWeight == null) {
            double sum = 0;
            for (Item i : items) sum += i.weight();
            avgWeight = sum / items.size();
        }
        return avgWeight;
    }

    public double minItemWeight() {
        if (minItemWeight == null) {
            double m = Double.POSITIVE_INFINITY;
            for (Item i : items) m = Math.min(m, i.weight());
            minItemWeight = m;
        }
        return minItemWeight;
    }

    public void setMu(double mu) { this.mu = mu; }
    public Double getMu() { return mu; }

    @Override public int compareTo(Itemset o) {
        // so sánh từ điển để dùng làm key set/map
        Iterator<Item> a = this.items.iterator();
        Iterator<Item> b = o.items.iterator();
        while (a.hasNext() && b.hasNext()) {
            int c = a.next().compareTo(b.next());
            if (c != 0) return c;
        }
        return Integer.compare(this.items.size(), o.items.size());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Itemset)) return false;
        return items.equals(((Itemset) o).items);
    }
    @Override public int hashCode() { return items.hashCode(); }

    @Override public String toString() {
        return items.toString();
    }
}
