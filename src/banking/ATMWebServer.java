package banking;

import bank.ATMNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
// import java.nio.file.Paths; -> Unused

public class ATMWebServer {

    private final ATMNode atmNode;
    private final int port;

    public ATMWebServer(ATMNode atmNode, int port) {
        this.atmNode = atmNode;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Serve Static Files
        server.createContext("/", new StaticHandler());

        // API Endpoints
        server.createContext("/api/balance", new BalanceHandler());
        server.createContext("/api/action", new ActionHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/forget", new ForgetHandler());

        // Admin Endpoints
        server.createContext("/api/admin/logs", new AdminLogsHandler());
        server.createContext("/api/admin/users", new AdminUsersHandler());
        server.createContext("/api/admin/stats", new AdminStatsHandler());

        server.setExecutor(null);
        server.start();
        System.out.println(">>> Web Server started at http://localhost:" + port + " <<<");
    }

    // Serve HTML/CSS
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html"))
                path = "/login.html";

            // Adjust this path based on where you run it from (Assuming run from project
            // root)
            File file = new File("src/web" + path);

            if (!file.exists()) {
                String response = "404 Not Found";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static String extractJson(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1)
            return "";

        start += search.length();

        // Determine if string or number
        char firstChar = json.charAt(start);
        while (firstChar == ' ' || firstChar == '"') {
            start++;
            firstChar = json.charAt(start);
        }

        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ','
                && json.charAt(end) != '}') {
            end++;
        }

        return json.substring(start, end);
    }

    class BalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery(); // user=user1&t=123
            String user = "";
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length > 1 && "user".equals(pair[0])) {
                        user = pair[1];
                        break;
                    }
                }
            }

            String balance = atmNode.checkBalance(user);

            t.sendResponseHeaders(200, balance.length());
            OutputStream os = t.getResponseBody();
            os.write(balance.getBytes());
            os.close();
        }
    }

    class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }

                // Very simple JSON parse (avoiding libraries)
                String json = buf.toString();
                String type = extractJson(json, "type");
                String user = extractJson(json, "user");
                String amount = extractJson(json, "amount");
                String target = extractJson(json, "target"); // Optional

                System.out.println("WEB API: Received " + type + " for " + user);

                // API Level Validation
                int valAmt = 0;
                try {
                    valAmt = Integer.parseInt(amount);
                    if (valAmt <= 0) {
                        throw new NumberFormatException("Negative amount");
                    }
                } catch (NumberFormatException e) {
                    String errResponse = "FAIL:INVALID_AMOUNT";
                    t.sendResponseHeaders(200, errResponse.length());
                    OutputStream os = t.getResponseBody();
                    os.write(errResponse.getBytes());
                    os.close();
                    return;
                }

                // Synchronize to prevent race conditions on the single ATMNode instance
                String response;
                synchronized (atmNode) {
                    atmNode.setOperationDetails(type, user, amount, target);
                    // BLOCKING CALL: Waits for Distributed Mutual Exclusion & Execution
                    atmNode.requestAccess(user);
                    response = atmNode.getLastTransactionResult();
                }

                // If response is empty (e.g. timeout without execution), set default
                if (response == null || response.isEmpty()) {
                    response = "ERROR:TIMEOUT_OR_FAILED";
                }

                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

    }

    class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }

                String json = buf.toString();
                System.out.println("DEBUG Register JSON: " + json);

                String user = extractJson(json, "user");
                String fName = extractJson(json, "fName");
                String sName = extractJson(json, "sName");
                String tName = extractJson(json, "tName");
                String phone = extractJson(json, "phone");
                String pass = extractJson(json, "pass");
                String amount = extractJson(json, "amount");

                System.out.println("DEBUG Register Parsed: User=" + user + ", Amt='" + amount + "'");

                String result = atmNode.register(user, fName, sName, tName, phone, pass, amount);

                t.sendResponseHeaders(200, result.length());
                OutputStream os = t.getResponseBody();
                os.write(result.getBytes());
                os.close();
            }
        }
    }

    class ForgetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }

                String json = buf.toString();
                String user = extractJson(json, "user");
                String fName = extractJson(json, "fName");
                String sName = extractJson(json, "sName");
                String tName = extractJson(json, "tName");
                String phone = extractJson(json, "phone");
                String newPass = extractJson(json, "newPass");

                String result = atmNode.forgetPassword(user, fName, sName, tName, phone, newPass);

                t.sendResponseHeaders(200, result.length());
                OutputStream os = t.getResponseBody();
                os.write(result.getBytes());
                os.close();
            }
        }
    }

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }

                String json = buf.toString();
                String user = extractJson(json, "user");
                String pass = extractJson(json, "pass");

                // Call ATMNode -> BankServer
                String result = atmNode.login(user, pass);

                t.sendResponseHeaders(200, result.length());
                OutputStream os = t.getResponseBody();
                os.write(result.getBytes());
                os.close();
            }
        }
    }

    class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }

                String json = buf.toString();
                String user = extractJson(json, "user");

                atmNode.logout(user);

                String response = "OK:LOGGED_OUT";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    // ================= ADMIN HANDLERS =================

    class AdminLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Fetch aggregated logs from ALL nodes in the cluster
            java.util.List<bank.Database.Transaction> logs = atmNode.getAllClusterTransactions();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < logs.size(); i++) {
                bank.Database.Transaction tx = logs.get(i);
                json.append("{")
                        .append("\"id\":").append(tx.id).append(",")
                        .append("\"timestamp\":\"").append(tx.timestamp).append("\",")
                        .append("\"type\":\"").append(tx.type).append("\",")
                        .append("\"user\":\"").append(tx.userId).append("\",")
                        .append("\"amount\":\"").append(tx.amount).append("\",")
                        .append("\"target\":\"").append(tx.targetId).append("\",")
                        .append("\"node\":").append(tx.nodeId)
                        .append("}");
                if (i < logs.size() - 1)
                    json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes("UTF-8");
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    class AdminUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            java.util.List<bank.Database.Account> allUsers = atmNode.getLocalDB().getAllUsers();
            StringBuilder json = new StringBuilder("[");

            // Filter out system admin (000000000000)
            java.util.List<bank.Database.Account> customers = new java.util.ArrayList<>();
            for (bank.Database.Account acc : allUsers) {
                if (!acc.id.equals("000000000000")) {
                    customers.add(acc);
                }
            }

            for (int i = 0; i < customers.size(); i++) {
                bank.Database.Account acc = customers.get(i);
                json.append("{")
                        .append("\"id\":\"").append(acc.id).append("\",")
                        .append("\"name\":\"").append(acc.name).append("\",")
                        .append("\"balance\":").append(acc.balance)
                        .append("}");
                if (i < customers.size() - 1)
                    json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes("UTF-8");
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    class AdminStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            java.util.List<bank.Database.Account> allUsers = atmNode.getLocalDB().getAllUsers();
            // Use cluster-wide transactions for stats
            java.util.List<bank.Database.Transaction> logs = atmNode.getAllClusterTransactions();

            long totalReserves = 0;
            int customerCount = 0;
            for (bank.Database.Account acc : allUsers) {
                // Exclude system admin from statistics
                if (!acc.id.equals("000000000000")) {
                    totalReserves += acc.balance;
                    customerCount++;
                }
            }

            StringBuilder json = new StringBuilder();
            json.append("{")
                    .append("\"totalUsers\":").append(customerCount).append(",")
                    .append("\"totalReserves\":").append(totalReserves).append(",")
                    .append("\"totalTransactions\":").append(logs.size())
                    .append("}");

            byte[] bytes = json.toString().getBytes("UTF-8");
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
