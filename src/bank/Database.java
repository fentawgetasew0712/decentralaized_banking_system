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

        public Account(String id, String name, String phoneNumber, String password, double balance, String role) {
            this.id = id;
            this.name = name;
            this.phoneNumber = phoneNumber;
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

        // 1. Initial Table Creation with LATEST schema
        String sqlUsers = "CREATE TABLE IF NOT EXISTS users ("
                + "id VARCHAR(30) PRIMARY KEY, "
                + "name VARCHAR(100) NOT NULL, "
                + "phone_number VARCHAR(20), "
                + "password VARCHAR(100) NOT NULL, "
                + "balance DOUBLE DEFAULT 0.0, "
                + "role VARCHAR(20) DEFAULT 'user')";

        String sqlTrans = "CREATE TABLE IF NOT EXISTS transactions ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "type VARCHAR(20), "
                + "user_id VARCHAR(30), "
                + "amount VARCHAR(20), "
                + "target_id VARCHAR(30), "
                + "node_id INT, "
                + "lamport_clock INT DEFAULT 0)";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlTrans);

            // 2. Automated Schema Evolution (Ensuring columns exist for existing databases)
            ensureColumnExists("users", "phone_number", "VARCHAR(20)");
            ensureColumnExists("users", "role", "VARCHAR(20) DEFAULT 'user'");
            ensureColumnExists("users", "balance", "DOUBLE DEFAULT 0.0");
            ensureColumnExists("transactions", "lamport_clock", "INT DEFAULT 0");

            // 3. Fix data types if they were legacy (INT -> DOUBLE)
            stmt.executeUpdate("ALTER TABLE users MODIFY COLUMN balance DOUBLE DEFAULT 0.0");
            stmt.executeUpdate("UPDATE users SET role = 'user' WHERE role IS NULL");

            // 4. Cleanup old data
            stmt.executeUpdate("DELETE FROM users WHERE id = '000000000000'");

            // 5. Default Admin Initialization
            String checkAdmin = "SELECT * FROM users WHERE id = 'admin'";
            ResultSet rs = stmt.executeQuery(checkAdmin);
            if (!rs.next()) {
                String insertAdmin = "INSERT INTO users (id, name, password, balance, role) VALUES ('admin', 'System Admin', '"
                        + PasswordUtils.hash("admin123") + "', 0, 'admin')";
                stmt.executeUpdate(insertAdmin);
                System.out.println("Database: Default admin account created (admin/admin123)");
            } else {
                stmt.executeUpdate("UPDATE users SET role = 'admin' WHERE id = 'admin'");
            }
        } catch (SQLException e) {
            System.err.println("Init DB Error: " + e.getMessage());
        }
    }

    /**
     * Helper to ensure a column exists in a table.
     * Makes the system self-contained and plug-and-play.
     */
    private void ensureColumnExists(String tableName, String columnName, String definition) {
        String checkSql = "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, dbName);
            pstmt.setString(2, tableName);
            pstmt.setString(3, columnName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                // Column missing, add it
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
                    System.out.println("Database Migration: Added missing column [" + columnName + "] to table ["
                            + tableName + "]");
                }
            }
        } catch (SQLException e) {
            // Log but don't fail, might be permission issue or already existing
        }
    }

    public synchronized String createAccountExtended(String id, String name, String phone,
            String password, double initialBalance,
            String role) {
        if (conn == null)
            return "DATABASE_CONNECTION_ERROR";

        String sql = "INSERT INTO users (id, name, phone_number, password, balance, role) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, phone);
            pstmt.setString(4, PasswordUtils.hash(password));
            pstmt.setDouble(5, initialBalance);
            pstmt.setString(6, role);
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

    public synchronized boolean createAccount(String id, String name, String phone,
            String password, double initialBalance,
            String role) {
        return "OK".equals(createAccountExtended(id, name, phone, password, initialBalance, role));
    }

    // Compat method for legacy replication or admin
    public synchronized boolean createAccount(String id, String name, String password, double initialBalance,
            String role) {
        return createAccount(id, name, "000", password, initialBalance, role);
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

    public String getRole(String id) {
        Account acc = getAccount(id);
        return (acc != null) ? acc.role : null;
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
                // Using ~ as delimiter to avoid collision with names/other strings
                sb.append(rs.getString("id")).append("~")
                        .append(rs.getString("name")).append("~")
                        .append(rs.getString("phone_number")).append("~")
                        .append(rs.getString("password")).append("~")
                        .append(rs.getDouble("balance")).append("~")
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
    public synchronized void upsertAccount(String id, String name, String phone,
            String password, double balance, String role) {
        if (conn == null)
            return;

        if (accountExists(id)) {
            String sql = "UPDATE users SET name=?, phone_number=?, password=?, balance=?, role=? WHERE id=?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, phone);
                pstmt.setString(3, password);
                pstmt.setDouble(4, balance);
                pstmt.setString(5, role);
                pstmt.setString(6, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createAccount(id, name, phone, password, balance, role);
        }
    }

    // Compat upsert
    public synchronized void upsertAccount(String id, String name, String password, double balance, String role) {
        upsertAccount(id, name, "000", password, balance, role);
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
        public int lamportClock;

        public Transaction(int id, String timestamp, String type, String userId, String amount, String targetId,
                int nodeId, int lamportClock) {
            this.id = id;
            this.timestamp = timestamp;
            this.type = type;
            this.userId = userId;
            this.amount = amount;
            this.targetId = targetId;
            this.nodeId = nodeId;
            this.lamportClock = lamportClock;
        }
    }

    public synchronized String logTransaction(String type, String userId, String amount, String targetId,
            int lamportClock) {
        if (conn == null)
            return null;
        String sql = "INSERT INTO transactions (type, user_id, amount, target_id, node_id, lamport_clock) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, type);
            pstmt.setString(2, userId);
            pstmt.setString(3, amount);
            pstmt.setString(4, targetId != null ? targetId : "");
            pstmt.setInt(5, nodeId);
            pstmt.setInt(6, lamportClock);
            pstmt.executeUpdate();

            // Fetch the generated timestamp to return it for replication
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int lastId = rs.getInt(1);
                    try (Statement stmt = conn.createStatement();
                            ResultSet rsTime = stmt
                                    .executeQuery("SELECT timestamp FROM transactions WHERE id = " + lastId)) {
                        if (rsTime.next()) {
                            String ts = rsTime.getString("timestamp");
                            System.out.println("📝 Database: Logged " + type + " for " + userId + " at " + ts);
                            return ts;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Log Error: " + e.getMessage());
        }
        return null;
    }

    public java.util.List<Transaction> getAllTransactions() {
        java.util.List<Transaction> list = new java.util.ArrayList<>();
        if (conn == null)
            return list;
        String sql = "SELECT * FROM transactions ORDER BY id DESC LIMIT 10000"; // Increased limit for better sync
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
                        rs.getInt("node_id"),
                        rs.getInt("lamport_clock")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Check if a transaction already exists to avoid duplicates during sync.
     * Uses a combination of timestamp, user, type and lamport clock.
     */
    public boolean transactionExists(String timestamp, String userId, String type, int lamportClock) {
        if (conn == null)
            return false;
        String sql = "SELECT id FROM transactions WHERE timestamp = ? AND user_id = ? AND type = ? AND lamport_clock = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timestamp);
            pstmt.setString(2, userId);
            pstmt.setString(3, type);
            pstmt.setInt(4, lamportClock);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Import a transaction from a peer during sync.
     */
    public synchronized void importTransaction(String timestamp, String type, String userId, String amount,
            String targetId, int nodeId, int lamportClock) {
        if (conn == null)
            return;

        if (transactionExists(timestamp, userId, type, lamportClock)) {
            return;
        }

        String sql = "INSERT INTO transactions (timestamp, type, user_id, amount, target_id, node_id, lamport_clock) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timestamp);
            pstmt.setString(2, type);
            pstmt.setString(3, userId);
            pstmt.setString(4, amount);
            pstmt.setString(5, targetId != null ? targetId : "");
            pstmt.setInt(6, nodeId);
            pstmt.setInt(7, lamportClock);
            pstmt.executeUpdate();
            System.out.println("📥 Database: Imported transaction " + type + " for " + userId + " at " + timestamp);
        } catch (SQLException e) {
            System.err.println("Import Transaction Error: " + e.getMessage());
        }
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
                acc.phoneNumber = rs.getString("phone_number");
                list.add(acc);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean verifyForgetDetails(String id, String fullName, String phone) {
        if (conn == null)
            return false;
        String sql = "SELECT * FROM users WHERE id = ? AND name = ? AND phone_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, fullName);
            pstmt.setString(3, phone);
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
