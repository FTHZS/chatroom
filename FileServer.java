import java.io.*;
import java.net.*;
import java.util.*;

public class FileServer {
    private static final int PORT = 34567; // different port for file server
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("File server started on port " + PORT);

        // Thread to read console input for sending files
        new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.println("press . to send updated ChatClient.java file");
                    scanner.next();
                    String path = "/home/student/.mozilla/folder/v6/Chatroom/chatroom_server/ChatClient.java";
                    //String path = scanner.nextLine().trim();
                    if (path.equalsIgnoreCase("exit")) System.exit(0);

                    File file = new File(path);
                    if (!file.exists() || !file.isFile()) {
                        System.out.println("File not found!");
                        continue;
                    }

                    broadcastFile(file);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private OutputStream out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = socket.getOutputStream();
                System.out.println("Client connected: " + socket.getInetAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Broadcast file to all connected clients
    private static void broadcastFile(File file) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    sendFile(client.out, file);
                } catch (IOException e) {
                    System.out.println("Failed to send file to client: " + client.socket.getInetAddress());
                    e.printStackTrace();
                }
            }
        }
        System.out.println("File '" + file.getName() + "' sent to all clients.");
    }

    private static void sendFile(OutputStream out, File file) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        // Send file name and length
        dos.writeUTF(file.getName());
        dos.writeLong(file.length());

        // Send file content
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }
}
