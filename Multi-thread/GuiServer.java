import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class GuiServer extends JFrame {

    private final JTextArea logArea = new JTextArea(20, 50);
    private final JButton startButton = new JButton("Start Server");
    private final JButton stopButton = new JButton("Stop Server");
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread serverThread;

    public GuiServer() {
        setTitle("Multithreaded Server GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Setup UI
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        stopButton.setEnabled(false);

        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        // Button actions
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
    }

    private void startServer() {
        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        threadPool = Executors.newFixedThreadPool(10);

        serverThread = new Thread(() -> {
            int port = 8010;
            try {
                serverSocket = new ServerSocket(port);
                log("Server started on port " + port);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.execute(() -> handleClient(clientSocket));
                    } catch (IOException ex) {
                        if (running) log("Error accepting client: " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                log("Could not start server: " + ex.getMessage());
            }
        });

        serverThread.start();
    }

    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ex) {
            log("Error closing server: " + ex.getMessage());
        }

        threadPool.shutdownNow();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        log("Server stopped.");
    }

    private void handleClient(Socket clientSocket) {
        try (
            PrintWriter toClient = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            String input = fromClient.readLine();
            log("Client says: " + input);
            toClient.println("Hello from server " + clientSocket.getInetAddress());
        } catch (IOException e) {
            log("Client error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                log("Failed to close client socket.");
            }
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GuiServer().setVisible(true));
    }
}
