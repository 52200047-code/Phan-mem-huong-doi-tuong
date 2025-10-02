import java.util.Objects;

/** Item có trọng số (không đổi theo giao dịch). */
public final class Item implements Comparable<Item> {
    private final String name;
    private final double weight; // w(i) in (0,1] hoặc bất kỳ >=0

    public Item(String name, double weight) {
        this.name = Objects.requireNonNull(name);
        if (weight < 0) throw new IllegalArgumentException("weight must be >= 0");
        this.weight = weight;
    }

    public String name() { return name; }
    public double weight() { return weight; }

    @Override public int compareTo(Item o) { return this.name.compareTo(o.name); }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item item = (Item) o;
        return name.equals(item.name);
    }
    @Override public int hashCode() { return name.hashCode(); }

    @Override public String toString() { return name + "{" + weight + "}"; }
}
