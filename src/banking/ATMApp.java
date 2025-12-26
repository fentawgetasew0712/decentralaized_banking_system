package banking;

import bank.ATMNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ATMApp
 * 
 * The main application entry point for running an ATM Node.
 * - Loads configuration.
 * - Asks user for their Node ID.
 * - Starts the ATMNode.
 */
public class ATMApp {

    private static final String CONFIG_FILE = "config/nodes.properties";

    public static void main(String[] args) {
        System.out.println("=== DECENTRALIZED BANKING SYSTEM (Ricart-Agrawala) ===");

        // 1. Load Configuration
        Properties props = new Properties();
        try (InputStream input = ATMApp.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("ERROR: Could not find " + CONFIG_FILE);
                System.exit(1);
            }
            props.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 2. Parse Peer Nodes (NO MORE CENTRAL SERVER!)
        ConcurrentHashMap<Integer, InetSocketAddress> allNodes = new ConcurrentHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("#"))
                continue;

            try {
                int id = Integer.parseInt(key);
                String[] parts = props.getProperty(key).split(":");
                allNodes.put(id, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
            } catch (NumberFormatException e) {
                // Ignore non-numeric keys
            }
        }

        // 3. Ask User for Identity
        int myId = -1;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Available Node IDs: " + allNodes.keySet());
            while (true) {
                System.out.print("Enter YOUR Node ID (e.g. 1, 2, or 3): ");
                try {
                    String input = scanner.next();
                    myId = Integer.parseInt(input);
                    if (allNodes.containsKey(myId)) {
                        break;
                    } else {
                        System.out.println("Error: ID " + myId + " is not in the configuration. Try again.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: Invalid input. Please enter a NUMBER (1, 2, etc.), not an IP address.");
                }
            }
        }

        if (!allNodes.containsKey(myId)) {
            System.err.println("Error: Node ID " + myId + " not found in config.");
            return;
        }

        // 4. Start ATM Node (DISTRIBUTED VERSION - No BankServer needed!)
        int myPort = allNodes.get(myId).getPort();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   STARTING TRUE DISTRIBUTED BANKING SYSTEM                 ║");
        System.out.println("║   Node ID: " + myId + "                                              ║");
        System.out.println("║   Port: " + myPort + "                                            ║");
        System.out.println("║   NO CENTRAL SERVER - FULLY DISTRIBUTED!                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        ATMNode atmNode = new ATMNode(myId, myPort, allNodes);
        atmNode.start();

        // 6. Launch Web Interface
        try {
            // Port = 8080 + NodeID to allow running multiple on one machine (for testing)
            // Or just fixed 8080 if they are real separate IPs.
            // Let's use 8080 + myId to be safe during localhost testing.
            int webPort = 8080 + myId;
            ATMWebServer webServer = new ATMWebServer(atmNode, webPort);
            webServer.start();

            System.out.println("=================================================");
            System.out.println("BANKING PORTAL: http://localhost:" + webPort);
            System.out.println("(Admins and Users login here)");
            System.out.println("=================================================");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
