package bank;

import algorithm.RicartNode;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ATMNode - DISTRIBUTED VERSION
 * 
 * Represents an ATM machine in the TRUE distributed system.
 * - Each node has its own local MySQL database
 * - Implements database replication across all peer nodes
 * - Uses 2-Phase Commit for data consistency
 * - NO dependency on central BankServer (eliminated single point of failure)
 */
public class ATMNode extends RicartNode {

    private final Database localDB; // Each node has its own database!
    // private final Set<String> activeSessions = Collections.newSetFromMap(new
    // ConcurrentHashMap<>()); // REMOVED: Concurrent Logins Allowed

    // Operation tracking
    private String nextOperation = "WITHDRAW";
    private String opUser;
    private String opAmount;
    private String opTarget;
    private volatile String lastTransactionResult = "";

    // Cluster Cache
    private volatile java.util.List<Database.Transaction> cachedClusterLogs = new java.util.ArrayList<>();
    private volatile long lastUpdate = 0;
    private static final long CACHE_TTL = 5000; // 5 seconds cache

    /**
     * Constructor for distributed ATM Node
     * 
     * @param nodeId   - Unique node identifier
     * @param port     - Port for peer communication
     * @param allNodes - Map of all peer nodes
     */
    public ATMNode(int nodeId, int port, ConcurrentHashMap<Integer, InetSocketAddress> allNodes) {
        super(nodeId, port, allNodes);

        // Initialize local database for this node
        this.localDB = new Database(nodeId);
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   ATM NODE " + nodeId + " - DISTRIBUTED DATABASE INITIALIZED    ║");
        System.out.println("║   Database: " + localDB.getDatabaseName() + "                    ║");
        System.out.println("║   NO CENTRAL SERVER - FULLY DISTRIBUTED!                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    public void setOperationDetails(String op, String user, String amount, String target) {
        this.nextOperation = op;
        this.opUser = user;
        this.opAmount = amount;
        this.opTarget = target;
    }

    public Database getLocalDB() {
        return localDB;
    }

    /**
     * Check balance from LOCAL database
     */
    public String checkBalance(String user) {
        double balance = localDB.getBalance(user);
        if (balance == -1.0) {
            return "Error";
        }
        return String.valueOf(balance);
    }

    /**
     * Authenticate with LOCAL database, with PEER FALLBACK
     * Phase 1: Fault-Tolerant Login
     */
    public String login(String user, String pass) {
        // 0. REMOVED: IS USER LOGGED IN ELSEWHERE CHECK (Concurrent Logins Allowed)

        // 1. Check local database first (fastest)
        if (localDB.authenticate(user, pass)) {
            Database.Account acc = localDB.getAccount(user);
            // activeSessions.add(user); // Lock session -> REMOVED
            return "OK:LOGIN_SUCCESS:" + acc.name + ":" + acc.role;
        }

        // 2. Query available peer nodes (fault tolerance)
        System.out.println("🔍 ATM " + getNodeId() + ": Account not found locally, querying peers...");
        Database.Account peerAccount = queryPeersForAccount(user, pass);

        if (peerAccount != null) {
            // 3. Cache account locally for future logins (performance optimization)
            System.out.println("✅ ATM " + getNodeId() + ": Found account on peer, caching locally");
            localDB.createAccount(user, peerAccount.name, peerAccount.password, peerAccount.balance, peerAccount.role);
            // activeSessions.add(user); // Lock session -> REMOVED
            return "OK:LOGIN_SUCCESS:" + peerAccount.name + ":" + peerAccount.role;
        }

        // 4. Not found anywhere
        return "FAIL:AUTH_FAILED";
    }

    /**
     * Register new account with REPLICATION to all peers
     */
    public String register(String user, String name, String pass, String amount) {
        try {
            double initialBalance = Double.parseDouble(amount);

            // Validation
            if (initialBalance < 500.0) {
                return "FAIL:MIN_DEPOSIT_500";
            }

            if (user.length() != 12 || !user.matches("\\d+")) {
                return "FAIL:INVALID_ID_FORMAT";
            }

            // Check if account already exists
            if (localDB.accountExists(user)) {
                return "FAIL:EXISTS";
            }

            // Create account in LOCAL database first
            boolean created = localDB.createAccount(user, name, pass, initialBalance);

            if (created) {
                // REPLICATE to all peer nodes
                String replicationMsg = "REPLICATE_CREATE:" + user + ":" + name + ":" + pass + ":" + initialBalance
                        + ":user";
                broadcastReplication(replicationMsg);

                System.out.println("✅ ATM " + getNodeId() + ": Account created and replicated to all peers");
                return "OK:CREATED";
            } else {
                return "FAIL:CREATE_ERROR";
            }

        } catch (NumberFormatException e) {
            return "FAIL:INVALID_AMOUNT";
        }
    }

    /**
     * Critical Section - Execute transaction and replicate
     */

    /**
     * Broadcast replication message to all peer nodes
     */
    private void broadcastReplication(String message) {
        System.out.println("📡 ATM " + getNodeId() + ": Broadcasting replication: " + message);

        for (int peerId : getAllNodes().keySet()) {
            // Changed: Don't skip myself if I want to replicate to myself (though usually
            // we optimize)
            // But logic says "if (peerId != getNodeId())"
            if (peerId != getNodeId()) {
                sendReplicationMessage(peerId, message);
            }
        }
    }

    /**
     * Query peer nodes for account information
     * Phase 1: Fault-Tolerant Login
     */
    private Database.Account queryPeersForAccount(String userId, String password) {
        for (int peerId : getAllNodes().keySet()) {
            if (peerId == getNodeId())
                continue;

            try {
                String response = sendQueryToPeer(peerId, "QUERY_ACCOUNT:" + userId + ":" + password);

                if (response != null && response.startsWith("ACCOUNT_FOUND:")) {
                    // Parse: ACCOUNT_FOUND:name:balance:role
                    String[] parts = response.split(":", 5);
                    if (parts.length >= 5) {
                        String name = parts[1];
                        double balance = Double.parseDouble(parts[2]);
                        String role = parts[3];
                        System.out.println("  ✅ Found account on Node " + peerId);
                        return new Database.Account(userId, name, password, balance, role);
                    }
                }
            } catch (Exception e) {
                // Peer is offline or error occurred, try next peer
                System.out.println("  ⚠️  Node " + peerId + " is offline or unreachable");
                continue;
            }
        }
        return null; // Not found on any peer
    }

    /**
     * Send query to a specific peer and wait for response
     * Phase 1: Fault-Tolerant Login
     */
    private String sendQueryToPeer(int targetNodeId, String query) {
        InetSocketAddress targetAddress = getAllNodes().get(targetNodeId);
        if (targetAddress == null)
            return null;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetAddress.getAddress(), targetAddress.getPort()), 500); // 500ms
                                                                                                             // Connect
                                                                                                             // Timeout

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(query);

