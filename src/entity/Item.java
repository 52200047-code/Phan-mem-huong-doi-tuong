package entity;

import java.util.Locale;
import java.util.Objects;

/**
 * Item (mục dữ liệu) trong bài toán WPFI.
 *
 * Lưu ý quan trọng theo mô hình trong paper:
 * - Trọng số w(i) : thuộc về Item và là cố định theo item (weight table).
 * - Xác suất xuất hiện p(i|t) : thuộc về từng Transaction (existential
 * probability),
 * nên KHÔNG nằm trong Item.
 *
 * Để tương thích với code cũ, class vẫn giữ field "probability" nhưng chỉ xem
 * như
 * "baseProbability/prior" (không dùng trong tính mu/support theo từng giao
 * dịch).
 */
public class Item implements Comparable<Item> {

    private final String name;

    // prior/base probability (KHÔNG phải p(i|t) theo từng transaction)
    private final double probability;

    // weight table: w(i)
    private final double weight;

    public Item(String name, double probability, double weight) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name is null/empty");
        }
        this.name = name.trim();

        // probability here is optional (prior). Keep it in [0,1] to be safe.
        this.probability = clip01(probability);

        // weight should be in (0,1] (or >0). We clip to avoid 0.
        this.weight = clipWeight(weight);
    }

    /** Constructor tiện: nếu chỉ cần name + weight (prior prob = 1.0) */
    public Item(String name, double weight) {
        this(name, 1.0, weight);
    }

    public String getName() {
        return name;
    }

    /**
     * Prior/base probability (không dùng cho p(i|t)).
     * Có thể để 1.0 để tránh hiểu nhầm.
     */
    public double getProbability() {
        return probability;
    }

    /** Trọng số w(i) cố định theo item (weight table) */
    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s(w=%.4f)", name, weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Item))
            return false;
        Item item = (Item) o;
        // so sánh theo tên để đảm bảo registry hoạt động ổn định
        return name.equals(item.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(Item o) {
        return this.name.compareTo(o.name);
    }

    private static double clip01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v))
            return 1e-6;
        if (v <= 0.0)
            return 1e-6;
        if (v > 1.0)
            return 1.0;
        return v;
    }

    private static double clipWeight(double w) {
        if (Double.isNaN(w) || Double.isInfinite(w))
            return 1e-6;
        if (w <= 0.0)
            return 1e-6;
        // nếu bạn muốn giới hạn weight trong (0,1] thì mở clip này:
        if (w > 1.0)
            return 1.0;
        return w;
    }
}
