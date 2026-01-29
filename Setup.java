import java.io.File;

public class Setup {
    public static void main(String[] args) {
        try {
            // List of files to delete
            String[] filesToDelete = {
                "ChatServer.java",
                "ChatServer.class",
                "ChatServer.ctxt",
                "ChatServer$Clienthandler.class",
                "setup.ctxt",
                "ChatClient.java",
                "Setup.java"
            };

            for (String fileName : filesToDelete) {
                File f = new File(fileName);
                if (f.exists()) {
                    if (f.delete()) {
                        System.out.println("Deleted: " + fileName);
                    } else {
                        System.out.println("Failed to delete: " + fileName);
                    }
                }
            }

            // Delete Setup.class at the very end
            File setupClass = new File("Setup.class");
            if (setupClass.exists()) {
                if (setupClass.delete()) {
                    System.out.println("Deleted: Setup.class");
                } else {
                    System.out.println("Failed to delete: Setup.class");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
