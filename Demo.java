
import java.util.*;

/** Ví dụ nhỏ: tạo DB bất định, gán trọng số, chạy w-PFI. */
public final class Demo {
    public static void main(String[] args) {
        // 1) Khai báo item + trọng số (ví dụ từ bài)
        Item milk = new Item("Milk", 0.4);
        Item fruit = new Item("Fruit", 0.9);
        Item video = new Item("Video", 0.6);

        // 2) Giao dịch bất định
        Transaction t1 = new Transaction(); // Tom
        t1.put(milk, 0.4);
        t1.put(fruit, 1.0);
        t1.put(video, 0.3);
        Transaction t2 = new Transaction(); // Lucy
        t2.put(milk, 1.0);
        t2.put(fruit, 0.8);

        UncertainDatabase db = new UncertainDatabase(Arrays.asList(t1, t2));

        // 3) Tham số: msup=2, t=0.2, alpha=0.6, dùng APPROX + POISSON
        Params params = new Params(
                /* msup */ 2,
                /* t */ 0.2,
                /* alpha */0.6,
                /* mode */ Params.Mode.APPROX,
                /* approx */ Params.ApproxFamily.POISSON);

        WeightedPFIMiner miner = new WeightedPFIMiner(db, params);
        Map<Integer, Set<Itemset>> result = miner.mine();

        // 4) In kết quả
        System.out.println("=== Weighted Probabilistic Frequent Itemsets (w-PFIs) ===");
        for (Map.Entry<Integer, Set<Itemset>> e : result.entrySet()) {
            System.out.println("k = " + e.getKey());
            for (Itemset X : e.getValue()) {
                System.out.printf("  %s  w=%.3f  mu=%.3f%n",
                        X.items(), X.avgWeight(), X.getMu());
            }
        }
    }
}
