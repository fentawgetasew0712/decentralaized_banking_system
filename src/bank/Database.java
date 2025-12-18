package bank;

import java.sql.*;
// import java.util.*; -> Unused

/**
 * Database
 * 
 * Persistent Database using MySQL.
 * Configured for XAMPP (root, no password).
 * DISTRIBUTED VERSION: Each node has its own database instance.
 */
public class Database {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/";
    private static final String DB_BASE_NAME = "bank_system";
    private static final String USER = "root";
    private static final String PASS = "";

    private Connection conn;
    private String dbName;
    private int nodeId;

    public static class Account {
        public String id;
        public String name;
        public String password;
        public double balance;
        public String role;

        public Account(String id, String name, String password, double balance, String role) {
            this.id = id;
            this.name = name;
            this.password = password;
            this.balance = balance;
            this.role = role;
        }
    }

    /**
     * Constructor for distributed database.
     * Each node gets its own database: bank_system_node1, bank_system_node2, etc.
     */
    public Database(int nodeId) {
        this.nodeId = nodeId;
        this.dbName = DB_BASE_NAME + "_node" + nodeId;
        connect();
        initDB();
    }

    /**
     * Legacy constructor for backward compatibility (uses default database)
     */
    public Database() {
        this.nodeId = 0;
        this.dbName = DB_BASE_NAME;
        connect();
        initDB();
    }

    public String getDatabaseName() {
        return dbName;
    }

    public int getNodeId() {
        return nodeId;
    }

    private void connect() {
        try {
            // Explicitly load driver to ensure it's registered
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Check if DB exists
            Connection tempConn = DriverManager.getConnection(DB_URL, USER, PASS);
            Statement stmt = tempConn.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
            stmt.close();
            tempConn.close();

            // Connect to actual DB
            conn = DriverManager.getConnection(DB_URL + dbName, USER, PASS);
            System.out.println("Database: Connected to MySQL (" + dbName + ")");
        } catch (Exception e) {
            System.err.println("Database Connection Error: " + e.getMessage());
            System.err.println("Make sure MySQL (XAMPP) is running!");
        }
    }

    private void initDB() {
        if (conn == null)
            return;
        String sqlUsers = "CREATE TABLE IF NOT EXISTS users ("
                + "id VARCHAR(12) PRIMARY KEY, "
                + "name VARCHAR(100) NOT NULL, "
                + "password VARCHAR(100) NOT NULL, "
                + "balance DECIMAL(15, 2) DEFAULT 0.0, "
                + "role VARCHAR(20) DEFAULT 'user')";

        String sqlTrans = "CREATE TABLE IF NOT EXISTS transactions ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "type VARCHAR(20), "
                + "user_id VARCHAR(12), "
                + "amount VARCHAR(20), "
                + "target_id VARCHAR(12), "
                + "node_id INT)";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlTrans);

            // Database Migrations
            try {
                // 1. Upgrade balance from INT to DECIMAL
                stmt.execute("ALTER TABLE users MODIFY COLUMN balance DECIMAL(15, 2) DEFAULT 0.0");
            } catch (SQLException e) {
            }

            try {
                // 2. Add role column if missed
                stmt.execute("ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'user'");
            } catch (SQLException e) {
            }

