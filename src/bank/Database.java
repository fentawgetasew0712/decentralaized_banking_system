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
        public String firstName;
        public String secondName;
        public String thirdName;
        public String phoneNumber;
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

        public Account(String id, String firstName, String secondName, String thirdName, String phoneNumber,
                String password, double balance, String role) {
            this.id = id;
            this.firstName = firstName;
            this.secondName = secondName;
            this.thirdName = thirdName;
            this.phoneNumber = phoneNumber;
            this.name = firstName + " " + secondName + " " + thirdName;
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
                + "first_name VARCHAR(50), "
                + "second_name VARCHAR(50), "
                + "third_name VARCHAR(50), "
                + "phone_number VARCHAR(20), "
                + "password VARCHAR(100) NOT NULL, "
                + "balance DOUBLE DEFAULT 0.0, "
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

            // Add new columns if they don't exist
            String[] newCols = {
                    "ALTER TABLE users ADD COLUMN first_name VARCHAR(50)",
                    "ALTER TABLE users ADD COLUMN second_name VARCHAR(50)",
                    "ALTER TABLE users ADD COLUMN third_name VARCHAR(50)",
                    "ALTER TABLE users ADD COLUMN phone_number VARCHAR(20)",
                    "ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'user'"
            };
            for (String colSql : newCols) {
                try {
                    stmt.execute(colSql);
                    System.out.println("Database Migration: Executed " + colSql);
                } catch (SQLException e) {
                    // Column already exists - common in XAMPP environments
                }
            }

            // MIGRATION: Change balance from INT to DOUBLE if needed
            try {
                stmt.execute("ALTER TABLE users MODIFY COLUMN balance DOUBLE DEFAULT 0.0");
                System.out.println("Database Migration: Migrated balance column to DOUBLE");
            } catch (SQLException e) {
                System.err.println("Migration Note (Balance): " + e.getMessage());
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

    public synchronized String createAccountExtended(String id, String fName, String sName, String tName, String phone,
            String password, double initialBalance,
            String role) {
        if (conn == null)
            return "DATABASE_CONNECTION_ERROR";

        String name = fName + " " + sName + " " + tName;
        String sql = "INSERT INTO users (id, name, first_name, second_name, third_name, phone_number, password, balance, role) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, fName);
            pstmt.setString(4, sName);
            pstmt.setString(5, tName);
            pstmt.setString(6, phone);
            pstmt.setString(7, PasswordUtils.hash(password));
            pstmt.setDouble(8, initialBalance);
            pstmt.setString(9, role);
            pstmt.executeUpdate();
            return "OK";
        } catch (SQLException e) {
            System.err.println("Create Account Error: " + e.getMessage());
            if (e.getErrorCode() == 1062) { // Duplicate entry
                return "FAIL:EXISTS";
            }
            return "FAIL:SQL_ERROR:" + e.getMessage();
        }
    }

    public synchronized boolean createAccount(String id, String fName, String sName, String tName, String phone,
            String password, double initialBalance,
            String role) {
        return "OK".equals(createAccountExtended(id, fName, sName, tName, phone, password, initialBalance, role));
    }

    // Compat method for legacy replication or admin
    public synchronized boolean createAccount(String id, String name, String password, double initialBalance,
            String role) {
        String[] parts = name.split(" ");
        String fName = parts.length > 0 ? parts[0] : "";
        String sName = parts.length > 1 ? parts[1] : "";
        String tName = parts.length > 2 ? parts[2] : "";
        return createAccount(id, fName, sName, tName, "000", password, initialBalance, role);
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
                Account acc = new Account(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getDouble("balance"),
                        rs.getString("role"));
                acc.firstName = rs.getString("first_name");
                acc.secondName = rs.getString("second_name");
                acc.thirdName = rs.getString("third_name");
                acc.phoneNumber = rs.getString("phone_number");
                return acc;
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
                        .append(rs.getString("first_name")).append(":")
                        .append(rs.getString("second_name")).append(":")
                        .append(rs.getString("third_name")).append(":")
                        .append(rs.getString("phone_number")).append(":")
                        .append(rs.getString("password")).append(":")
                        .append(rs.getDouble("balance")).append(":")
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
    public synchronized void upsertAccount(String id, String fName, String sName, String tName, String phone,
            String password, double balance, String role) {
        if (conn == null)
            return;

        if (accountExists(id)) {
            String sql = "UPDATE users SET name=?, first_name=?, second_name=?, third_name=?, phone_number=?, password=?, balance=?, role=? WHERE id=?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fName + " " + sName + " " + tName);
                pstmt.setString(2, fName);
                pstmt.setString(3, sName);
                pstmt.setString(4, tName);
                pstmt.setString(5, phone);
                pstmt.setString(6, password);
                pstmt.setDouble(7, balance);
                pstmt.setString(8, role);
                pstmt.setString(9, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createAccount(id, fName, sName, tName, phone, password, balance, role);
        }
    }

    // Compat upsert
    public synchronized void upsertAccount(String id, String name, String password, double balance, String role) {
        String[] parts = name.split(" ");
        String fName = parts.length > 0 ? parts[0] : "";
        String sName = parts.length > 1 ? parts[1] : "";
        String tName = parts.length > 2 ? parts[2] : "";
        upsertAccount(id, fName, sName, tName, "000", password, balance, role);
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
                Account acc = new Account(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getDouble("balance"),
                        rs.getString("role"));
                acc.firstName = rs.getString("first_name");
                acc.secondName = rs.getString("second_name");
                acc.thirdName = rs.getString("third_name");
                acc.phoneNumber = rs.getString("phone_number");
                list.add(acc);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean verifyForgetDetails(String id, String fName, String sName, String tName, String phone) {
        if (conn == null)
            return false;
        String sql = "SELECT * FROM users WHERE id = ? AND first_name = ? AND second_name = ? AND third_name = ? AND phone_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, fName);
            pstmt.setString(3, sName);
            pstmt.setString(4, tName);
            pstmt.setString(5, phone);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean updatePassword(String id, String newPassword) {
        if (conn == null)
            return false;
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, PasswordUtils.hash(newPassword));
            pstmt.setString(2, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean updatePasswordWithHash(String id, String passHash) {
        if (conn == null)
            return false;
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passHash);
            pstmt.setString(2, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
