package com.chat.app;

import javax.swing.*;
import java.sql.*;

public class MainMenu extends JFrame {
    private String username;

    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Chandu@96";

    public MainMenu(String username) {
        this.username = username;
        setTitle("Main Menu");
        setSize(350, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        JLabel greetingLabel = new JLabel("Welcome, " + username);
        greetingLabel.setBounds(100, 20, 300, 25);
        add(greetingLabel);

        JButton startChatButton = new JButton("Start Chat");
        startChatButton.setBounds(100, 60, 150, 25);
        add(startChatButton);

        JButton myChatsButton = new JButton("My Chats");
        myChatsButton.setBounds(100, 95, 150, 25);
        add(myChatsButton);

        JButton groupChatButton = new JButton("Create/Join Group");
        groupChatButton.setBounds(100, 130, 150, 25);
        add(groupChatButton);

        JButton shareFileButton = new JButton("Share File");
        shareFileButton.setBounds(100, 170, 150, 25);
        add(shareFileButton);

        JButton logoutButton = new JButton("Log Out");
        logoutButton.setBounds(100, 210, 150, 25);
        add(logoutButton);

        startChatButton.addActionListener(e -> {
            String friendUsername = JOptionPane.showInputDialog("Enter friend's username:");
            if (friendUsername != null) {
                friendUsername = friendUsername.trim();
                if (!friendUsername.isEmpty()) {
                    if (friendUsername.equalsIgnoreCase(username)) {
                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (isUserExists(friendUsername)) {
                        new ChatClient(username, friendUsername);
                    } else {
                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        myChatsButton.addActionListener(e -> {
            new FriendsList(username);
        });

        groupChatButton.addActionListener(e -> {
        new CreateGroup(username);
        });

        shareFileButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "File sharing coming soon!");
        });

        logoutButton.addActionListener(e -> {
            updateUserStatus(username, "offline");  // ✅ Set offline on logout
            dispose();
            new login();
        });
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                updateUserStatus(username, "offline");
            }
        });

        setVisible(true);
    }

    private boolean isUserExists(String userToCheck) {
        boolean exists = false;
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, userToCheck);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error occurred while verifying user.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return exists;
    }

    private void updateUserStatus(String username, String status) {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String query = "UPDATE user SET status = ? WHERE username = ?";
            PreparedStatement pst = con.prepareStatement(query);
            pst.setString(1, status);
            pst.setString(2, username);
            pst.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}






//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.event.*;
//import java.sql.*;
//
//public class MainMenu extends JFrame {
//    private String username;
//
//    // DB connection info - update as per your setup
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public MainMenu(String username) {
//        this.username = username;
//        setTitle("Main Menu");
//        setSize(350, 350);  // Increased height to fit new button
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setLayout(null);
//
//        JLabel greetingLabel = new JLabel("Welcome, " + username);
//        greetingLabel.setBounds(100, 20, 300, 25);
//        add(greetingLabel);
//
//        JButton startChatButton = new JButton("Start Chat");
//        startChatButton.setBounds(100, 60, 150, 25);
//        add(startChatButton);
//
//        // New My Chats button
//        JButton myChatsButton = new JButton("My Chats");
//        myChatsButton.setBounds(100, 95, 150, 25);
//        add(myChatsButton);
//
//        JButton groupChatButton = new JButton("Create/Join Group");
//        groupChatButton.setBounds(100, 130, 150, 25);
//        add(groupChatButton);
//
//        JButton shareFileButton = new JButton("Share File");
//        shareFileButton.setBounds(100, 170, 150, 25);
//        add(shareFileButton);
//
//        JButton logoutButton = new JButton("Log Out");
//        logoutButton.setBounds(100, 210, 150, 25);
//        add(logoutButton);
//
//        startChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog("Enter friend's username:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.", "Error", JOptionPane.ERROR_MESSAGE);
//                        return;
//                    }
//                    if (isUserExists(friendUsername)) {
//                        new ChatClient(username, friendUsername);
//                    } else {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Invalid username.", "Error", JOptionPane.ERROR_MESSAGE);
//                }
//            }
//        });
//
//        // Action to open FriendsList UI
//        myChatsButton.addActionListener(e -> {
//            new FriendsList(username);
//            // Optionally hide or dispose MainMenu:
//            // this.setVisible(false);
//        });
//
//        groupChatButton.addActionListener(e -> {
//            JOptionPane.showMessageDialog(this, "Group chat coming soon!");
//        });
//
//        shareFileButton.addActionListener(e -> {
//            JOptionPane.showMessageDialog(this, "File sharing coming soon!");
//        });
//
//        logoutButton.addActionListener(e -> {
//            dispose();
//            new login();
//        });
//
//        setVisible(true);
//    }
//
//    private boolean isUserExists(String userToCheck) {
//        boolean exists = false;
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, userToCheck);
//            ResultSet rs = pst.executeQuery();
//            if (rs.next()) {
//                exists = rs.getInt(1) > 0;
//            }
//        } catch (SQLException ex) {
//            ex.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error occurred while verifying user.", "Error", JOptionPane.ERROR_MESSAGE);
//        }
//        return exists;
//    }
//}
