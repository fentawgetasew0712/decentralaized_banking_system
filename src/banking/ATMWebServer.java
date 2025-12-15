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
                String name = extractJson(json, "name");
                String pass = extractJson(json, "pass");
                String amount = extractJson(json, "amount");

                System.out.println("DEBUG Register Parsed: User=" + user + ", Amt='" + amount + "'");

                String result = atmNode.register(user, name, pass, amount);

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
}