            // Create or Update default admin account
            String checkAdmin = "SELECT * FROM users WHERE id = '000000000000'";
            ResultSet rs = stmt.executeQuery(checkAdmin);
            if (!rs.next()) {
                String insertAdmin = "INSERT INTO users (id, name, password, balance, role) VALUES ('000000000000', 'System Admin', '"
                        + PasswordUtils.hash("admin123") + "', 0, 'admin')";
                stmt.executeUpdate(insertAdmin);
                System.out.println("Database: Default admin account created (000000000000 / admin123 [hashed])");
            } else {
                String currentPass = rs.getString("password");
                if ("admin123".equals(currentPass)) {
                    String updateAdmin = "UPDATE users SET password = ? WHERE id = '000000000000'";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateAdmin)) {
                        pstmt.setString(1, PasswordUtils.hash("admin123"));
                        pstmt.executeUpdate();
                        System.out.println("Database: Migrated admin password to hash");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Init DB Error: " + e.getMessage());
        }
    }

    public synchronized boolean createAccount(String id, String name, String password, double initialBalance,
            String role) {
        if (conn == null)
            return false;
        String sql = "INSERT INTO users (id, name, password, balance, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, PasswordUtils.hash(password));
            pstmt.setDouble(4, initialBalance);
            pstmt.setString(5, role);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Create Account Error (might exist): " + e.getMessage());
            return false;
        }
    }

    // Default version for regular users
    public synchronized boolean createAccount(String id, String name, String password, double initialBalance) {
        return createAccount(id, name, password, initialBalance, "user");
    }

    public boolean authenticate(String id, String password) {
        if (conn == null)
            return false;
        String sql = "SELECT password FROM users WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password");
                return storedHash.equals(PasswordUtils.hash(password));
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Account getAccount(String id) {
        if (conn == null)
            return null;
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Account(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getDouble("balance"),
                        rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public double getBalance(String id) {
        Account acc = getAccount(id);
        return (acc != null) ? acc.balance : -1.0;
    }

    public boolean accountExists(String id) {
        return getAccount(id) != null;
    }

    public synchronized void updateBalance(String id, double newBalance) {
        if (conn == null)
            return;
        String sql = "UPDATE users SET balance = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newBalance);
            pstmt.setString(2, id);
            pstmt.executeUpdate();
            System.out.println("Database: Updated " + id + " -> $" + newBalance);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get all accounts serialized as a string for synchronization.
     * Format: id:name:pass:balance|id:name:pass:balance|...
     */
    public String getAllAccountsSerialized() {
        if (conn == null)
            return "";
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT * FROM users";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (sb.length() > 0)
                    sb.append("|");
                sb.append(rs.getString("id")).append(":")
                        .append(rs.getString("name")).append(":")
                        .append(rs.getString("password")).append(":")
                        .append(rs.getInt("balance")).append(":")
                        .append(rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * Insert or Update account (Upsert) for synchronization
     */
    public synchronized void upsertAccount(String id, String name, String password, double balance, String role) { // Changed
                                                                                                                   // int
                                                                                                                   // balance
                                                                                                                   // to
                                                                                                                   // double
                                                                                                                   // balance
        if (conn == null)
            return;

        // Try to update first
        if (accountExists(id)) {
            String sql = "UPDATE users SET name=?, password=?, balance=?, role=? WHERE id=?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, password);
                pstmt.setDouble(3, balance); // Changed setInt to setDouble
                pstmt.setString(4, role);
                pstmt.setString(5, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            // Insert if not exists
            createAccount(id, name, password, balance, role);
        }
    }

    public synchronized void upsertAccount(String id, String name, String password, double balance) {
        upsertAccount(id, name, password, balance, "user");
    }

    public static class Transaction {
        public int id;
        public String timestamp;
        public String type;
        public String userId;
        public String amount;
        public String targetId;
        public int nodeId;

        public Transaction(int id, String timestamp, String type, String userId, String amount, String targetId,
                int nodeId) {
            this.id = id;
            this.timestamp = timestamp;
            this.type = type;
            this.userId = userId;
            this.amount = amount;
            this.targetId = targetId;
            this.nodeId = nodeId;
        }
    }

    public synchronized void logTransaction(String type, String userId, String amount, String targetId) {
        if (conn == null)
            return;
        String sql = "INSERT INTO transactions (type, user_id, amount, target_id, node_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, userId);
            pstmt.setString(3, amount);
            pstmt.setString(4, targetId != null ? targetId : "");
            pstmt.setInt(5, nodeId);
            pstmt.executeUpdate();
            System.out.println("📝 Database: Logged " + type + " for " + userId);
        } catch (SQLException e) {
            System.err.println("Log Error: " + e.getMessage());
        }
    }

    public java.util.List<Transaction> getAllTransactions() {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        if (conn == null)
            return list;
        String sql = "SELECT * FROM transactions ORDER BY id DESC LIMIT 50";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Transaction(
                        rs.getInt("id"),
                        rs.getString("timestamp"),
                        rs.getString("type"),
                        rs.getString("user_id"),
                        rs.getString("amount"),
                        rs.getString("target_id"),
                        rs.getInt("node_id")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public java.util.List<Account> getAllUsers() {
        java.util.List<Account> list = new java.util.ArrayList<>();
        if (conn == null)
            return list;
        String sql = "SELECT * FROM users";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Account(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getDouble("balance"), // Changed from getInt to getDouble
                        rs.getString("role")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
