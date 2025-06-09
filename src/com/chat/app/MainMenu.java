package com.chat.app;

import javax.swing.*;
import java.awt.event.*;

public class MainMenu extends JFrame {
    private String username;

    public MainMenu(String username) {
        this.username = username;
        setTitle("Main Menu");
        setSize(350, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        JLabel greetingLabel = new JLabel("Welcome, " + username);
        greetingLabel.setBounds(100, 20, 300, 25);
        add(greetingLabel);

        JButton startChatButton = new JButton("Start Chat");
        startChatButton.setBounds(100, 60, 150, 25);
        add(startChatButton);

        JButton groupChatButton = new JButton("Create/Join Group");
        groupChatButton.setBounds(100, 100, 150, 25);
        add(groupChatButton);

        JButton shareFileButton = new JButton("Share File");
        shareFileButton.setBounds(100, 140, 150, 25);
        add(shareFileButton);

        JButton logoutButton = new JButton("Log Out");
        logoutButton.setBounds(100, 180, 150, 25);
        add(logoutButton);

        // ▶️ Open FriendsWindow on Start Chat click
        startChatButton.addActionListener(e -> {
            new FriendsWindow(username); // show friends with chat buttons
        });

        groupChatButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Group chat coming soon!");
        });

        shareFileButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "File sharing coming soon!");
        });

        logoutButton.addActionListener(e -> {
            dispose();
            new login();
        });

        setVisible(true);
    }
}
