Tính năng chính
- Hỗ trợ cơ sở dữ liệu không chắc chắn (probabilistic database)  
- Áp dụng mô hình phân phối **Poisson** để ước lượng xác suất xuất hiện  
-  3 chiến lược cắt tỉa:
  - Weight Pruning – loại bỏ mục yếu theo trọng số  
  - μ̂-Pruning – cắt tỉa theo ngưỡng kỳ vọng  
  - Approx-μ Pruning – xấp xỉ μ để giảm chi phí tính toán  
-  Cấu trúc hướng đối tượng rõ ràng, dễ mở rộng  
-  Có thể tích hợp và chạy trong SPMF Framework
Dataset Explanation (Giải thích tập dữ liệu)
  Dataset được sử dụng trong dự án là cơ sở dữ liệu giao dịch không chắc chắn (Uncertain Transaction Database), trong đó:
- Mỗi dòng trong file dữ liệu biểu diễn một giao dịch (Transaction).
- Các số nguyên cách nhau bởi dấu cách biểu diễn các mục (Item) trong giao dịch.
- Mỗi mục có:
  - Xác suất xuất hiện (`p(i,t)`) – mặc định là 0.8 nếu không chỉ định.
  - Trọng số (`w(i)`) – mức độ quan trọng hoặc độ tin cậy của mục, mặc định là 1.0.

Chạy dự án :
  Biên dịch file MainApp.java : 
    mkdir bin, src\out -Force | Out-Null
    $files = Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName }
    javac -encoding UTF-8 -d bin $files
  Chạy:
  java -Xmx4g -cp bin MainApp 1 src\data\chess.txt src\out\chess\chess_weight.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 2 src\data\chess.txt src\out\chess\chess_muhat.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 3 src\data\chess.txt src\out\chess\chess_approx.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 4 src\data\chess.txt src\out\chess\chess_all.txt 1100 0.8 3 8.5

  java -Xmx4g -cp bin MainApp 1 src\data\mushrooms.txt src\out\mushrooms\mushrooms_weight.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 2 src\data\mushrooms.txt src\out\mushrooms\mushrooms_muhat.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 3 src\data\mushrooms.txt src\out\mushrooms\mushrooms_approx.txt 1100 0.8 3 8.5
  java -Xmx4g -cp bin MainApp 4 src\data\mushrooms.txt src\out\mushrooms\mushrooms_all.txt 1100 0.8 3 8.5
