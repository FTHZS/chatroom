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

            // Delete server_files directory recursively
            File serverDir = new File("server_files");
            if (serverDir.exists()) {
                deleteDirectory(serverDir);
                System.out.println("Deleted directory: server_files");
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

    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        if (!dir.delete()) {
            System.out.println("Failed to delete: " + dir.getPath());
        }
    }
}
