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

    private static final String CLIENT_VERSION = "1.095";
    
    private DefaultListModel<String> userListModel = new DefaultListModel<>();
    private JList<String> userList = new JList<>(userListModel);
    private JScrollPane userScrollPane = new JScrollPane(userList);


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
    
    private static final Map<String, String> emojiMap = new LinkedHashMap<>();
    static {
        emojiMap.put(":smile:", "😄");
        emojiMap.put(":grin:", "😁");
        emojiMap.put(":joy:", "😂");
        emojiMap.put(":rofl:", "🤣");
        emojiMap.put(":wink:", "😉");
        emojiMap.put(":blush:", "😊");
        emojiMap.put(":sunglasses:", "😎");
        emojiMap.put(":thinking:", "🤔");
        emojiMap.put(":neutral:", "😐");
        emojiMap.put(":cry:", "😢");
        emojiMap.put(":sob:", "😭");
        emojiMap.put(":angry:", "😠");
        emojiMap.put(":rage:", "😡");
        emojiMap.put(":skull:", "💀");
        emojiMap.put(":fire:", "🔥");
        emojiMap.put(":thumbs:", "👍");
        emojiMap.put(":thumbsdown:", "👎");
        emojiMap.put(":heart:", "❤️");
        emojiMap.put(":broken_heart:", "💔");
        emojiMap.put(":100:", "💯");
        emojiMap.put(":star:", "⭐");
        emojiMap.put(":sparkles:", "✨");
        emojiMap.put(":zap:", "⚡");
        emojiMap.put(":check:", "✔️");
        emojiMap.put(":x:", "❌");
        emojiMap.put(":wave:", "👋");
        emojiMap.put(":clap:", "👏");
        emojiMap.put(":pray:", "🙏");
        emojiMap.put(":ok_hand:", "👌");
        emojiMap.put(":eyes:", "👀");
        emojiMap.put(":poop:", "💩");
        emojiMap.put(":alien:", "👽");
        emojiMap.put(":robot:", "🤖");
        emojiMap.put(":cat:", "🐱");
        emojiMap.put(":dog:", "🐶");
        emojiMap.put(":dragon:", "🐉");
        emojiMap.put(":ghost:", "👻");
        emojiMap.put(":pumpkin:", "🎃");
        emojiMap.put(":snowflake:", "❄️");
        emojiMap.put(":christmas_tree:", "🎄");
    }
    
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
        underlineBtn.setText("<html><u>U</u></html>");
        underlineBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));


        toolBar.add(boldBtn);
        toolBar.add(italicBtn);
        toolBar.add(underlineBtn);
        toolBar.add(attachButton);
        toolBar.setFloatable(false);
        
        JButton emojiButton = new JButton("+😀"); // or any icon/text
        toolBar.add(emojiButton);
        emojiButton.addActionListener(e -> {
            // Create panel with grid layout
            JPanel emojiGrid = new JPanel(new GridLayout(0, 8, 5, 5)); // 8 columns, flexible rows
            for (Map.Entry<String, String> entry : emojiMap.entrySet()) {
                JButton btn = new JButton(entry.getValue());
                btn.setToolTipText(entry.getKey());
                btn.setMargin(new Insets(2, 2, 2, 2)); // small padding
        
                // Insert emoji into inputPane
                btn.addActionListener(ev -> {
                    try {
                        int pos = inputPane.getCaretPosition();
                        inputPane.getDocument().insertString(pos, entry.getValue(), null);
                    } catch (BadLocationException ex) {
                        ex.printStackTrace();
                    }
                });
        
                emojiGrid.add(btn);
            }
        
            // Wrap in a scroll pane in case there are many emojis
            JScrollPane scrollPane = new JScrollPane(emojiGrid);
            scrollPane.setPreferredSize(new Dimension(300, 200));
        
            // Create popup
            JPopupMenu popup = new JPopupMenu();
            popup.setLayout(new BorderLayout());
            popup.add(scrollPane, BorderLayout.CENTER);
        
            // Show the popup near the emoji button
            popup.show(emojiButton, 0, emojiButton.getHeight());
        });


        // Panel for editor + send button
        JScrollPane inputScrollPane = new JScrollPane(inputPane);
        FontMetrics metrics = inputPane.getFontMetrics(inputPane.getFont());
        int lineHeight = metrics.getHeight();
        inputScrollPane.setPreferredSize(new Dimension(400, lineHeight * 5 + 8)); // 5 lines height
        
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(inputScrollPane, BorderLayout.CENTER);


        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(toolBar, BorderLayout.NORTH);
        bottomPanel.add(editorPanel, BorderLayout.CENTER);

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.add(sendButton, BorderLayout.EAST);
        sendPanel.add(typingLabel, BorderLayout.CENTER);
        
        userList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        userList.setFixedCellWidth(120);
        userList.setBorder(BorderFactory.createTitledBorder("Online Users"));


        bottomPanel.add(sendPanel, BorderLayout.SOUTH);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(new JScrollPane(chatPane), BorderLayout.CENTER);
        mainPanel.add(userScrollPane, BorderLayout.EAST);
        
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

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
            if (msg.startsWith("/getFiles")) {
                requestFileFromServer(msg.substring(5).trim());
            } else if (msg.equalsIgnoreCase("/clear")) {
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
    
    private void checkForUpdate() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("192.168.100.60", 34567), 5000); // 5s timeout
            socket.setSoTimeout(5000); // read timeout
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
    
            // Request update for ChatClient
            dos.writeUTF(CLIENT_VERSION);
            dos.flush();
    
            // Server sends latest version first
            String latestVersion = dis.readUTF();
            if (latestVersion.equals("NO_UPDATE") || latestVersion.equals(CLIENT_VERSION)) {
                //System.out.println("Already up to date (v" + CLIENT_VERSION + ")");
                socket.close();
                return;
            }
    
            // Read the file info
            String fileName = dis.readUTF();
            long fileLength = dis.readLong();
    
            // Save file locally
            File file = new File(fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileLength;
                while (remaining > 0 && (read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining))) > 0) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }
    
            socket.close();
    
            JOptionPane.showMessageDialog(frame,
                    "Updated to latest version v" + latestVersion + ". Please run main() again.",
                    "Update Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        } catch (SocketException se) {
            System.out.println("Update server unreachable. Continuing without update...");
            se.printStackTrace();
        } catch (IOException e) {
            //System.out.println("No update available or file server unreachable.");
            e.printStackTrace();
        }
    }

    private void start() {
        
        checkForUpdate();
        
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
                            appendSystemMessage("You are using the latest ChatClient v"+CLIENT_VERSION);
                            appendSystemMessage("✅ You are now logged in as: " + currentUsername);
                            continue;
                        }
                        if (message.startsWith("👥 Online:")) {
                            updateUserList(message.replace("👥 Online:", "").trim());
                        } else if (message.startsWith("[TYPING]")) {
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
    
    private void requestFileFromServer(String filename) {
        new Thread(() -> {
            try {
                Socket fileSocket = new Socket();
                fileSocket.connect(new InetSocketAddress(SERVER_IP, 34567), 5000);
                fileSocket.setSoTimeout(10000);
    
                DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(fileSocket.getInputStream());
    
                // Send request
                dos.writeUTF(filename);
                dos.flush();
    
                // Read server response
                String response = dis.readUTF();
                if (response.equals("NO_FILE")) {
                    appendSystemMessage("❌ File not found on server: " + filename);
                    fileSocket.close();
                    return;
                }
    
                String[] parts = response.split(" ", 2);
                if (parts.length != 2) {
                    appendSystemMessage("❌ Invalid file response from server.");
                    fileSocket.close();
                    return;
                }
    
                String serverFileName = parts[0];
                long fileLength = Long.parseLong(parts[1]);
    
                // Ask user where to save
                SwingUtilities.invokeLater(() -> {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setSelectedFile(new File(serverFileName));
                    int choice = fileChooser.showSaveDialog(frame);
                    if (choice != JFileChooser.APPROVE_OPTION) {
                        appendSystemMessage("⚠️ File download canceled: " + serverFileName);
                        try { fileSocket.close(); } catch (IOException ignored) {}
                        return;
                    }
    
                    File saveFile = fileChooser.getSelectedFile();
    
                    // ✅ Now do the actual download inside SAME socket scope
                    new Thread(() -> {
                        try (Socket s = fileSocket;
                             DataInputStream fileIn = dis;
                             FileOutputStream fos = new FileOutputStream(saveFile)) {
    
                            byte[] buffer = new byte[4096];
                            long remaining = fileLength;
                            int read;
                            while (remaining > 0 && (read = fileIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                                fos.write(buffer, 0, read);
                                remaining -= read;
                            }
    
                            appendSystemMessage("✅ File downloaded: " + saveFile.getAbsolutePath());
                        } catch (IOException e) {
                            appendSystemMessage("❌ Failed to save file: " + serverFileName);
                            e.printStackTrace();
                        }
                    }).start();
                });
    
            } catch (IOException e) {
                appendSystemMessage("❌ Could not connect to file server: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }



    private void updateUserList(String usersLine) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] users = usersLine.split(",\\s*");
            for (String user : users) {
                if (!user.isEmpty()) userListModel.addElement(user);
            }
        });
    }

    private void handleDisconnect() {
        appendSystemMessage("[@]❌ Server disconnected.[/@]");
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
                int startLen = doc.getLength();
                
                // first run through your tag parser
                insertStyledText(doc, message + "\n");
                
                // then apply magenta + bold to just this inserted section
                SimpleAttributeSet sysStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(sysStyle, Color.MAGENTA);
                StyleConstants.setBold(sysStyle, true);
                doc.setCharacterAttributes(startLen, message.length(), sysStyle, false);
    
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
            } else if (text.startsWith("[@]", i)) {               // start of mention
                StyleConstants.setBackground(attrs, Color.YELLOW);
                    if (frame != null) {
                    SwingUtilities.invokeLater(() -> {
                        frame.setState(JFrame.NORMAL);   // restore if minimized
                        frame.toFront();                 // bring to front
                        frame.requestFocus();            // request focus
                    });
                }
                i += 3;
            } else if (text.startsWith("[/@]", i)) {             // end of mention
                StyleConstants.setBackground(attrs, Color.WHITE);
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
    
                    for (Map.Entry<String, String> entry : emojiMap.entrySet()) {
                        msg = msg.replace(entry.getKey(), entry.getValue());
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
                    
                    msg = msg.replaceAll("@" + currentUsername, "[@]" + "@" + currentUsername + "[/@]");
    
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
