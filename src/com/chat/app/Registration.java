package com.chat.app;

import javax.swing.*;
import java.sql.*;

public class Registration extends JFrame {
    private JTextField usernameField, fullNameField;
    private JPasswordField passwordField, confirmPassword;
    private JButton createButton, backButton;

    public Registration() {
        setTitle("User Registration");
        setSize(350, 280);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        JLabel label1 = new JLabel("Username:");
        label1.setBounds(10, 20, 80, 25);
        add(label1);

        usernameField = new JTextField();
        usernameField.setBounds(150, 20, 165, 25);
        add(usernameField);

        JLabel label2 = new JLabel("Full Name:");
        label2.setBounds(10, 60, 80, 25);
        add(label2);

        fullNameField = new JTextField();
        fullNameField.setBounds(150, 60, 165, 25);
        add(fullNameField);

        JLabel label3 = new JLabel("Password:");
        label3.setBounds(10, 100, 80, 25);
        add(label3);

        passwordField = new JPasswordField();
        passwordField.setBounds(150, 100, 165, 25);
        add(passwordField);

        JLabel label4 = new JLabel("Confirm Password:");
        label4.setBounds(10, 140, 120, 25);
        add(label4);

        confirmPassword = new JPasswordField();
        confirmPassword.setBounds(150, 140, 165, 25);
        add(confirmPassword);

        createButton = new JButton("Create Account");
        createButton.setBounds(10, 180, 130, 25);
        add(createButton);

        backButton = new JButton("Back to Login");
        backButton.setBounds(180, 180, 130, 25);
        add(backButton);

        createButton.addActionListener(e -> {
            String username = usernameField.getText();
            String fullName = fullNameField.getText();
            String password = new String(passwordField.getPassword());
            String confirmPass = new String(confirmPassword.getPassword());

            if (username.isEmpty() || fullName.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!password.equals(confirmPass)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            createAccount(username, fullName, password);
        });

        backButton.addActionListener(e -> {
            dispose();
            new login();
        });

        setVisible(true);
    }

    public void createAccount(String username, String fullName, String password) {
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ChatApp", "root", "Chandu@96")) {
            String query = "INSERT INTO user (username, fullName, password) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = con.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, fullName);
                stmt.setString(3, password);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Account created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                new login();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