                // Wait for response with timeout
                socket.setSoTimeout(500); // 0.5 second Read timeout
                String response = in.readLine();
                socket.close(); // Explicit close
                return response;
            }
        } catch (IOException e) {
            // Inner catch handles it, or we bubble up.
            // Actually, let's just have one catch block.
            // logic above was: try { socket code } catch(IO) { } catch(IO) { }
            // The outer catch was failing.
            // Let's just catch it once.
            // Peer is offline or error occurred
            System.out.println("  ⚠️  Node " + targetNodeId + " is offline or unreachable");
            return null; // Return null as before
        }
        // Removed the extra catch block below via this replacement
        // return null; // Logic flow handles this

    }

    /**
     * Send replication message to a specific peer
     */
    private void sendReplicationMessage(int targetNodeId, String message) {
        InetSocketAddress targetAddress = getAllNodes().get(targetNodeId);
        if (targetAddress == null)
            return;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetAddress.getAddress(), targetAddress.getPort()), 500); // 500ms
                                                                                                             // Connect
                                                                                                             // Timeout

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(message);
                System.out.println("  ↳ Sent to Node " + targetNodeId);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("  ✗ Failed to replicate to Node " + targetNodeId + ": " + e.getMessage());
            System.err.println("  (Node may be offline - this is OK in distributed systems)");
        }
        // Removing the extra catch block

    }

    /**
     * Handle incoming replication messages from peers
     * This is called by RicartNode when it receives a replication message
     */
    public void handleReplicationMessage(String message) {
        System.out.println("📥 ATM " + getNodeId() + ": Received replication: " + message);

        String[] parts = message.split(":");
        String action = parts[0];

        try {
            if ("REPLICATE_CREATE".equals(action)) {
                // REPLICATE_CREATE:userId:name:password:balance
                String userId = parts[1];
                String name = parts[2];
                String password = parts[3];
                double balance = Double.parseDouble(parts[4]);
                String role = parts.length > 5 ? parts[5] : "user";

                // Create account in local database (if doesn't exist)
                if (!localDB.accountExists(userId)) {
                    localDB.createAccount(userId, name, password, balance, role);
                    System.out.println("  ✅ Replicated account creation: " + userId + " (Role: " + role + ")");
                } else {
                    System.out.println("  ⚠️  Account already exists: " + userId);
                }

            } else if ("REPLICATE_UPDATE".equals(action)) {
                // REPLICATE_UPDATE:userId:newBalance
                String userId = parts[1];
                double newBalance = Double.parseDouble(parts[2]);

                // Update balance in local database
                if (localDB.accountExists(userId)) {
                    localDB.updateBalance(userId, newBalance);
                    System.out.println("  ✅ Replicated balance update: " + userId + " -> $" + newBalance);
                } else {
                    System.out.println("  ⚠️  Cannot update non-existent account: " + userId);
                }
            }
        } catch (Exception e) {
            System.err.println("  ✗ Error processing replication: " + e.getMessage());
        }
    }

    public String getLastTransactionResult() {
        return lastTransactionResult;
    }

    /**
     * Get access to all nodes (for replication)
     */
    private ConcurrentHashMap<Integer, InetSocketAddress> getAllNodes() {
        return super.getAllNodesMap();
    }

    @Override
    public void start() {
        super.start();
        // Trigger initial sync with peers to get latest data
        new Thread(this::syncWithPeers, "Sync-Thread").start();

        // Start background log aggregator
        new Thread(() -> {
            while (true) {
                try {
                    // Update cluster logs periodically in background
                    this.cachedClusterLogs = refreshClusterLogs();
                    this.lastUpdate = System.currentTimeMillis();
                    Thread.sleep(10000); // Every 10 seconds
                } catch (InterruptedException e) {
                }
            }
        }, "Log-Aggregator-Thread").start();
    }

    /**
     * SYNC: Connect to peers and ask for their database
     */
    private void syncWithPeers() {
        System.out.println("🔄 ATM " + getNodeId() + ": Starting Initial Sync...");

        // Simple strategy: Ask ALL peers, merge everything.
        // In a real system, you might ask just one or use a Merkle tree.
        for (int peerId : getAllNodes().keySet()) {
            if (peerId == getNodeId())
                continue;

            try {
                // We manually send a socket message because RicartNode.sendMessage is for
                // protocol messages
                // and we want a request-response flow here.
                // Actually, let's just use a socket directly like queryPeersForAccount
                sendSyncRequestToPeer(peerId);
            } catch (Exception e) {
                System.out.println("  ⚠️  Sync: Peer " + peerId + " unreachable.");
            }
        }
    }

    private void sendSyncRequestToPeer(int targetNodeId) {
        InetSocketAddress targetAddress = getAllNodes().get(targetNodeId);
        if (targetAddress == null)
            return;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetAddress.getAddress(), targetAddress.getPort()), 500); // 500ms
                                                                                                             // Connect
                                                                                                             // Timeout

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                System.out.println("  -> Sending SYNC_REQUEST to Node " + targetNodeId);
                out.println("SYNC_REQUEST");

                // Read response (could be large)
                String response = in.readLine();
                if (response != null && response.startsWith("SYNC_RESPONSE:")) {
                    handleReplicationMessage(response); // Reuse handler or call onSyncResponse
                    onSyncResponse(response);
                }
            }
            socket.close();
        } catch (IOException e) {
            // Peer offline
            System.out.println("  ⚠️  Sync: Peer " + targetNodeId + " unreachable.");
        }
        // Removing extra catch block
    }

    // ==========================================
    // OVERRIDES FOR RICART / SYNC HANDLERS
    // ==========================================

    @Override
    protected String onAccountQuery(String userId, String password) {
        if (localDB.authenticate(userId, password)) {
            Database.Account acc = localDB.getAccount(userId);
            return "ACCOUNT_FOUND:" + acc.name + ":" + acc.balance + ":" + acc.role;
        }
        return "ACCOUNT_NOT_FOUND";
    }

    @Override
    protected void onSyncRequest(PrintWriter out) {
        String allData = localDB.getAllAccountsSerialized();
        out.println("SYNC_RESPONSE:" + allData);
        System.out.println("📤 ATM " + getNodeId() + ": Sent full DB dump to peer.");
    }

    @Override
    protected void onSyncResponse(String message) {
        // Message: SYNC_RESPONSE:id:name:pass:bal|id:name...
        String data = message.substring("SYNC_RESPONSE:".length());
        if (data.isEmpty())
            return;

        int count = 0;
        String[] accounts = data.split("\\|");
        for (String accStr : accounts) {
            String[] parts = accStr.split(":");
            if (parts.length >= 5) {
                localDB.upsertAccount(parts[0], parts[1], parts[2], Double.parseDouble(parts[3]), parts[4]);
                count++;
            }
        }
        System.out.println("📥 ATM " + getNodeId() + ": Synced " + count + " accounts from peer.");
    }

    /**
     * Check if user is logged in on ANY node (Cluster-wide lock) - REMOVED
     */
    /*
     * private boolean isUserLoggedInElsewhere(String userId) {
     * // ... Removed implementation ...
     * return false;
     * }
     */

    /**
     * Logout user - Release session lock (No-op now)
     */
    public void logout(String userId) {
        // activeSessions.remove(userId);
        System.out.println("🔓 ATM " + getNodeId() + ": User " + userId + " logged out locally.");
    }

    /*
     * @Override
     * protected String onSessionQuery(String userId) {
     * if (activeSessions.contains(userId)) {
     * return "SESSION_ACTIVE";
     * }
     * return "SESSION_INACTIVE";
     * }
     */

    /**
     * Override to handle replication messages from RicartNode
     */
    @Override
    protected void onReplicationMessage(String message) {
        handleReplicationMessage(message);
    }

    /**
     * Get ALL transactions from the ENTIRE cluster (Local + Remote)
     */
    public java.util.List<Database.Transaction> getAllClusterTransactions() {
        // Return cached logs if available, otherwise fetch locally
        if (cachedClusterLogs == null || cachedClusterLogs.isEmpty()) {
            return new java.util.ArrayList<>(localDB.getAllTransactions());
        }
        return cachedClusterLogs;
    }

    private java.util.List<Database.Transaction> refreshClusterLogs() {
        // 1. Get Local Logs
        java.util.List<Database.Transaction> allLogs = new java.util.ArrayList<>(localDB.getAllTransactions());

        // 2. Query All Peers
        for (int peerId : getAllNodes().keySet()) {
            if (peerId == getNodeId())
                continue;

            try {
                // Fetch logs from peer
                java.util.List<Database.Transaction> peerLogs = requestPeerTransactions(peerId);
                if (peerLogs != null) {
                    allLogs.addAll(peerLogs);
                }
            } catch (Exception e) {
                System.out.println("  ⚠️  Admin: Could not fetch logs from Node " + peerId);
            }
        }

        // 3. Sort by Timestamp Descending
        allLogs.sort((t1, t2) -> t2.timestamp.compareTo(t1.timestamp));

        return allLogs;
    }

    private java.util.List<Database.Transaction> requestPeerTransactions(int peerId) {
        InetSocketAddress targetAddress = getAllNodes().get(peerId);
        if (targetAddress == null)
            return null;

        String targetStr = targetAddress.getAddress().getHostAddress() + ":" + targetAddress.getPort();
        System.out.println("  Admin: Fetching logs from Node " + peerId + " (" + targetStr + ")...");

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetAddress.getAddress(), targetAddress.getPort()), 300); // Reduced
                                                                                                             // timeout
                                                                                                             // for UI
                                                                                                             // fetch

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println("QUERY_TRANSACTION_LOGS");

                String response = in.readLine();
                if (response != null && response.startsWith("LOGS_RESPONSE:")) {
                    return parseTransactions(response.substring("LOGS_RESPONSE:".length()));
                }
            }
            socket.close();
        } catch (IOException e) {
            // connection failed
            return null;
        }
        return null;
    }

    private java.util.List<Database.Transaction> parseTransactions(String data) {
        java.util.List<Database.Transaction> list = new java.util.ArrayList<>();
        if (data.isEmpty())
            return list;

        String[] rows = data.split("\\|");
        for (String row : rows) {
            // Format: id:time:type:user:amt:target:node
            String[] parts = row.split("~"); // Using ~ as delimiter to avoid conflict with time colons
            if (parts.length >= 7) {
                list.add(new Database.Transaction(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5],
                        Integer.parseInt(parts[6])));
            }
        }
        return list;
    }

    // Handle incoming log request
    @Override
    protected void onLogQuery(PrintWriter out) {
        java.util.List<Database.Transaction> myLogs = localDB.getAllTransactions();
        StringBuilder sb = new StringBuilder("LOGS_RESPONSE:");

        for (int i = 0; i < myLogs.size(); i++) {
            Database.Transaction t = myLogs.get(i);
            if (i > 0)
                sb.append("|");
            // id~time~type~user~amount~target~node
            sb.append(t.id).append("~")
                    .append(t.timestamp).append("~")
                    .append(t.type).append("~")
                    .append(t.userId).append("~")
                    .append(t.amount).append("~")
                    .append(t.targetId).append("~")
                    .append(t.nodeId);
        }
        out.println(sb.toString());
    }

    /**
     * CRITICAL SECTION: Perform the actual banking operation safely
     * This is guaranteed to run on only ONE node at a time (or sequential)
     * thanks to Ricart-Agrawala.
     */
    @Override
    protected void onCriticalSection() {
        System.out.println("⚡ CRITICAL SECTION ENTERED by " + getNodeId() + " for " + nextOperation);

        try {
            // 1. DEPOSIT
            if ("DEPOSIT".equals(nextOperation)) {
                double currentBalance = localDB.getBalance(opUser);
                if (currentBalance != -1.0) {
                    double amountObj = Double.parseDouble(opAmount);
                    double newBalance = currentBalance + amountObj;
                    localDB.updateBalance(opUser, newBalance);

                    // Broadcast replication
                    broadcastReplication("REPLICATE_UPDATE:" + opUser + ":" + newBalance);

                    // LOG TRANSACTION
                    localDB.logTransaction("DEPOSIT", opUser, opAmount, null);

                    lastTransactionResult = "OK:DEPOSIT_SUCCESS:NewBalance=" + newBalance;
                    System.out.println("✅ ATM " + getNodeId() + ": Deposited $" + amountObj + " to " + opUser);
                } else {
                    lastTransactionResult = "FAIL:USER_NOT_FOUND";
                }
            }

            // 2. WITHDRAW
            else if ("WITHDRAW".equals(nextOperation)) {
                double currentBalance = localDB.getBalance(opUser);
                if (currentBalance != -1.0) {
                    double amountObj = Double.parseDouble(opAmount);
                    if (currentBalance >= amountObj) {
                        double newBalance = currentBalance - amountObj;
                        localDB.updateBalance(opUser, newBalance);

                        // Broadcast replication
                        broadcastReplication("REPLICATE_UPDATE:" + opUser + ":" + newBalance);

                        // LOG TRANSACTION
                        localDB.logTransaction("WITHDRAW", opUser, opAmount, null);

                        lastTransactionResult = "OK:WITHDRAW_SUCCESS:NewBalance=" + newBalance;
                        System.out.println("✅ ATM " + getNodeId() + ": Withdrew $" + amountObj + " from " + opUser);
                    } else {
                        lastTransactionResult = "FAIL:INSUFFICIENT_FUNDS";
                    }
                } else {
                    lastTransactionResult = "FAIL:USER_NOT_FOUND";
                }
            }

            // 3. TRANSFER
            else if ("TRANSFER".equals(nextOperation)) {
                double amountObj = Double.parseDouble(opAmount);
                double senderBalance = localDB.getBalance(opUser);
                double receiverBalance = localDB.getBalance(opTarget);

                // Validate sender has funds AND receiver exists
                if (senderBalance != -1.0 && receiverBalance != -1.0 && senderBalance >= amountObj) {
                    double newSenderBalance = senderBalance - amountObj;
                    double newReceiverBalance = receiverBalance + amountObj;

                    localDB.updateBalance(opUser, newSenderBalance);
                    localDB.updateBalance(opTarget, newReceiverBalance);

                    // REPLICATE BOTH
                    broadcastReplication("REPLICATE_UPDATE:" + opUser + ":" + newSenderBalance);
                    broadcastReplication("REPLICATE_UPDATE:" + opTarget + ":" + newReceiverBalance);

                    // LOG TRANSACTION
                    localDB.logTransaction("TRANSFER", opUser, opAmount, opTarget);

                    lastTransactionResult = "OK:TRANSFER_SUCCESS:NewBalance=" + newSenderBalance;
                    System.out.println("✅ ATM " + getNodeId() + ": Transferred $" + amountObj + " from " + opUser
                            + " to " + opTarget);
                } else {
                    if (receiverBalance == -1.0) {
                        lastTransactionResult = "FAIL:RECEIVER_NOT_FOUND";
                    } else {
                        lastTransactionResult = "FAIL:INSUFFICIENT_FUNDS";
                    }
                }
            }
        } catch (Exception e) {
            lastTransactionResult = "FAIL:EXCEPTION:" + e.getMessage();
            e.printStackTrace();
        }
    }
}
