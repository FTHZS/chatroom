
import java.io.*;
import java.net.*;

public class FileClient {
    private static final String SERVER_IP = "192.168.1.255"; // change to server IP
    private static final int SERVER_PORT = 34567;
    
    /*
    Run this method only if your ChatClient code is corrupted.
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.writeUTF("0.0");
            dis.readUTF();

            // Read file details
            String fileName = dis.readUTF();
            long fileLength = dis.readLong();

            // Save file
            File file = new File(fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                long remaining = fileLength;
                while (remaining > 0 &&
                        (read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining))) > 0) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
