package bank;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BankServer
 * 
 * Acts as the 'Central Database' for the Decentralized Banking System.
 * - Stores multiple account balances.
 * - Logs all transactions.
 * - Processes TCP requests (BALANCE, WITHDRAW, DEPOSIT, TRANSFER).
 */
public class BankServer {

    private static final int PORT = 9000;
    private static Database db;
    private static final String LOG_FILE = "server_log.txt";

    public static void main(String[] args) {
        System.out.println("$$$ BANK SERVER STARTED ON PORT " + PORT + " $$$");
        logTransaction("SYSTEM", "Server Started");

        // Load Database
        db = new Database();

        // Helper: Check if accounts exist, else created by db.load()
        System.out.println("Database Ready.");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String command = in.readLine();
            if (command == null)
                return;

            String response = processCommand(command);
            out.println(response);
            System.out.println("Processed: " + command + " -> " + response);

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static synchronized String processCommand(String command) {
        String[] parts = command.split(" ");
        String action = parts[0];

        try {
            switch (action) {
                case "LOGIN":
                    // LOGIN <id> <password>
                    if (parts.length < 3)
                        return "FAIL:INVALID_ARGS";
                    String lUser = parts[1];
                    String lPass = parts[2];
                    if (db.authenticate(lUser, lPass)) {
                        String name = db.getAccount(lUser).name;
                        return "OK:LOGIN_SUCCESS:" + name;
                    } else {
                        return "FAIL:AUTH_FAILED";
                    }

                case "REGISTER":
                    System.out.println("DEBUG REGISTER Parts: " + java.util.Arrays.toString(parts));

                    // REGISTER <id> <password> <amount> <Full Name>
                    if (parts.length < 5)
                        return "FAIL:INVALID_ARGS";
                    String rUser = parts[1];
                    String rPass = parts[2];
                    double rAmount = 0;
                    try {
                        rAmount = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException e) {
                        return "FAIL:INVALID_AMOUNT";
                    }

                    if (rAmount <= 500) {
                        return "FAIL:MIN_DEPOSIT_500";
                    }

                    // Rejoin the rest as Name
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 4; i < parts.length; i++) {
                        nameBuilder.append(parts[i]).append(" ");
                    }
                    String rName = nameBuilder.toString().trim();

                    if (rUser.length() != 12 || !rUser.matches("\\d+")) {
                        return "FAIL:INVALID_ID_FORMAT";
                    }

                    if (db.createAccount(rUser, rName, rPass, rAmount)) {
                        logTransaction(rUser, "REGISTERED (Init: $" + rAmount + ")");
                        return "OK:CREATED";
                    } else {
                        return "FAIL:EXISTS";
                    }

                case "BALANCE":
                    String userId = parts[1];
                    double bal = db.getBalance(userId);
                    if (bal == -1.0)
                        return "FAIL:USER_NOT_FOUND";
                    return "OK:" + bal;

                case "WITHDRAW":
                    String wUser = parts[1];
                    double wAmount = Double.parseDouble(parts[2]);
                    double wBal = db.getBalance(wUser);

                    if (wBal == -1.0)
                        return "FAIL:USER_NOT_FOUND";
                    if (wBal >= wAmount) {
                        db.updateBalance(wUser, wBal - wAmount);
                        logTransaction(wUser, "WITHDRAW $" + wAmount);
                        return "OK:NEW_BALANCE=" + (wBal - wAmount);
                    } else {
                        return "FAIL:INSUFFICIENT_FUNDS";
                    }

                case "DEPOSIT":
                    String dUser = parts[1];
                    double dAmount = Double.parseDouble(parts[2]);
                    double dBal = db.getBalance(dUser);

                    if (dBal == -1.0)
                        return "FAIL:USER_NOT_FOUND";

                    db.updateBalance(dUser, dBal + dAmount);
                    logTransaction(dUser, "DEPOSIT $" + dAmount);
                    return "OK:NEW_BALANCE=" + (dBal + dAmount);

                case "TRANSFER":
                    // TRANSFER <FromUser> <ToUser> <Amount>
                    String fromUser = parts[1];
                    String toUser = parts[2];
                    double tAmount = Double.parseDouble(parts[3]);

                    double fBal = db.getBalance(fromUser);
                    if (fBal == -1.0)
                        return "FAIL:SENDER_NOT_FOUND";

                    if (!db.accountExists(toUser)) {
                        return "FAIL:RECIPIENT_NOT_FOUND";
                    }

                    if (fBal >= tAmount) {
                        db.updateBalance(fromUser, fBal - tAmount);
                        db.updateBalance(toUser, db.getBalance(toUser) + tAmount);
                        logTransaction(fromUser, "TRANSFER $" + tAmount + " to " + toUser);
                        return "OK:TRANSFERRED_$" + tAmount;
                    } else {
                        return "FAIL:INSUFFICIENT_FUNDS";
                    }

                default:
                    return "ERROR:UNKNOWN_COMMAND";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR:BAD_REQUEST";
        }
    }

    private static void logTransaction(String user, String details) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
                PrintWriter pw = new PrintWriter(fw)) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.println("[" + time + "] [" + user + "] " + details);
        } catch (IOException e) {
            System.err.println("Failed to write to log: " + e.getMessage());
        }
    }
}
