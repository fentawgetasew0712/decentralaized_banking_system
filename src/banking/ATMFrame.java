package banking;

import bank.ATMNode;
import javax.swing.*;
import java.awt.*;
// import java.awt.event.ActionEvent; -> Unused

public class ATMFrame extends JFrame {
    private ATMNode atmNode;
    private JTextArea logArea;
    private JLabel balanceLabel;
    private JTextField amountField;
    private JTextField targetUserField;
    private JTextField myUserField;
    private String currentUserId;

    public ATMFrame(String title) {
        super(title);
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top Panel: Login
        JPanel topPanel = new JPanel(new GridLayout(2, 2));
        topPanel.add(new JLabel("Username:"));
        myUserField = new JTextField("user1");
        topPanel.add(myUserField);
        JButton loginBtn = new JButton("Set Active User");
        loginBtn.addActionListener(e -> {
            currentUserId = myUserField.getText();
            log("Active User Set to: " + currentUserId);
            updateBalance();
        });
        topPanel.add(loginBtn);
        balanceLabel = new JLabel("Balance: ???");
        topPanel.add(balanceLabel);
        add(topPanel, BorderLayout.NORTH);

        // Center Panel: Controls
        JPanel centerPanel = new JPanel(new GridLayout(5, 2, 5, 5));

        centerPanel.add(new JLabel("Amount:"));
        amountField = new JTextField("10");
        centerPanel.add(amountField);

        JButton withdrawBtn = new JButton("WITHDRAW");
        withdrawBtn.addActionListener(e -> performAction("WITHDRAW"));
        centerPanel.add(withdrawBtn);

        JButton depositBtn = new JButton("DEPOSIT");
        depositBtn.addActionListener(e -> performAction("DEPOSIT"));
        centerPanel.add(depositBtn);

        centerPanel.add(new JLabel("Target User (Transfer):"));
        targetUserField = new JTextField("user2");
        centerPanel.add(targetUserField);

        JButton transferBtn = new JButton("TRANSFER");
        transferBtn.addActionListener(e -> performAction("TRANSFER"));
        centerPanel.add(transferBtn);

        add(centerPanel, BorderLayout.CENTER);

        // Bottom Panel: Logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    public void setAtmNode(ATMNode node) {
        this.atmNode = node;
    }

    private void performAction(String action) {
        if (atmNode == null) {
            log("Error: ATM Node not started.");
            return;
        }

        log("Requesting Access for " + action + "...");

        // Run in background to avoid freezing GUI
        new Thread(() -> {
            atmNode.setOperationDetails(action, currentUserId, amountField.getText(), targetUserField.getText());
            atmNode.requestAccess(currentUserId);
            // GUI Update happens after Critical Section via callback or just refreshing
            // balance
            SwingUtilities.invokeLater(this::updateBalance);
        }).start();
    }

    private void updateBalance() {
        if (atmNode == null || currentUserId == null)
            return;
        new Thread(() -> {
            String bal = atmNode.checkBalance(currentUserId);
            SwingUtilities.invokeLater(() -> balanceLabel.setText("Balance: " + bal));
        }).start();
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }
}
