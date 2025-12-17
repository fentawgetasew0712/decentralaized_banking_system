package algorithm;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RicartNode
 * 
 * Core implementation of the Ricart-Agrawala Distributed Mutual Exclusion
 * Algorithm.
 * - Handles the logic of REQUEST and REPLY messages.
 * - Uses Lamport Logical Clocks for ordering.
 * - Manages the Critical Section entry/exit.
 */
public class RicartNode {

    private final int nodeId;
    private final int port;
    private final ConcurrentHashMap<Integer, InetSocketAddress> allNodes; // <ID, IP:Port>
    private final int N; // Total number of nodes

    public int getNodeId() {
        return nodeId;
    }

    /**
     * Expose allNodes map to subclasses (for replication)
     */
    protected ConcurrentHashMap<Integer, InetSocketAddress> getAllNodesMap() {
        return allNodes;
    }

    // Lamport's Logical Clock
    private final AtomicInteger lamportClock = new AtomicInteger(0);
    private volatile int requestTimestamp = Integer.MAX_VALUE; // Timestamp of the current request

    // State Flags and Queues
    private volatile boolean requestingCS = false;
    private volatile String targetResource = null; // The resource we want to lock (e.g., "user1")
    private volatile int repliesReceived = 0;
    private final CopyOnWriteArrayList<Integer> replyDeferredQueue = new CopyOnWriteArrayList<>();

    // Message Types
    private static final String MSG_REQUEST = "REQUEST";
    private static final String MSG_REPLY = "REPLY";

    public RicartNode(int nodeId, int port, ConcurrentHashMap<Integer, InetSocketAddress> allNodes) {
        this.nodeId = nodeId;
        this.port = port;
        this.allNodes = allNodes;
        this.N = allNodes.size();
    }

    public void start() {
        // Start the server thread to listen for incoming requests/replies
        new Thread(this::startServer, "Node-" + nodeId + "-Listener").start();

        // Start a thread to periodically request the critical section (for testing)
        // COMMENTED OUT FOR PRODUCTION USE:
        // new Thread(this::periodicallyRequestCS, "Node-" + nodeId +
        // "-Requester").start();
    }

    // =================================================================
    // 1. LAMPORT CLOCK UTILITY
    // =================================================================

    // Rule 1: Increment before sending a message
    private int tickAndGet() {
        return lamportClock.incrementAndGet();
    }

    // Rule 2: Update based on received message
    private void updateClock(int receivedTimestamp) {
        lamportClock.set(Math.max(lamportClock.get(), receivedTimestamp) + 1);
    }

