package entity;

import java.util.*;

public class Itemset implements Comparable<Itemset> {
    private final SortedSet<Item> items;

    public Itemset(Set<Item> items) {
        this.items = new TreeSet<>(Objects.requireNonNull(items));
        if (this.items.isEmpty()) throw new IllegalArgumentException("itemset must be non-empty");
    }

    public SortedSet<Item> getItems() { return Collections.unmodifiableSortedSet(items); }
    public int size() { return items.size(); }

    public Itemset unionWith(Item i) {
        SortedSet<Item> s = new TreeSet<>(items);
        s.add(i);
        return new Itemset(s);
    }

    public double avgWeight() {
        double sum = 0;
        for (Item i : items) sum += i.getWeight();
        return sum / items.size();
    }

    public double minItemWeight() {
        double m = Double.POSITIVE_INFINITY;
        for (Item i : items) m = Math.min(m, i.getWeight());
        return m;
    }

    @Override public String toString() { return items.toString(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Itemset)) return false;
        Itemset that = (Itemset) o;
        return this.items.equals(that.items);
    }

    @Override public int hashCode() { return items.hashCode(); }

    @Override public int compareTo(Itemset o) {
        Iterator<Item> a = this.items.iterator();
        Iterator<Item> b = o.items.iterator();
        while (a.hasNext() && b.hasNext()) {
            int c = a.next().compareTo(b.next());
            if (c != 0) return c;
        }
        return Integer.compare(this.size(), o.size());
    }
}
