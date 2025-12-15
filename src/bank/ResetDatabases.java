package bank;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ResetDatabases {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/";
        String user = "root";
        String pass = "";

        String[] dbs = {
                "bank_system",
                "bank_system_node1",
                "bank_system_node2",
                "bank_system_node3"
        };

        try (Connection conn = DriverManager.getConnection(url, user, pass);
                Statement stmt = conn.createStatement()) {

            System.out.println("=== RESETTING DATABASES ===");
            for (String db : dbs) {
                System.out.print("Resetting " + db + "...");
                stmt.executeUpdate("DROP DATABASE IF EXISTS " + db);
                stmt.executeUpdate("CREATE DATABASE " + db);
                System.out.println(" DONE [Dropped & Created]");
            }
            System.out.println("=== ALL DATABASES CLEARED SUCCESSFULLY ===");
            System.out.println("Please restart your nodes and REGISTER new accounts.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
