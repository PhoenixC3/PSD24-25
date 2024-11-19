import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class ResetDB {

    public static void main(String[] args) {
        // Define paths
        String keystoresPath = "keystores";
        String truststoresPath = "truststores";
        String peersDbPath1 = "peers8080.db";
        String peersDbPath2 = "peers8081.db";
        String peersDbPath3 = "peers8082.db";

        // Define files to exclude
        String excludeKeystore = "server_keystore.jks";
        String excludeTruststore1 = "general_truststore.jks";
        String excludeTruststore2 = "server_truststore.jks";

        // Clean up keystores folder
        deleteFilesInDirectoryExcept(keystoresPath, excludeKeystore);

        // Clean up truststores folder
        deleteFilesInDirectoryExcept(truststoresPath, excludeTruststore1, excludeTruststore2);

        // Delete peers.db if it exists
        deleteFile(peersDbPath1);
        deleteFile(peersDbPath2);
        deleteFile(peersDbPath3);
    }

    /**
     * Deletes all files in the specified directory except for those that match the exclusion list.
     */
    private static void deleteFilesInDirectoryExcept(String directoryPath, String... exclusions) {
        Path dirPath = Paths.get(directoryPath);
        
        // Check if directory exists
        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    boolean isExcluded = false;

                    // Check if file is in the exclusion list
                    for (String exclude : exclusions) {
                        if (fileName.equalsIgnoreCase(exclude)) {
                            isExcluded = true;
                            break;
                        }
                    }

                    // Delete the file if it's not excluded
                    if (!isExcluded && Files.isRegularFile(entry)) {
                        System.out.println("Deleting file: " + entry);
                        Files.delete(entry);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error cleaning up directory " + directoryPath + ": " + e.getMessage());
            }
        } else {
            System.out.println("Directory does not exist: " + directoryPath);
        }
    }

    /**
     * Deletes a file if it exists.
     */
    private static void deleteFile(String filePath) {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            try {
                System.out.println("Deleting file: " + filePath);
                Files.delete(path);
            } catch (IOException e) {
                System.err.println("Error deleting file " + filePath + ": " + e.getMessage());
            }
        } else {
            System.out.println("File does not exist: " + filePath);
        }
    }
}
