import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient {
    private static final String SERVER_IP = "192.168.100.60"; // change to your server IP
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private JFrame frame = new JFrame("Chat Client");
    private JTextPane chatPane = new JTextPane();
    private JTextField inputField = new JTextField(40);
    private JLabel typingLabel = new JLabel(" "); // shows "Bob is typing..."

    private Map<String, Color> userColors = new HashMap<>();
    private Random random = new Random();

    private final Map<String, String> commands = new LinkedHashMap<>();

    public ChatClient() {
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Monospaced", Font.PLAIN, 14));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(typingLabel, BorderLayout.NORTH);
        typingLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        typingLabel.setForeground(Color.GRAY);

        frame.getContentPane().add(new JScrollPane(chatPane), BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // available commands
        commands.put("/help", "Show available commands");
        commands.put("/clear", "Clear the chat window");
        commands.put("/users", "List online users");

        // send message or commands
        inputField.addActionListener(e -> {
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                if (msg.equalsIgnoreCase("/clear")) {
                    chatPane.setText("");
                } else if (msg.equalsIgnoreCase("/help")) {
                    showHelp();
                } else {
                    if (out != null) out.println(msg);
                }
                inputField.setText("");
            }
        });

        // send typing notification
        inputField.addKeyListener(new KeyAdapter() {
            private long lastSent = 0;
            @Override
            public void keyTyped(KeyEvent e) {
                long now = System.currentTimeMillis();
                if (out != null && now - lastSent > 1000) { // throttle to 1s
                    out.println("/typing");
                    lastSent = now;
                }
            }
        });
    }

    private void showHelp() {
        StringBuilder sb = new StringBuilder("📖 Available commands:\n");
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            sb.append(String.format("  %-8s - %s%n", entry.getKey(), entry.getValue()));
        }
        appendSystemMessage(sb.toString());
    }

    private void start() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ask for username
            String username = JOptionPane.showInputDialog(frame, "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
            if (username == null || username.trim().isEmpty()) username = "Anonymous";
            out.println(username);

            // Thread to read messages
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.equals("SERVER_CLOSED")) {
                            handleDisconnect();
                            break;
                        }
                        if (message.startsWith("[KICK]")) {
                            appendSystemMessage("⛔ You were kicked by the server.");
                            handleDisconnect();
                            break;
                        }
                        if (message.startsWith("[TYPING]")) {
                            showTyping(message.replace("[TYPING]", "").trim());
                        } else {
                            hideTyping();
                            appendMessage(message);
                        }
                    }
                    handleDisconnect();
                } catch (IOException e) {
                    handleDisconnect();
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "❌ Could not connect to server", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void handleDisconnect() {
        appendSystemMessage("❌ Server disconnected.");
        inputField.setEditable(false);
        closeClient();
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                System.exit(0);
            }
        }, 2000);
    }

    private void closeClient() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void appendSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.StyledDocument doc = chatPane.getStyledDocument();
                javax.swing.text.SimpleAttributeSet sysStyle = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(sysStyle, Color.MAGENTA);
                javax.swing.text.StyleConstants.setBold(sysStyle, true);
                doc.insertString(doc.getLength(), message + "\n", sysStyle);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.StyledDocument doc = chatPane.getStyledDocument();

                // Normal chat message parsing
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3 && parts[1].contains(":")) {
                    String time = parts[0];
                    String user = parts[1].substring(0, parts[1].length() - 1);
                    String msg = parts[2];

                    // replace emojis
                    msg = msg.replace(":smile:", "😄")
                             .replace(":heart:", "❤️")
                             .replace(":thumbs:", "👍")
                             .replace(":fire:", "🔥")
                             .replace(":skull:", "💀");

                    userColors.putIfAbsent(user,
                            new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));

                    // Time
                    javax.swing.text.SimpleAttributeSet timeStyle = new javax.swing.text.SimpleAttributeSet();
                    javax.swing.text.StyleConstants.setForeground(timeStyle, Color.GRAY);
                    doc.insertString(doc.getLength(), time + " ", timeStyle);

                    // Username
                    javax.swing.text.SimpleAttributeSet userStyle = new javax.swing.text.SimpleAttributeSet();
                    javax.swing.text.StyleConstants.setForeground(userStyle, userColors.get(user));
                    javax.swing.text.StyleConstants.setBold(userStyle, true);
                    doc.insertString(doc.getLength(), user + ": ", userStyle);

                    // Message
                    javax.swing.text.SimpleAttributeSet msgStyle = new javax.swing.text.SimpleAttributeSet();
                    javax.swing.text.StyleConstants.setForeground(msgStyle, Color.BLACK);
                    doc.insertString(doc.getLength(), msg + "\n", msgStyle);
                } else {
                    // fallback
                    appendSystemMessage(message);
                }

                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void showTyping(String user) {
        SwingUtilities.invokeLater(() -> typingLabel.setText(user + " is typing…"));
        new java.util.Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hideTyping();
            }
        }, 2000); // hide after 2s
    }

    private void hideTyping() {
        SwingUtilities.invokeLater(() -> typingLabel.setText(" "));
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.start();
    }
}