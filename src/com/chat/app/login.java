package com.chat.app;

import javax.swing.*;
import java.awt.event.*;
import java.sql.*;

public class login extends JFrame {
    private JTextField usernamField;
    private JPasswordField passwordField;
    private JButton loginButton, createAccountButton;

    public login() {
        setTitle("Login");
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        JLabel userlabel = new JLabel("Username :");
        userlabel.setBounds(10, 20, 80, 25);
        add(userlabel);

        usernamField = new JTextField();
        usernamField.setBounds(100, 20, 165, 25);
        add(usernamField);

        JLabel passLabel = new JLabel("Password :");
        passLabel.setBounds(10, 50, 80, 25);
        add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setBounds(100, 50, 165, 25);
        add(passwordField);

        loginButton = new JButton("Login");
        loginButton.setBounds(10, 80, 80, 25);
        add(loginButton);

        createAccountButton = new JButton("Register User?");
        createAccountButton.setBounds(100, 80, 140, 25);
        add(createAccountButton);

        loginButton.addActionListener(e -> {
            String username = usernamField.getText();
            String password = new String(passwordField.getPassword());
            if (validateLogin(username, password)) {
                updateUserStatus(username, "online");  // ✅ Set status to online
                JOptionPane.showMessageDialog(this, "Login successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                new MainMenu(username);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        createAccountButton.addActionListener(e -> {
            new Registration();
            dispose();
        });

        setVisible(true);
    }

    public boolean validateLogin(String username, String password) {
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp", "root", "Chandu@96")) {
            String query = "SELECT * FROM user WHERE username = ? AND password = ?";
            try (PreparedStatement stmt = con.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void updateUserStatus(String username, String status) {
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp", "root", "Chandu@96")) {
            String query = "UPDATE user SET status = ? WHERE username = ?";
            try (PreparedStatement pst = con.prepareStatement(query)) {
                pst.setString(1, status);
                pst.setString(2, username);
                pst.executeUpdate();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error updating status: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        new login();
    }
}






//package com.chat.app;
//
//import java.sql.*;
//import javax.swing.*;
//import java.awt.event.*;
//
//public class login extends JFrame {
//    private JTextField usernamField;
//    private JPasswordField passwordField;
//    private JButton loginButton, createAccountButton;
//
//    public login() {
//        setTitle("Login");
//        setSize(300, 150);
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setLayout(null);
//
//        JLabel userlabel = new JLabel("Username :");
//        userlabel.setBounds(10, 20, 80, 25);
//        add(userlabel);
//
//        usernamField = new JTextField();
//        usernamField.setBounds(100, 20, 165, 25);
//        add(usernamField);
//
//        JLabel passLabel = new JLabel("Password :");
//        passLabel.setBounds(10, 50, 80, 25);
//        add(passLabel);
//
//        passwordField = new JPasswordField();
//        passwordField.setBounds(100, 50, 165, 25);
//        add(passwordField);
//
//        loginButton = new JButton("Login");
//        loginButton.setBounds(10, 80, 80, 25);
//        add(loginButton);
//
//        createAccountButton = new JButton("Register User?");
//        createAccountButton.setBounds(100, 80, 140, 25);
//        add(createAccountButton);
//
//        loginButton.addActionListener(e -> {
//            String username = usernamField.getText();
//            String password = new String(passwordField.getPassword());
//            if (validateLogin(username, password)) {
//                JOptionPane.showMessageDialog(this, "Login successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
//                new MainMenu(username);
//                dispose();
//            } else {
//                JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
//            }
//        });
//
//        createAccountButton.addActionListener(e -> {
//            new Registration();
//            dispose();
//        });
//
//        setVisible(true);
//    }
//
//    public boolean validateLogin(String username, String password) {
//        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ChatApp", "root", "Chandu@96")) {
//            String query = "SELECT * FROM user WHERE username = ? AND password = ?";
//            try (PreparedStatement stmt = con.prepareStatement(query)) {
//                stmt.setString(1, username);
//                stmt.setString(2, password);
//                ResultSet rs = stmt.executeQuery();
//                return rs.next();
//            }
//        } catch (SQLException e) {
//            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
//            return false;
//        }
//    }
//
//    public static void main(String[] args) {
//        new login();
//    }
//}