    // =================================================================
    // 2. SERVER SIDE: LISTENING FOR INCOMING MESSAGES
    // =================================================================

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Node " + nodeId + " started listener on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleIncomingMessage(clientSocket), "Node-" + nodeId + "-Handler").start();
            }
        } catch (IOException e) {
            // Server socket closed on exit
        }
    }

    private void handleIncomingMessage(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String message = in.readLine();
            if (message != null) {
                // Phase 1: Handle QUERY_ACCOUNT messages for fault-tolerant login
                if (message.startsWith("QUERY_ACCOUNT:")) {
                    String[] parts = message.split(":");
                    if (parts.length >= 3) {
                        String userId = parts[1];
                        String password = parts[2];
                        String response = onAccountQuery(userId, password);
                        out.println(response);
                    }
                    return;
                }

                // Phase 2: Handle QUERY_SESSION messages for Session Locking
                if (message.startsWith("QUERY_SESSION:")) {
                    String[] parts = message.split(":");
                    if (parts.length >= 2) {
                        String userId = parts[1];
                        String response = onSessionQuery(userId);
                        out.println(response);
                    }
                    return;
                }

                // Check if this is a replication message (doesn't have timestamp format)
                if (message.startsWith("REPLICATE_")) {
                    // Route to subclass for handling (ATMNode)
                    onReplicationMessage(message);
                    return;
                }

                // Check for SYNC messages (New Feature: Sync-on-Connect)
                if (message.startsWith("SYNC_REQUEST")) {
                    onSyncRequest(out);
                    return;
                }

                if (message.startsWith("SYNC_RESPONSE:")) {
                    onSyncResponse(message);
                    return;
                }

                // Check for LOGS messages (New Feature: Admin Distributed Logs)
                if (message.startsWith("QUERY_TRANSACTION_LOGS")) {
                    onLogQuery(out);
                    return;
                }

                String[] parts = message.split(":");
                String msgType = parts[0];
                int receivedTimestamp = Integer.parseInt(parts[1]);
                int senderId = Integer.parseInt(parts[2]);

                // Update clock based on received message timestamp
                updateClock(receivedTimestamp);

                if (msgType.equals(MSG_REQUEST)) {
                    // REQUEST:Timestamp:NodeID:ResourceID
                    String requestedResource = (parts.length > 3) ? parts[3] : "GLOBAL";
                    handleRequest(receivedTimestamp, senderId, requestedResource);
                } else if (msgType.equals(MSG_REPLY)) {
                    handleReply(senderId);
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Handle connection reset or bad message format
            System.err.println("Node " + nodeId + " error handling message: " + e.getMessage());
        }
    }

    // =================================================================
    // 3. RICART-AGRAWALA CORE LOGIC
    // =================================================================

    private synchronized void handleRequest(int receivedTimestamp, int senderId, String requestedResource) {

        boolean replyImmediately = false;

        // Condition 1: Node is NOT requesting the CS.
        if (!requestingCS) {
            replyImmediately = true;
        }
        // Condition 2: Node IS requesting CS, but for a DIFFERENT resource.
        // (Granular Locking: I only care if you want the SAME thing I want).
        else if (targetResource != null && !targetResource.equals(requestedResource)) {
            System.out.println("Node " + nodeId + " DONT CARE about resource '" + requestedResource + "' (I want '"
                    + targetResource + "'). Replying.");
            replyImmediately = true;
        }
        // Condition 3: Node IS requesting CS for the SAME resource. Check Priority.
        else {
            if (receivedTimestamp < requestTimestamp ||
                    (receivedTimestamp == requestTimestamp && senderId < nodeId)) {

                replyImmediately = true;
            }
        }

        if (replyImmediately) {
            System.out.println("Node " + nodeId + " REPLYING immediately to " + senderId);
            sendReply(senderId);
        } else {
            // Defer the reply
            System.out.println(
                    "Node " + nodeId + " DEFERRING REPLY to " + senderId + " for resource " + requestedResource);
            replyDeferredQueue.add(senderId);
        }
    }

    private synchronized void handleReply(int senderId) {
        repliesReceived++;
        System.out.println("Node " + nodeId + " received REPLY from " + senderId +
                " (Total replies: " + repliesReceived + "/" + (N - 1) + ")");

        // Notify the waiting thread in requestAccess that a reply has arrived
        this.notifyAll();
    }

    private void enterCriticalSection() {
        System.out.println("\n*** Node " + nodeId + " GRANTED ACCESS TO CS ***");

        // 1. Execute the Critical Section
        onCriticalSection();

        // 2. Exit Critical Section
        exitCriticalSection();
    }

    private synchronized void exitCriticalSection() {
        requestingCS = false;
        repliesReceived = 0;
        requestTimestamp = Integer.MAX_VALUE;

        System.out.println("--- Node " + nodeId + " EXIT CS and PROCESSING DEFERRED REPLIES ("
                + replyDeferredQueue.size() + ") ---");

        // 3. Send REPLY to all deferred requests
        for (int deferredId : replyDeferredQueue) {
            sendReply(deferredId);
        }
        replyDeferredQueue.clear();
    }

    // =================================================================
    // 4. CLIENT SIDE: SENDING MESSAGES
    // =================================================================

    // Exposed for manual triggering (e.g. via ATMApp)
    public void requestAccess(String resourceId) {
        // Set state for request and get timestamp
        requestingCS = true;
        targetResource = resourceId;
        requestTimestamp = tickAndGet();
        repliesReceived = 0;

        System.out.println("\nNode " + nodeId + " SENDING REQUEST for [" + resourceId + "], Time: " + requestTimestamp);

        // Send request message to all other nodes
        // Format: REQUEST:Timestamp:NodeID:ResourceID
        String message = MSG_REQUEST + ":" + requestTimestamp + ":" + nodeId + ":" + resourceId;
        for (int targetId : allNodes.keySet()) {
            if (targetId != nodeId) {
                sendMessage(targetId, message);
            }
        }

        // TIMEOUT LOGIC: Wait for replies using wait/notify instead of sleep
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds timeout

        synchronized (this) {
            while (requestingCS && repliesReceived < (N - 1)) {
                long timeLeft = timeout - (System.currentTimeMillis() - startTime);
                if (timeLeft <= 0) {
                    System.err.println("!!! TIMEOUT WAITING FOR REPLIES (" + repliesReceived + "/" + (N - 1) + ") !!!");
                    System.err.println("!!! ASSUMING CRITICAL SECTION PERMISSION !!!");
                    break; // Force entry on timeout
                }

                try {
                    // Wait for handleReply to notify us, or until timeout
                    this.wait(timeLeft);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (requestingCS) {
            enterCriticalSection();
        }
    }

    private void sendReply(int targetId) {
        // Clock tick before sending the reply
        int currentClock = tickAndGet();

        String message = MSG_REPLY + ":" + currentClock + ":" + nodeId;
        sendMessage(targetId, message);
    }

    private void sendMessage(int targetId, String message) {
        InetSocketAddress targetAddress = allNodes.get(targetId);
        if (targetAddress == null)
            return;

        if (targetAddress.isUnresolved()) {
            System.err.println("Node " + nodeId + " WARNING: Cannot resolve address for Node " + targetId +
                    " (" + targetAddress.getHostName() + "). Skipping.");
            return;
        }

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetAddress.getAddress(), targetAddress.getPort()), 500); // 500ms
                                                                                                             // timeout

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(message);
            }
            socket.close();
        } catch (IOException e) {
            System.err.println("Node " + nodeId + " failed to communicate with Node " + targetId +
                    " (" + targetAddress + "). Node is assumed down.");
        }
    }

    // =================================================================
    // 5. TESTING LOOP
    // =================================================================

    @SuppressWarnings("unused")
    private void periodicallyRequestCS() {
        try {
            // Wait for all nodes to start up
            Thread.sleep(3000);

            // Loop to continuously compete for the lock (Just for demo/testing)
            for (int i = 0; i < 5; i++) {
                // Wait a random amount of time before requesting the CS again
                Thread.sleep((long) (Math.random() * 2000) + 1000);
                requestAccess("TEST_RESOURCE");

                // Wait for the process to enter/exit the CS before requesting again
                while (requestingCS) {
                    Thread.sleep(100);
                }
            }
            System.out.println("\nNode " + nodeId + " finished its scheduled requests.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Can be overridden by subclasses (e.g. for Banking System)
    protected void onCriticalSection() {
        System.out.println("Node " + nodeId + " is in Critical Section (Base Implementation)");
    }

    /**
     * Handle replication messages from peers
     * Can be overridden by subclasses (e.g. ATMNode)
     */
    protected void onReplicationMessage(String message) {
        // Default: do nothing
        System.out.println("Node " + nodeId + " received replication message (not handled): " + message);
    }

    /**
     * Handle session query from peers
     * Phase 2: Session Locking
     * Can be overridden by subclasses (e.g. ATMNode)
     */
    protected String onSessionQuery(String userId) {
        // Default: session inactive
        return "SESSION_INACTIVE";
    }

    /**
     * Handle account query from peers
     * Phase 1: Fault-Tolerant Login
     * Can be overridden by subclasses (e.g. ATMNode)
     */
    protected String onAccountQuery(String userId, String password) {
        // Default: return not found
        return "ACCOUNT_NOT_FOUND";
    }

    /**
     * Handle header-less SYNC_REQUEST. subclass should write response to out.
     */
    protected void onSyncRequest(PrintWriter out) {
        // Default: do nothing
    }

    /**
     * Handle SYNC_RESPONSE containing full DB dump
     */
    protected void onSyncResponse(String message) {
        // Default: do nothing
    }

    /**
     * Handle QUERY_TRANSACTION_LOGS. subclass should write response to out.
     */
    protected void onLogQuery(PrintWriter out) {
        // Default: do nothing
    }
}