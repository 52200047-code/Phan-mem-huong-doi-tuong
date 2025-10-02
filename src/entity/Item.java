package entity;

public class Item {
    private String name;
    private double weight; // trọng số w(i)

    public Item(String name, double weight) {
        this.name = name;
        this.weight = weight;
    }

    public String getName() { return name; }
    public double getWeight() { return weight; }

    @Override
    public String toString() {
        return name + "(" + weight + ")";
    }
}
