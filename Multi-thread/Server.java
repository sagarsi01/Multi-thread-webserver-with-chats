import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

public class Server {

    // Thread-safe set of client output streams for broadcasting
    private final Set<PrintWriter> clientWriters = ConcurrentHashMap.newKeySet();

    public void handleClient(Socket clientSocket) {
        String username = null;
        try (
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter toClient = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // Read username from client (first message)
            username = fromClient.readLine();
            if (username == null || username.trim().isEmpty()) {
                username = clientSocket.getInetAddress().toString();
            } else {
                username = username.trim();
            }

            clientWriters.add(toClient);
            System.out.println(username + " connected.");

            // Inform client of successful connection
            toClient.println("Welcome, " + username + "!");

            // Broadcast that user has joined
            broadcast(username + " has joined the chat.");

            // Read messages from client and broadcast
            String message;
            while ((message = fromClient.readLine()) != null) {
                broadcast(username + ": " + message);
            }
        } catch (IOException e) {
            System.err.println("Error with client " + username + ": " + e.getMessage());
        } finally {
            if (username != null) {
                System.out.println(username + " disconnected.");
                broadcast(username + " has left the chat.");
            }
            clientWriters.removeIf(PrintWriter::checkError);
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Failed to close client socket.");
            }
        }
    }

    private void broadcast(String message) {
        for (PrintWriter writer : clientWriters) {
            writer.println(message);
        }
    }

    public static void main(String[] args) {
        int port = 8010;
        Server server = new Server();
        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(() -> server.handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}
