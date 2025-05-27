import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class Server {
    
    public Consumer<Socket> getConsumer() {
        return (clientSocket) -> {
            try (
                Socket socket = clientSocket; // Ensures socket is closed automatically
                PrintWriter toSocket = new PrintWriter(socket.getOutputStream(), true)
            ) {
                toSocket.println("Hello from server " + socket.getInetAddress());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        };
    }

    public static void main(String[] args) {
        int port = 8010;
        Server server = new Server();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(70000);
            System.out.println("Server is listening on port " + port);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Thread thread = new Thread(() -> server.getConsumer().accept(clientSocket));
                    thread.start();
                } catch (IOException ex) {
                    System.err.println("Error accepting client connection: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not start the server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
