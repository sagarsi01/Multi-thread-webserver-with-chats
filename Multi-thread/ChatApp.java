import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

public class ChatApp extends JFrame {

    // Server GUI components
    private final JTextArea chatArea = new JTextArea(25, 50);
    private final JLabel clientsLabel = new JLabel("Clients connected: 0");
    private final JButton startServerBtn = new JButton("Start Server");
    private final JButton stopServerBtn = new JButton("Stop Server");
    private final JButton openClientBtn = new JButton("Open Chat Client");

    // Server variables
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread serverThread;
    private final Set<PrintWriter> clientWriters = ConcurrentHashMap.newKeySet();

    public ChatApp() {
        setTitle("Chat Server with Client Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel topPanel = new JPanel(new BorderLayout());
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 15));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        topPanel.add(clientsLabel, BorderLayout.WEST);

        JPanel serverButtonsPanel = new JPanel();
        serverButtonsPanel.add(startServerBtn);
        serverButtonsPanel.add(stopServerBtn);
        serverButtonsPanel.add(openClientBtn);

        stopServerBtn.setEnabled(false);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(serverButtonsPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Button listeners
        startServerBtn.addActionListener(e -> startServer());
        stopServerBtn.addActionListener(e -> stopServer());
        openClientBtn.addActionListener(e -> openChatClient());
    }

    private void startServer() {
        running = true;
        startServerBtn.setEnabled(false);
        stopServerBtn.setEnabled(true);
        threadPool = Executors.newCachedThreadPool();

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

        if (threadPool != null) threadPool.shutdownNow();
        clientWriters.clear();

        startServerBtn.setEnabled(true);
        stopServerBtn.setEnabled(false);
        updateClientCount();
        log("Server stopped.");
    }

    private void handleClient(Socket clientSocket) {
        String username = null;
        try (
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter toClient = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // Read username as first message
            username = fromClient.readLine();

            // If username is null or empty, use client IP
            if (username == null || username.trim().isEmpty()) {
                username = clientSocket.getInetAddress().toString();
            } else {
                username = username.trim();
            }

            clientWriters.add(toClient);
            updateClientCount();

            log(username + " connected from " + clientSocket.getInetAddress());

            String message;
            while ((message = fromClient.readLine()) != null) {
                String fullMessage = username + ": " + message;
                log(fullMessage);
                broadcastMessage(fullMessage);
            }
        } catch (IOException e) {
            log("Client error: " + e.getMessage());
        } finally {
            clientWriters.removeIf(PrintWriter::checkError);
            updateClientCount();
            try {
                clientSocket.close();
            } catch (IOException e) {
                log("Failed to close client socket.");
            }
            log(username + " disconnected.");
        }
    }

    private void broadcastMessage(String message) {
        for (PrintWriter writer : clientWriters) {
            writer.println(message);
        }
    }

    private void updateClientCount() {
        SwingUtilities.invokeLater(() -> clientsLabel.setText("Clients connected: " + clientWriters.size()));
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void openChatClient() {
        SwingUtilities.invokeLater(() -> {
            ChatClientGui clientGui = new ChatClientGui();
            clientGui.setVisible(true);
        });
    }

    // Inner class for Chat Client GUI
    private static class ChatClientGui extends JFrame {
        private JTextArea chatArea = new JTextArea(25, 50);
        private JTextField inputField = new JTextField(40);
        private JButton sendButton = new JButton("Send");
        private Socket socket;
        private BufferedReader fromServer;
        private PrintWriter toServer;
        private Thread listenerThread;

        public ChatClientGui() {
            setTitle("Chat Client GUI");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout(10,10));

            chatArea.setEditable(false);
            chatArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
            JScrollPane scrollPane = new JScrollPane(chatArea);

            JPanel inputPanel = new JPanel();
            inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
            sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
            inputPanel.add(inputField);
            inputPanel.add(sendButton);

            add(scrollPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(null);

            // Ask for username before connecting
            String username = JOptionPane.showInputDialog(this, "Enter your username:", "Username", JOptionPane.PLAIN_MESSAGE);
            if (username == null) {
                // User cancelled input -> use IP as username (will be assigned on server side)
                username = "";
            } else if (username.trim().isEmpty()) {
                // Empty input -> use IP as username
                username = "";
            } else {
                username = username.trim();
            }

            sendButton.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());

            connectToServer("localhost", 8010, username);
        }

        private void connectToServer(String host, int port, String username) {
            try {
                socket = new Socket(host, port);
                fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                toServer = new PrintWriter(socket.getOutputStream(), true);

                // Send username as the first message (can be empty)
                toServer.println(username);

                listenerThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = fromServer.readLine()) != null) {
                            appendMessage(line);
                        }
                    } catch (IOException e) {
                        appendMessage("Disconnected from server.");
                    }
                });
                listenerThread.start();

                String displayName = username.isEmpty() ? socket.getLocalAddress().toString() : username;
                appendMessage("Connected to chat server at " + host + ":" + port + " as " + displayName);
            } catch (IOException e) {
                appendMessage("Unable to connect to server: " + e.getMessage());
                sendButton.setEnabled(false);
                inputField.setEditable(false);
            }
        }

        private void sendMessage() {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                toServer.println(message);
                inputField.setText("");
            }
        }

        private void appendMessage(String message) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append(message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatApp().setVisible(true));
    }
}
