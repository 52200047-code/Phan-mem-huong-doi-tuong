package entity;

import java.util.Set;
import java.util.HashSet;

public class Itemset {
    private Set<Item> items = new HashSet<>();

    public Itemset(Set<Item> items) {
        this.items = items;
    }

    public Set<Item> getItems() { return items; }

    public double avgWeight() {
        return items.stream().mapToDouble(Item::getWeight).average().orElse(0);
    }

    @Override
    public String toString() {
        return items.toString();
    }
}
