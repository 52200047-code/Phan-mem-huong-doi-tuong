package main;

import db.UncertainDatabase;
import miner.WPFI_Apriori;
import entity.*;

import java.util.*;

public class MainApp {
    public static void main(String[] args) {
        // Demo dữ liệu
        Item milk = new Item("Milk", 0.4);
        Item fruit = new Item("Fruit", 0.9);
        Item video = new Item("Video", 0.6);

        Transaction t1 = new Transaction();
        t1.addItem(milk, 0.4);
        t1.addItem(fruit, 1.0);
        t1.addItem(video, 0.3);

        Transaction t2 = new Transaction();
        t2.addItem(milk, 1.0);
        t2.addItem(fruit, 0.8);

        UncertainDatabase db = new UncertainDatabase();
        db.addTransaction(t1);
        db.addTransaction(t2);

        // Chạy thuật toán
        WPFI_Apriori miner = new WPFI_Apriori(db);
        System.out.println("W-PFI kết quả: " + miner.mine());
    }
}
