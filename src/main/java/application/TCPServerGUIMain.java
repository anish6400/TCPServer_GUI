package application;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class TCPServerGUIMain {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private JTextPane logTextPane; // Updated to JTextPane
    private JTextField portField;
    private JButton startButton;
    private JButton stopButton;
    private StyledDocument doc;

    private void log(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            Style style = logTextPane.addStyle("LogStyle", null);
            StyleConstants.setFontFamily(style, "SansSerif");
            StyleConstants.setFontSize(style, 12);

            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String logMessage = "[" + timestamp + "] ";

            // Set the text color for the timestamp to green
            StyleConstants.setForeground(style, Color.GREEN);

            try {
                doc.insertString(doc.getLength(), logMessage, style);

                // Append the message with the specified color
                StyleConstants.setForeground(style, color);
                doc.insertString(doc.getLength(), message + "\n", style);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public TCPServerGUIMain() {
        JFrame mainFrame = new JFrame("TCP Server");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 500); // Adjusted window size

        JPanel controlPanel = new JPanel();
        portField = new JTextField("12345", 5);
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        controlPanel.add(new JLabel("Port: "));
        controlPanel.add(portField);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        logTextPane = new JTextPane(); // Updated to JTextPane
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Set a monospaced font
        DefaultCaret caret = (DefaultCaret) logTextPane.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Auto-scroll to the bottom
        JScrollPane scrollPane = new JScrollPane(logTextPane);

        mainFrame.setLayout(new BorderLayout());
        mainFrame.add(controlPanel, BorderLayout.NORTH);
        mainFrame.add(scrollPane, BorderLayout.CENTER);

        mainFrame.setVisible(true);

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int port = Integer.parseInt(portField.getText());
                new Thread(() -> startServer(port)).start();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopServer();
            }
        });

        doc = logTextPane.getStyledDocument();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TCPServerGUIMain();
        });
    }

    private void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            log("Server started on port " + port, Color.BLUE); // Log in blue color

            portField.setEnabled(false);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Create a new thread to handle the client connection
                ClientHandler clientThread = new ClientHandler(clientSocket);
                clients.add(clientThread);
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopServer() {
        try {
            serverSocket.close();
            log("Server stopped.", Color.RED); // Log in red color
            portField.setEnabled(true);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);

            // Use an iterator to safely remove clients
            Iterator<ClientHandler> iterator = clients.iterator();
            while (iterator.hasNext()) {
                ClientHandler client = iterator.next();
                client.closeClient();
                iterator.remove(); // Remove the client using the iterator
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Thread for handling a client connection
    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private JFrame frame;
        private JTextPane logTextPane; // Updated to JTextPane
        private JTextPane messagePane; // Updated to JTextPane with height
        private JButton sendButton;
        private PrintWriter out;
        private StyledDocument doc; // Separate StyledDocument for ClientHandler

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;

            frame = new JFrame("Client: " + socket.getInetAddress());
            frame.setSize(800, 500); // Adjusted window size
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            logTextPane = new JTextPane(); // Updated to JTextPane
            logTextPane.setEditable(false);
            logTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Set a monospaced font
            DefaultCaret caret = (DefaultCaret) logTextPane.getCaret();
            caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Auto-scroll to the bottom
            JScrollPane scrollPane = new JScrollPane(logTextPane);

            messagePane = new JTextPane(); // Updated to JTextPane with height
            messagePane.setPreferredSize(new Dimension(30, 100)); // Set the preferred size
            sendButton = new JButton("Send");

            sendButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sendMessage();
                }
            });

            JPanel inputPanel = new JPanel();
            inputPanel.setLayout(new BorderLayout()); // Use BorderLayout for better component placement
            inputPanel.add(new JScrollPane(messagePane), BorderLayout.CENTER); // Wrap messagePane in a JScrollPane
            inputPanel.add(sendButton, BorderLayout.EAST);

            frame.setLayout(new BorderLayout());
            frame.add(scrollPane, BorderLayout.CENTER);
            frame.add(inputPanel, BorderLayout.SOUTH);

            frame.setVisible(true);

            // Initialize the PrintWriter for sending messages to the client
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            doc = logTextPane.getStyledDocument(); // Initialize a separate StyledDocument
            logClient("Accepted connection from: " + clientSocket.getInetAddress(), Color.BLUE); // Log in green color
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // Log client messages with timestamp in green color
                    logClient("Received from client: " + inputLine, Color.BLACK);
                }
            } catch (SocketException se) {
                // Handle client disconnect
                logClient("Client disconnected: " + clientSocket.getInetAddress(), Color.RED); // Log in red color
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeClient();
            }
        }

        private void sendMessage() {
            String message = messagePane.getText();
            if(message.isEmpty()) {
                return;
            }
            String[] messageSplitByLine = message.split("\n");
            for(String messageLine: messageSplitByLine){
                logClient("Sent to client: " + messageLine, Color.BLACK); // Log in black color
            }
            // Send the message to the client
            out.println(message);
            messagePane.setText("");
        }

        public void closeClient() {
            try {
                clientSocket.close();
                clients.remove(this);
                logClient("Connection with client closed.", Color.RED); // Log in red color
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void logClient(String message, Color color) {
            SwingUtilities.invokeLater(() -> {
                Style style = logTextPane.addStyle("LogStyle", null);
                StyleConstants.setFontFamily(style, "SansSerif");
                StyleConstants.setFontSize(style, 12);

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String logMessage = "[" + timestamp + "] ";

                // Set the text color for the timestamp to green
                StyleConstants.setForeground(style, Color.GREEN);

                try {
                    doc.insertString(doc.getLength(), logMessage, style);
                    if (message.startsWith("Received from client: ")) {
                        StyleConstants.setForeground(style, Color.MAGENTA);
                        doc.insertString(doc.getLength(), "Received from client: ", style); // Apply a different style for "Received from client:"
                        StyleConstants.setForeground(style, color);
                        doc.insertString(doc.getLength(), message.substring(22) + "\n", style);
                    } else if (message.startsWith("Sent to client: ")) {
                        StyleConstants.setForeground(style, Color.MAGENTA);
                        doc.insertString(doc.getLength(), "Sent to client: ", style); // Apply a different style for "Sent to client: "
                        StyleConstants.setForeground(style, color);
                        doc.insertString(doc.getLength(), message.substring(16) + "\n", style); // Apply a different style for "You:"
                    } else {
                        StyleConstants.setForeground(style, color);
                        doc.insertString(doc.getLength(), message + "\n", style); // Apply a different style for "You:"
                    }
                    // Append the message with the specified color
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
