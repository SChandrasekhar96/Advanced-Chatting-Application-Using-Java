package com.chat.app;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.*;
import java.awt.image.BufferedImage;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String friendUsername;
    private String lastSentMessage = null;

    private JFrame frame;
    private JTextArea messageArea;
    private JTextField messageField;
    private JButton sendButton, deleteButton;
    private JTextField receiverField;
    private JLabel statusLabel;

    public ChatClient(String username, String friendUsername) {
        this.username = username;
        this.friendUsername = friendUsername;

        frame = new JFrame("Chat Client - " + username);
        messageArea = new JTextArea(15, 30);
        messageArea.setEditable(false);
        messageField = new JTextField(20);
        sendButton = new JButton("Send");
        deleteButton = new JButton("Delete");
        receiverField = new JTextField(friendUsername, 15);
        receiverField.setEditable(false);

        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        statusLabel = new JLabel();
        // Initialize with red icon and "checking" text
        statusLabel.setIcon(createStatusIcon(Color.RED.darker()));
        statusLabel.setText("  Chatting with: " + friendUsername);
        statusLabel.setIconTextGap(8);
        topPanel.add(statusLabel);
        frame.add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.add(new JScrollPane(messageArea));
        frame.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(messageField);
        bottomPanel.add(sendButton);
        bottomPanel.add(deleteButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setSize(450, 400);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        deleteButton.addActionListener(e -> showDeleteOptions());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (out != null) out.writeObject("[DISCONNECT]");
                    if (socket != null) socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        startClient();
    }

    public void startClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(username);
            out.flush();
            out.writeObject(friendUsername);
            out.flush();

            new Thread(this::listenForMessages).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        try {
            String receiver = receiverField.getText();
            String message = messageField.getText();
            if (!receiver.isEmpty() && !message.isEmpty()) {
                out.writeObject(receiver);
                out.writeObject(message);
                out.flush();

                lastSentMessage = message;
                messageArea.append("You: " + message + "\n");
                messageField.setText("");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showDeleteOptions() {
        if (lastSentMessage == null) {
            JOptionPane.showMessageDialog(frame, "No message to delete!");
            return;
        }

        Object[] options = {"Delete for Me", "Delete for Everyone"};
        int choice = JOptionPane.showOptionDialog(
                frame,
                "Choose delete option for last message:\n\"" + lastSentMessage + "\"",
                "Delete Message",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        try {
            if (choice == 0) {
                out.writeObject("DELETE_FOR_ME");
                out.writeObject(lastSentMessage);
                String text = messageArea.getText();
                String lineToRemove = "You: " + lastSentMessage + "\n";
                messageArea.setText(text.replaceFirst(Pattern.quote(lineToRemove), ""));
            } else if (choice == 1) {
                out.writeObject("DELETE_FOR_EVERYONE");
                out.writeObject(lastSentMessage);
                String text = messageArea.getText();
                String oldLine = "You: " + lastSentMessage + "\n";
                String newLine = "You: This message was deleted.\n";
                messageArea.setText(text.replaceFirst(Pattern.quote(oldLine), Matcher.quoteReplacement(newLine)));
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        lastSentMessage = null;
    }

    private void listenForMessages() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof String msg) {
                    if (msg.startsWith("[STATUS]")) {
                        // Format: [STATUS]username:online|offline
                        String[] parts = msg.substring(8).split(":");
                        if (parts[0].equals(friendUsername)) {
                            setStatusIcon(friendUsername, parts[1]);
                        }
                    } else if (msg.startsWith("[DELETE_NOTIFICATION]")) {
                        String content = msg.substring("[DELETE_NOTIFICATION]".length());
                        int colonIndex = content.indexOf(':');
                        if (colonIndex != -1) {
                            String sender = content.substring(0, colonIndex);
                            String deletedMsg = content.substring(colonIndex + 1);
                            replaceDeletedMessage(sender, deletedMsg);
                        }
                    } else {
                        messageArea.append(msg + "\n");
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server.");
        }
    }

    private void replaceDeletedMessage(String sender, String deletedMsg) {
        String oldLine = sender + ": " + deletedMsg;
        String newLine = sender + ": This message was deleted.";
        String text = messageArea.getText();
        messageArea.setText(text.replaceFirst(Pattern.quote(oldLine), Matcher.quoteReplacement(newLine)));
    }

    // Updates statusLabel with colored icon and text
    private void setStatusIcon(String friendUsername, String status) {
        Color color = "online".equalsIgnoreCase(status) ? Color.GREEN.darker() : Color.RED;
        statusLabel.setIcon(createStatusIcon(color));
        statusLabel.setText("  Chatting with: " + friendUsername);
        statusLabel.setIconTextGap(8);
    }

    // Creates a colored circle icon for status
    private Icon createStatusIcon(Color color) {
        int diameter = 12;
        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(0, 0, diameter, diameter);
        g2.dispose();
        return new ImageIcon(img);
    }

    public static void main(String[] args) {
        // Example: new ChatClient("chandu", "amma");
    }
}
