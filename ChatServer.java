import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int UPDATE_PORT = 34567; // new port for auto-updates
    private static final String UPDATE_FILE = "ChatClient.java"; // file to send for updates

    private static final String CLIENT_VERSION = "1.01"; // current local version
    
    private static final int PORT = 12345;
    @SuppressWarnings("unused")
    private static final int FILE_PORT = 12346; // reserved (not used in this text-line protocol)

    private static final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    private static final List<String> messageHistory =
            Collections.synchronizedList(new LinkedList<>());

    private static final int MAX_HISTORY = 50;
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    private static ServerSocket serverSocket;
    private static final File filesDir = new File("server_files");

    // simple in-memory file index for quick existence checks
    private static final Map<String, File> fileIndex = new ConcurrentHashMap<>();

    // Server console commands help text
    private static final String SERVER_HELP =
        "\n📖 Server Commands:\n" +
        "  /help              - Show this help message\n" +
        "  /stop              - Stop the server\n" +
        "  /kick <username>   - Kick a specific user\n" +
        "  <message>          - Broadcast a server announcement (markup supported)\n";

    // Client commands help text
    private static final String CLIENT_HELP =
        "\n📖 Commands:\n" +
        "  /help                - Show this help\n" +
        "  /users               - List online users\n" +
        "  /files               - List uploaded files\n" +
        "  /get <filename>      - Download a file\n" +
        "  /typing              - Send typing indicator\n" +
        "\nFormatting:\n" +
        "  Use [b]bold[/b], [i]italic[/i], [u]underline[/u]\n" +
        "  Emojis: :smile: :heart: :thumbs: :fire: :skull:\n";

    public static void main(String[] args) {
        if (!filesDir.exists()) filesDir.mkdir();
        // refresh index from disk
        for (File f : Objects.requireNonNull(filesDir.listFiles())) {
            fileIndex.put(f.getName(), f);
        }

        System.out.println("Chat server started on port " + PORT);
        System.out.println("Type /help for server commands.");

        // Thread for server console commands
        new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    String line = scanner.nextLine();
                    if (line.equalsIgnoreCase("/stop")) {
                        shutdownServer();
                        break;
                    }
                    if (line.equalsIgnoreCase("/help")) {
                        System.out.println(SERVER_HELP);
                        continue;
                    }
                    if (line.startsWith("/kick ")) {
                        String target = line.substring(6).trim();
                        kickUser(target);
                        continue;
                    }
                    if (!line.trim().isEmpty()) {
                        // console broadcast supports markup too
                        broadcast("📢 SERVER: " + line);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "server-console").start();
        
        // start auto-update server
        new Thread(() -> {
            try (ServerSocket updateSocket = new ServerSocket(UPDATE_PORT)) {
                System.out.println("Auto-update server started on port " + UPDATE_PORT);
                while (true) {
                    Socket socket = updateSocket.accept();
                    new Thread(() -> handleUpdateRequest(socket)).start();
                }
            } catch (IOException e) {
                System.out.println("Update server stopped.");
            }
        }, "update-server").start();

        // Start main chat server
        try (ServerSocket ss = new ServerSocket(PORT)) {
            serverSocket = ss;
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start(); // add to clients after username is assigned
            }
        } catch (IOException e) {
            System.out.println("Server stopped.");
        }
        
    }
    
    private static String getLatestClientVersion(File chatClientFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(chatClientFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("private static final String CLIENT_VERSION")) {
                    // extract version between quotes
                    int start = line.indexOf("\"");
                    int end = line.lastIndexOf("\"");
                    if (start >= 0 && end > start) {
                        return line.substring(start + 1, end);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "0.0"; // fallback if not found
    }

    
    private static void handleUpdateRequest(Socket socket) {
        File chatClientFile = new File(filesDir, "ChatClient.java");
        String latestVersion = getLatestClientVersion(chatClientFile);
    
        try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            // send version first
            dos.writeUTF(latestVersion);
    
            if (!chatClientFile.exists()) {
                dos.writeUTF("NO_UPDATE");
                return;
            }
    
            dos.writeUTF(chatClientFile.getName());
            dos.writeLong(chatClientFile.length());
    
            try (FileInputStream fis = new FileInputStream(chatClientFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    dos.write(buffer, 0, read);
                }
            }
    
            System.out.println("Sent ChatClient update: v" + latestVersion);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void shutdownServer() {
        broadcast("⚠️ Server disconnected");
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    client.out.println("SERVER_CLOSED");
                    client.socket.close();
                } catch (IOException ignored) {}
            }
        }
        try { serverSocket.close(); } catch (IOException ignored) {}
        System.exit(0);
    }

    private static void kickUser(String target) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.username != null && client.username.equalsIgnoreCase(target)) {
                    client.out.println("[KICK] You have been kicked by the server.");
                    try { client.socket.close(); } catch (IOException ignored) {}
                    clients.remove(client);
                    broadcast("⛔ " + client.username + " was kicked by the server.");
                    break;
                }
            }
        }
    }

    private static String getUniqueUsername(String base) {
        String newName = (base == null || base.trim().isEmpty()) ? "Anonymous" : base.trim();
        int count = 1;
        synchronized (clients) {
            boolean exists;
            do {
                exists = false;
                for (ClientHandler c : clients) {
                    if (c.username != null && c.username.equalsIgnoreCase(newName)) {
                        exists = true;
                        newName = base + "(" + count + ")";
                        count++;
                        break;
                    }
                }
            } while (exists);
        }
        return newName;
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            super("client-" + socket.getPort());
            this.socket = socket;
        }

        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                // First message is username (client sends it immediately)
                // We still print a prompt for compatibility with dumb clients.
                out.println("Enter your username:");
                String requested = in.readLine();
                username = getUniqueUsername(requested);

                // Add to client set only after username determined
                synchronized (clients) { clients.add(this); }

                // Tell client final username (client expects this)
                out.println("[USERNAME] " + username);

                // Send chat history
                synchronized (messageHistory) {
                    for (String oldMsg : messageHistory) {
                        out.println(oldMsg);
                    }
                }

                // Send current user list
                sendUserList();

                // Tip
                out.println("[INFO] Type /help to view all commands");

                broadcast("🔵 " + username + " has joined the chat!");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/help")) {
                        out.println(CLIENT_HELP);
                        continue;
                    }
                    if (message.startsWith("/users")) {
                        sendUserList();
                        continue;
                    }
                    if (message.equals("/files")) {
                        out.println(listFilesLine());
                        continue;
                    }
                    if (message.startsWith("/typing")) {
                        broadcastTyping(username);
                        continue;
                    }
                    if (message.startsWith("/file ")) {
                        handleFileUpload(message);
                        continue;
                    }
                    if (message.startsWith("/get ")) {
                        handleFileDownload(message);
                        continue;
                    }

                    // Normal chat: emoji + markup normalization is done server-side globally
                    String parsedMessage = applyGlobalParsing(message);
                    broadcast(username + ": " + parsedMessage);
                }
            } catch (IOException e) {
                System.out.println((username != null ? username : "unknown") + " disconnected.");
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                clients.remove(this);
                if (username != null) {
                    broadcast("🔴 " + username + " has left the chat!");
                }
            }
        }

        private void sendUserList() {
            StringBuilder sb = new StringBuilder("👥 Online: ");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    sb.append(client.username).append(", ");
                }
            }
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            out.println(sb.toString());
        }

        private String listFilesLine() {
            File[] files = filesDir.listFiles();
            if (files == null || files.length == 0) return "📂 No files uploaded.";
            StringBuilder sb = new StringBuilder("📂 Files: ");
            for (File f : files) {
                sb.append(f.getName()).append(" (").append(f.length()).append(" bytes), ");
            }
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            return sb.toString();
        }

        // Supports:
        // 1) New client:   /file <filename> <base64>
        // 2) Legacy flow:  /file <filename> <size>  (followed by <size> raw bytes on the same socket)
        private void handleFileUpload(String command) {
            try {
                String[] parts = command.split(" ", 3);
                if (parts.length < 3) {
                    out.println("[ERROR] Usage: /file <filename> <base64 | size>");
                    return;
                }

                String rawName = parts[1];
                String filename = sanitizeFilename(rawName);

                // If 3rd token is a number, treat as legacy size+raw-bytes flow
                Long sizeIfNumeric = tryParseLong(parts[2]);
                if (sizeIfNumeric != null) {
                    long size = sizeIfNumeric;
                    File outFile = uniqueFile(filesDir, filename);
                    // Informational line (optional; the client doesn't rely on it)
                    out.println("[INFO] Ready to receive file: " + outFile.getName() + " (" + size + " bytes)");

                    // Read raw bytes directly from the socket InputStream
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        InputStream is = socket.getInputStream();
                        byte[] buffer = new byte[4096];
                        long remaining = size;
                        while (remaining > 0) {
                            int read = is.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (read < 0) break;
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }

                    fileIndex.put(outFile.getName(), outFile);
                    broadcast("📎 " + username + " uploaded file: " + outFile.getName() +
                              " (" + size + " bytes). Download with /get " + outFile.getName());
                    return;
                }

                // Otherwise treat parts[2] as base64
                String base64 = parts[2];
                byte[] data;
                try {
                    data = Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException iae) {
                    out.println("[ERROR] Invalid Base64 data.");
                    return;
                }

                File outFile = uniqueFile(filesDir, filename);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(data);
                }

                fileIndex.put(outFile.getName(), outFile);
                broadcast("📎 " + username + " uploaded file: " + outFile.getName() +
                          " (" + outFile.length() + " bytes). Download with /get " + outFile.getName());

            } catch (Exception e) {
                out.println("[ERROR] File upload failed: " + e.getMessage());
            }
        }

        private void handleFileDownload(String command) {
            try {
                String[] parts = command.split(" ", 2);
                if (parts.length < 2) {
                    out.println("[ERROR] Usage: /get <filename>");
                    return;
                }
                String filename = sanitizeFilename(parts[1]);
                File file = fileIndex.get(filename);
                if (file == null) file = new File(filesDir, filename);

                if (!file.exists() || !file.isFile()) {
                    out.println("[ERROR] File not found: " + filename);
                    return;
                }

                // Let client know a binary payload is coming (protocol hint)
                out.println("[DOWNLOAD] " + file.getName() + " " + file.length());

                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = socket.getOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    os.flush();
                }

            } catch (Exception e) {
                out.println("[ERROR] File download failed: " + e.getMessage());
            }
        }
    }

    // ---- broadcast + typing ----

    private static void broadcast(String msg) {
        // Global parsing of user/console messages (emojis + markup normalization)
        String body = applyGlobalParsing(msg);

        String message = "[" + timeFormat.format(new Date()) + "] " + body;

        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.out.println(message);
            }
        }

        synchronized (messageHistory) {
            messageHistory.add(message);
            if (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(0);
            }
        }

        System.out.println(message);
    }

    private static void broadcastTyping(String user) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.username != null && !client.username.equals(user)) {
                    client.out.println("[TYPING] " + user + " is typing…");
                }
            }
        }
    }

    // ---- emoji + markup (global) ----

    private static String applyGlobalParsing(String msg) {
        // order: first emoji replacements, then markup normalization
        String withEmojis = replaceEmojis(msg);
        return normalizeMarkup(withEmojis);
    }

    private static String replaceEmojis(String msg) {
        String[][] emojis = {
            {":smile:", "😄"},
            {":grin:", "😁"},
            {":joy:", "😂"},
            {":rofl:", "🤣"},
            {":wink:", "😉"},
            {":blush:", "😊"},
            {":sunglasses:", "😎"},
            {":thinking:", "🤔"},
            {":neutral:", "😐"},
            {":cry:", "😢"},
            {":sob:", "😭"},
            {":angry:", "😠"},
            {":rage:", "😡"},
            {":skull:", "💀"},
            {":fire:", "🔥"},
            {":thumbs:", "👍"},
            {":thumbsdown:", "👎"},
            {":heart:", "❤️"},
            {":broken_heart:", "💔"},
            {":100:", "💯"},
            {":star:", "⭐"},
            {":sparkles:", "✨"},
            {":zap:", "⚡"},
            {":check:", "✔️"},
            {":x:", "❌"},
            {":wave:", "👋"},
            {":clap:", "👏"},
            {":pray:", "🙏"},
            {":ok_hand:", "👌"},
            {":eyes:", "👀"},
            {":poop:", "💩"},
            {":alien:", "👽"},
            {":robot:", "🤖"},
            {":cat:", "🐱"},
            {":dog:", "🐶"},
            {":dragon:", "🐉"},
            {":ghost:", "👻"},
            {":pumpkin:", "🎃"},
            {":snowflake:", "❄️"},
            {":christmas_tree:", "🎄"}
        };
    
        for (String[] emoji : emojis) {
            msg = msg.replace(emoji[0], emoji[1]);
        }
        return msg;
    }


    /**
     * Normalize custom markup:
     * - Supports [b] [/b], [i] [/i], [u] [/u]
     * - Ignores unknown bracketed tokens
     * - Discards stray closing tags
     * - Auto-closes any leftover open tags at the end (LIFO)
     * - Does NOT touch bracketed timestamps like [12:34]
     */
    private static String normalizeMarkup(String input) {
        if (input == null || input.isEmpty()) return input;

        final String[] OPEN_TAGS  = {"[b]", "[i]", "[u]"};
        final String[] CLOSE_TAGS = {"[/b]", "[/i]", "[/u]"};
        final Map<String, String> OPEN_TO_CLOSE = new HashMap<>();
        OPEN_TO_CLOSE.put("[b]", "[/b]");
        OPEN_TO_CLOSE.put("[i]", "[/i]");
        OPEN_TO_CLOSE.put("[u]", "[/u]");

        final Map<String, String> CLOSE_TO_OPEN = new HashMap<>();
        CLOSE_TO_OPEN.put("[/b]", "[b]");
        CLOSE_TO_OPEN.put("[/i]", "[i]");
        CLOSE_TO_OPEN.put("[/u]", "[u]");

        Deque<String> stack = new ArrayDeque<>();
        StringBuilder out = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            // try to match known open/close tokens
            String token = null;

            // check open tokens
            for (String t : OPEN_TAGS) {
                if (input.startsWith(t, i)) { token = t; break; }
            }
            // check close tokens
            if (token == null) {
                for (String t : CLOSE_TAGS) {
                    if (input.startsWith(t, i)) { token = t; break; }
                }
            }

            if (token == null) {
                // normal char
                out.append(input.charAt(i));
                i++;
                continue;
            }

            // handle token
            if (OPEN_TO_CLOSE.containsKey(token)) {
                // opening tag
                stack.push(token);
                out.append(token);
            } else {
                // closing tag
                String neededOpen = CLOSE_TO_OPEN.get(token);
                if (neededOpen == null) {
                    // unknown token, ignore
                } else if (stack.isEmpty()) {
                    // stray closing, drop it
                } else {
                    // pop until matching
                    while (!stack.isEmpty() && !stack.peek().equals(neededOpen)) {
                        out.append(OPEN_TO_CLOSE.get(stack.pop())); // auto-close mismatched open
                    }
                    if (!stack.isEmpty() && stack.peek().equals(neededOpen)) {
                        stack.pop();
                        out.append(token); // append the actual closing tag
                    }
                }
            }
            i += token.length();
        }

        // close any remaining open tags
        while (!stack.isEmpty()) {
            out.append(OPEN_TO_CLOSE.get(stack.pop()));
        }

        return out.toString();
    }

    // ---- helpers: filenames, numbers, unique files ----

    private static String sanitizeFilename(String name) {
        if (name == null) return "file";
        // keep letters, digits, dot, dash, underscore; strip the rest
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        // prevent sneaky parent dirs
        cleaned = cleaned.replace("..", "_");
        if (cleaned.isEmpty()) cleaned = "file";
        return cleaned;
    }

    private static Long tryParseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return null; }
    }

    private static File uniqueFile(File dir, String baseName) {
        File f = new File(dir, baseName);
        if (!f.exists()) return f;
        String name = baseName;
        String stem = name;
        String ext  = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext  = name.substring(dot); // includes dot
        }
        int i = 1;
        do {
            f = new File(dir, stem + "(" + i + ")" + ext);
            i++;
        } while (f.exists());
        return f;
    }
}
