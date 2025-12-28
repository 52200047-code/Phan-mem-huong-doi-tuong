import db.UncertainDatabase;
import miner.WPFI_Apriori;
import util.Constants;

import java.io.File;

public class MainApp {

    public static void main(String[] args) {

        try {
            /*1️ CẤU HÌNH */

            // ===== Chạy tùy chọn tham số =====
            // java -cp bin MainApp [algo] [dataPath] [outputPath] [MSUP] [T] [ALPHA] [MIN_AVG_WEIGHT]
            // algo:
            //   1 = WEIGHT_ONLY
            //   2 = MUHAT_ONLY
            //   3 = APPROX_ONLY
            //   4 = ALL (gộp 3 cắt tỉa)

            int algo = (args.length >= 1) ? Integer.parseInt(args[0]) : 4;
            String dataPath   = (args.length >= 2) ? args[1] : "src/data/fruithut_original.txt";
            String outputPath = (args.length >= 3) ? args[2] : ("src/out/result_algo" + algo + ".txt");

            // Thiết lập tham số mặc định (có thể override bằng args)
            Constants.MSUP  = (args.length >= 4) ? Integer.parseInt(args[3]) : 5;
            Constants.T     = (args.length >= 5) ? Double.parseDouble(args[4]) : 0.01;
            Constants.ALPHA = (args.length >= 6) ? Double.parseDouble(args[5]) : 0.5;
            Constants.MIN_AVG_WEIGHT = (args.length >= 7) ? Double.parseDouble(args[6]) : 0.0;

            WPFI_Apriori.PruningMode mode = switch (algo) {
                case 1 -> WPFI_Apriori.PruningMode.WEIGHT_ONLY;
                case 2 -> WPFI_Apriori.PruningMode.MUHAT_ONLY;
                case 3 -> WPFI_Apriori.PruningMode.APPROX_ONLY;
                default -> WPFI_Apriori.PruningMode.ALL;
            };

            // tạo folder output nếu chưa có
            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            /* 2️ LOAD DATABASE */

            UncertainDatabase db = new UncertainDatabase();
            db.loadDatabase(dataPath);

            System.out.println("\n========== DATABASE LOADED ==========");
            System.out.println("Dataset : " + dataPath);
            System.out.println("Transactions : " + db.size());
            System.out.println("====================================\n");

            /* 3 CHẠY THUẬT TOÁN */

            WPFI_Apriori miner = new WPFI_Apriori(db, mode);

            System.out.println("Bat dau khai thac WPFI...");
            System.out.println("Algo/Mode: " + algo + " / " + mode);
            System.out.println("Output (resume): " + outputPath);

            miner.mine(outputPath);

            System.out.println("\nFINISHED");
            System.out.println("Ket qua: " + outputPath);

        } catch (Exception e) {
            System.err.println("ERROR");
            e.printStackTrace();
        }
    }
}