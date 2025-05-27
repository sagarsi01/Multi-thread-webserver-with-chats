import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        String serverHost = "localhost";
        int port = 8010;

        try (Socket socket = new Socket(serverHost, port);
             BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter toServer = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to chat server at " + serverHost + ":" + port);

            // Thread to listen for messages from server
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = fromServer.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("Connection closed.");
                }
            });
            listener.start();

            // Main thread reads input from user and sends to server
            while (true) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                toServer.println(input);
            }

            System.out.println("Disconnected from chat server.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
