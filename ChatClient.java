import javax.swing.*;
import javax.swing.text.*;
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
    private JTextPane inputPane = new JTextPane(); // replaced JTextField
    private JLabel typingLabel = new JLabel(" "); // shows "Bob is typing..."
    private JButton sendButton = new JButton("Send");
    private JButton attachButton = new JButton("Attach");

    private Map<String, Color> userColors = new HashMap<>();
    private Random random = new Random();

    private final Map<String, String> commands = new LinkedHashMap<>();
    private String currentUsername = "Anonymous";

    public ChatClient() {
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // Toolbar for formatting
        JToolBar toolBar = new JToolBar();
        JButton boldBtn = new JButton("B");
        JButton italicBtn = new JButton("I");
        JButton underlineBtn = new JButton("U");

        boldBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        italicBtn.setFont(new Font("SansSerif", Font.ITALIC, 14));
        underlineBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        underlineBtn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.BLACK));

        toolBar.add(boldBtn);
        toolBar.add(italicBtn);
        toolBar.add(underlineBtn);
        toolBar.add(attachButton);

        // Panel for editor + send button
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(new JScrollPane(inputPane), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(toolBar, BorderLayout.NORTH);
        bottomPanel.add(editorPanel, BorderLayout.CENTER);

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.add(sendButton, BorderLayout.EAST);
        sendPanel.add(typingLabel, BorderLayout.CENTER);

        bottomPanel.add(sendPanel, BorderLayout.SOUTH);

        frame.getContentPane().add(new JScrollPane(chatPane), BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // available commands
        commands.put("/help", "Show available commands");
        commands.put("/clear", "Clear the chat window");
        commands.put("/users", "List online users");

        // --- Formatting actions ---
        boldBtn.addActionListener(e -> toggleStyle(StyleConstants.Bold));
        italicBtn.addActionListener(e -> toggleStyle(StyleConstants.Italic));
        underlineBtn.addActionListener(e -> toggleStyle(StyleConstants.Underline));

        // Send button
        sendButton.addActionListener(e -> sendMessage());

        // Ctrl+Enter sends message
        inputPane.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "send");
        inputPane.getActionMap().put("send", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Attach button
        attachButton.addActionListener(e -> attachFile());

        // Typing indicator
        inputPane.addKeyListener(new KeyAdapter() {
            private long lastSent = 0;
            @Override
            public void keyTyped(KeyEvent e) {
                long now = System.currentTimeMillis();
                if (out != null && now - lastSent > 1000) {
                    out.println("/typing");
                    lastSent = now;
                }
            }
        });
    }

    private void toggleStyle(Object style) {
        StyledDocument doc = inputPane.getStyledDocument();
        int start = inputPane.getSelectionStart();
        int end = inputPane.getSelectionEnd();
        if (start == end) return;

        MutableAttributeSet attr = new SimpleAttributeSet();
        if (style == StyleConstants.Bold) {
            boolean bold = StyleConstants.isBold(doc.getCharacterElement(start).getAttributes());
            StyleConstants.setBold(attr, !bold);
        } else if (style == StyleConstants.Italic) {
            boolean italic = StyleConstants.isItalic(doc.getCharacterElement(start).getAttributes());
            StyleConstants.setItalic(attr, !italic);
        } else if (style == StyleConstants.Underline) {
            boolean underline = StyleConstants.isUnderline(doc.getCharacterElement(start).getAttributes());
            StyleConstants.setUnderline(attr, !underline);
        }
        doc.setCharacterAttributes(start, end - start, attr, false);
    }

    // Extract styled text -> markup
    private String getStyledTextAsMarkup(JTextPane textPane) {
        StyledDocument doc = textPane.getStyledDocument();
        StringBuilder sb = new StringBuilder();
        int length = doc.getLength();
        for (int i = 0; i < length; ) {
            Element element = doc.getCharacterElement(i);
            AttributeSet as = element.getAttributes();
            boolean bold = StyleConstants.isBold(as);
            boolean italic = StyleConstants.isItalic(as);
            boolean underline = StyleConstants.isUnderline(as);

            int start = element.getStartOffset();
            int end = element.getEndOffset();
            String text = "";
            try {
                text = doc.getText(start, end - start);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            if (bold) sb.append("[b]");
            if (italic) sb.append("[i]");
            if (underline) sb.append("[u]");
            sb.append(text);
            if (underline) sb.append("[/u]");
            if (italic) sb.append("[/i]");
            if (bold) sb.append("[/b]");

            i = end;
        }
        return sb.toString().trim();
    }

    private void sendMessage() {
        String msg = getStyledTextAsMarkup(inputPane);
        if (!msg.isEmpty() && out != null) {
            if (msg.equalsIgnoreCase("/clear")) {
                chatPane.setText("");
            } else if (msg.equalsIgnoreCase("/help")) {
                showHelp();
            } else {
                out.println(msg);
            }
            inputPane.setText("");
        }
    }

    private void attachFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            appendSystemMessage("📎 Attached file: " + file.getName());
            try {
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                String encoded = Base64.getEncoder().encodeToString(data);
                out.println("/file " + file.getName() + " " + encoded);
            } catch (IOException ex) {
                appendSystemMessage("❌ Failed to attach file.");
            }
        }
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
                        if (message.startsWith("[USERNAME]")) {
                            currentUsername = message.replace("[USERNAME]", "").trim();
                            appendSystemMessage("✅ You are now logged in as: " + currentUsername);
                            continue;
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
        inputPane.setEditable(false);
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
                StyledDocument doc = chatPane.getStyledDocument();
                SimpleAttributeSet sysStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(sysStyle, Color.MAGENTA);
                StyleConstants.setBold(sysStyle, true);
                doc.insertString(doc.getLength(), message + "\n", sysStyle);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    // Parse markup [b][i][u] into styles
    private void insertStyledText(StyledDocument doc, String text) throws BadLocationException {
        int i = 0;
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        while (i < text.length()) {
            if (text.startsWith("[b]", i)) {
                StyleConstants.setBold(attrs, true);
                i += 3;
            } else if (text.startsWith("[/b]", i)) {
                StyleConstants.setBold(attrs, false);
                i += 4;
            } else if (text.startsWith("[i]", i)) {
                StyleConstants.setItalic(attrs, true);
                i += 3;
            } else if (text.startsWith("[/i]", i)) {
                StyleConstants.setItalic(attrs, false);
                i += 4;
            } else if (text.startsWith("[u]", i)) {
                StyleConstants.setUnderline(attrs, true);
                i += 3;
            } else if (text.startsWith("[/u]", i)) {
                StyleConstants.setUnderline(attrs, false);
                i += 4;
            } else {
                doc.insertString(doc.getLength(), String.valueOf(text.charAt(i)), attrs);
                i++;
            }
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = chatPane.getStyledDocument();
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3 && parts[1].contains(":")) {
                    String time = parts[0];
                    String user = parts[1].substring(0, parts[1].length() - 1);
                    String msg = parts[2];
    
                    // ✅ Emoji replacements (using array instead of chained replace)
                    String[][] emojis = {
                        {":smile:", "😄"}, {":grin:", "😁"}, {":joy:", "😂"},
                        {":rofl:", "🤣"}, {":wink:", "😉"}, {":blush:", "😊"},
                        {":sunglasses:", "😎"}, {":thinking:", "🤔"}, {":neutral:", "😐"},
                        {":cry:", "😢"}, {":sob:", "😭"}, {":angry:", "😠"},
                        {":rage:", "😡"}, {":skull:", "💀"}, {":fire:", "🔥"},
                        {":thumbs:", "👍"}, {":thumbsdown:", "👎"}, {":heart:", "❤️"},
                        {":broken_heart:", "💔"}, {":100:", "💯"}, {":star:", "⭐"},
                        {":sparkles:", "✨"}, {":zap:", "⚡"}, {":check:", "✔️"},
                        {":x:", "❌"}, {":wave:", "👋"}, {":clap:", "👏"},
                        {":pray:", "🙏"}, {":ok_hand:", "👌"}, {":eyes:", "👀"},
                        {":poop:", "💩"}, {":alien:", "👽"}, {":robot:", "🤖"},
                        {":cat:", "🐱"}, {":dog:", "🐶"}, {":dragon:", "🐉"},
                        {":ghost:", "👻"}, {":pumpkin:", "🎃"}, {":snowflake:", "❄️"},
                        {":christmas_tree:", "🎄"}
                    };
    
                    for (String[] emoji : emojis) {
                        msg = msg.replace(emoji[0], emoji[1]);
                    }
    
                    userColors.putIfAbsent(user,
                            new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
    
                    SimpleAttributeSet timeStyle = new SimpleAttributeSet();
                    StyleConstants.setForeground(timeStyle, Color.GRAY);
                    doc.insertString(doc.getLength(), time + " ", timeStyle);
    
                    SimpleAttributeSet userStyle = new SimpleAttributeSet();
                    StyleConstants.setForeground(userStyle, userColors.get(user));
                    StyleConstants.setBold(userStyle, true);
                    doc.insertString(doc.getLength(), user + ": ", userStyle);
    
                    insertStyledText(doc, msg + "\n");
                } else {
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
        }, 2000);
    }

    private void hideTyping() {
        SwingUtilities.invokeLater(() -> typingLabel.setText(" "));
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.start();
    }
}
