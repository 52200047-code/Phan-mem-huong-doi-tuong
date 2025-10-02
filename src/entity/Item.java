package entity;

import java.util.Objects;

public class Item implements Comparable<Item> {
    private final String name;
    private final double weight; // w(i)

    public Item(String name, double weight) {
        this.name = Objects.requireNonNull(name);
        if (weight < 0) throw new IllegalArgumentException("weight >= 0");
        this.weight = weight;
    }

    public String getName() { return name; }
    public double getWeight() { return weight; }

    @Override public String toString() { return name + "(" + weight + ")"; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item item = (Item) o;
        return name.equals(item.name);
    }

    @Override public int hashCode() { return name.hashCode(); }

    @Override public int compareTo(Item o) { return this.name.compareTo(o.name); }
}
