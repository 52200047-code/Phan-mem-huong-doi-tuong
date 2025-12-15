import db.UncertainDatabase;
import miner.WPFI_Apriori;
import util.Constants;

public class MainApp {

    public static void main(String[] args) {

        try {
            /* =======================
               1️⃣ CẤU HÌNH
             ======================= */

            String dataPath   = "src/data/fruithut_original.txt";
            String outputPath = "src/out/sources.txt";

            // Thiết lập tham số (có thể chỉnh)
            Constants.MSUP  = 5;     // minsup
            Constants.T     = 0.01;  // ngưỡng xác suất * trọng số
            Constants.ALPHA = 0.5;   // pruning
            Constants.MIN_AVG_WEIGHT = 0.0;

            /* =======================
               2️⃣ LOAD DATABASE
             ======================= */

            UncertainDatabase db = new UncertainDatabase();
            db.loadDatabase(dataPath);

            System.out.println("\n========== DATABASE LOADED ==========");
            System.out.println("Dataset : " + dataPath);
            System.out.println("Transactions : " + db.size());
            System.out.println("====================================\n");

            /* =======================
               3️⃣ CHẠY THUẬT TOÁN
             ======================= */

            WPFI_Apriori miner = new WPFI_Apriori(db);

            System.out.println("🚀 Bắt đầu khai thác WPFI...");
            System.out.println("📄 Output (resume): " + outputPath);
            System.out.println("👉 Có thể Ctrl+C, chạy lại sẽ tiếp tục\n");

            miner.mine(outputPath);

            System.out.println("\n✅ KHAI THÁC HOÀN TẤT");
            System.out.println("📂 Kết quả nằm trong: " + outputPath);

        } catch (Exception e) {
            System.err.println("❌ LỖI KHI CHẠY CHƯƠNG TRÌNH:");
            e.printStackTrace();
        }
    }
}
