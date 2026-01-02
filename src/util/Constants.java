package util;

/**
 * Quy ước:
 * - MAX_K > 0  : giới hạn độ dài itemset
 * - MAX_K <= 0 : không giới hạn k (chạy đến khi hết pattern)
 */
public class Constants {

    /** Minimum Support threshold (MSUP) */
    public static int MSUP = 2;

    /** Score threshold T (avgWeight * P(Poisson(mu) >= MSUP)) */
    public static double T = 0.2;

    /** Approx pruning parameter (chỉ có tác dụng khi mode bật APPROX) */
    public static double ALPHA = 0.6;

    /** Weight pruning: ngưỡng trung bình trọng số tối thiểu */
    public static double MIN_AVG_WEIGHT = 0.0;

    /** Max itemset size: 0/-1 = không giới hạn */
    public static int MAX_K = 3;
}
