package com.chat.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class GroupChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    private String username;
    private int groupId;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;

    public GroupChatClient(String username, int groupId) {
        this.username = username;
        this.groupId = groupId;
        createGUI();
        startClient();
    }

    private void createGUI() {
        frame = new JFrame("Group Chat - Group #" + groupId + " (" + username + ")");
        chatArea = new JTextArea(20, 40);
        chatArea.setEditable(false);
        inputField = new JTextField(30);
        sendButton = new JButton("Send");

        JPanel panel = new JPanel();
        panel.add(inputField);
        panel.add(sendButton);

        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    if (out != null) out.writeObject("[DISCONNECT]");
                    if (socket != null) socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void startClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Send username and dummy friend (to keep protocol compatible)
            out.writeObject(username);
            out.flush();
            out.writeObject("group::" + groupId);
            out.flush();

            new Thread(this::listenForMessages).start();
        } catch (IOException e) {
            showError("Failed to connect to the server.");
            e.printStackTrace();
        }
    }

    private void listenForMessages() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof String msg) {
                    chatArea.append(msg + "\n");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            chatArea.append("Disconnected from group.\n");
        }
    }

    private void sendMessage() {
        try {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                out.writeObject("group::" + groupId);
                out.writeObject(message);
                out.flush();
                chatArea.append("You: " + message + "\n");
                inputField.setText("");
            }
        } catch (IOException e) {
            showError("Failed to send message.");
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Entry point for testing
    public static void main(String[] args) {
        String username = JOptionPane.showInputDialog("Enter your username:");
        String groupIdStr = JOptionPane.showInputDialog("Enter group ID:");
        if (username != null && groupIdStr != null && !username.isEmpty() && !groupIdStr.isEmpty()) {
            new GroupChatClient(username, Integer.parseInt(groupIdStr));
        }
    }
}
