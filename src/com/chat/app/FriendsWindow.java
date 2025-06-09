package com.chat.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class FriendsWindow extends JFrame {
    private String username;
    private JPanel friendsPanel;

    public FriendsWindow(String username) {
        this.username = username;
        setTitle("Your Friends");
        setSize(400, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        friendsPanel = new JPanel();
        friendsPanel.setLayout(new BoxLayout(friendsPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(friendsPanel);
        add(scrollPane, BorderLayout.CENTER);

        JButton backButton = new JButton("Back to Main Menu");
        backButton.addActionListener(e -> {
            dispose();
            new MainMenu(username);
        });

        JButton startNewChat = new JButton("Start New Chat");
        startNewChat.addActionListener(e -> {
            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start new chat:");
            if (friendUsername != null && !friendUsername.trim().isEmpty()) {
                new ChatClient(username, friendUsername.trim());
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startNewChat);
        buttonPanel.add(backButton);
        add(buttonPanel, BorderLayout.SOUTH);

        loadFriends();
        setVisible(true);
    }

    private void loadFriends() {
        List<FriendEntry> friends = getChatFriends(username);
        friends.sort((f1, f2) -> f2.timestamp.compareTo(f1.timestamp)); // latest first

        for (FriendEntry friend : friends) {
            JPanel panel = new JPanel(new BorderLayout());
            JLabel nameLabel = new JLabel(friend.friend + " - " + (friend.isOnline ? "Online" : "Offline"));
            JButton chatButton = new JButton("Chat");

            chatButton.addActionListener(e -> {
                new ChatClient(username, friend.friend);
            });

            panel.add(nameLabel, BorderLayout.CENTER);
            panel.add(chatButton, BorderLayout.EAST);
            panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            friendsPanel.add(panel);
        }

        friendsPanel.revalidate();
        friendsPanel.repaint();
    }

    private List<FriendEntry> getChatFriends(String username) {
        List<FriendEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                     " SELECT receiver AS friend, MAX(timestamp) AS ts FROM messages WHERE sender = ? GROUP BY receiver " +
                     " UNION " +
                     " SELECT sender AS friend, MAX(timestamp) AS ts FROM messages WHERE receiver = ? GROUP BY sender " +
                     ") AS combined ORDER BY ts DESC";

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp", "root", "Chandu@96");
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, username);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String friend = rs.getString("friend");
                Timestamp ts = rs.getTimestamp("ts");
                boolean isOnline = ChatServer.isUserOnline(friend); // Static method in ChatServer
                list.add(new FriendEntry(friend, ts, isOnline));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    private static class FriendEntry {
        String friend;
        Timestamp timestamp;
        boolean isOnline;

        public FriendEntry(String friend, Timestamp timestamp, boolean isOnline) {
            this.friend = friend;
            this.timestamp = timestamp;
            this.isOnline = isOnline;
        }
    }
}
