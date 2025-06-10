package com.chat.app;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;

public class CreateGroup extends JFrame {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Chandu@96";

    private String currentUser;
    private JTextField groupNameField, searchUserField;
    private DefaultListModel<String> interactedUserModel, selectedUserModel;
    private JList<String> interactedUserList, selectedUserList;
    private HashSet<String> selectedUsersSet = new HashSet<>(); // To prevent duplicates

    public CreateGroup(String currentUser) {
        this.currentUser = currentUser;
        setTitle("Create Group");
        setSize(600, 500);
        setLayout(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel groupNameLabel = new JLabel("Group Name:");
        groupNameLabel.setBounds(30, 20, 100, 25);
        add(groupNameLabel);

        groupNameField = new JTextField();
        groupNameField.setBounds(130, 20, 200, 25);
        add(groupNameField);

        JLabel interactedLabel = new JLabel("Previously Chatted Users:");
        interactedLabel.setBounds(30, 60, 200, 25);
        add(interactedLabel);

        interactedUserModel = new DefaultListModel<>();
        interactedUserList = new JList<>(interactedUserModel);
        JScrollPane interactedScroll = new JScrollPane(interactedUserList);
        interactedScroll.setBounds(30, 90, 200, 200);
        add(interactedScroll);

        JButton addFromInteractedBtn = new JButton("Add Selected");
        addFromInteractedBtn.setBounds(240, 150, 120, 30);
        add(addFromInteractedBtn);
        

        JButton removeSelectedBtn = new JButton("Remove");
        removeSelectedBtn.setBounds(240, 190, 120, 30);
        add(removeSelectedBtn);

        JLabel selectedLabel = new JLabel("Selected Members:");
        selectedLabel.setBounds(370, 60, 150, 25);
        add(selectedLabel);

        selectedUserModel = new DefaultListModel<>();
        selectedUserList = new JList<>(selectedUserModel);
        JScrollPane selectedScroll = new JScrollPane(selectedUserList);
        selectedScroll.setBounds(370, 90, 200, 200);
        add(selectedScroll);

        addFromInteractedBtn.addActionListener(e -> {
            for (String user : interactedUserList.getSelectedValuesList()) {
                if (!selectedUsersSet.contains(user)) {
                    selectedUserModel.addElement(user);
                    selectedUsersSet.add(user);
                }
            }
        });
        
        removeSelectedBtn.addActionListener(e -> {
            for (String user : selectedUserList.getSelectedValuesList()) {
                selectedUserModel.removeElement(user);
                selectedUsersSet.remove(user);
            }
        });

        JLabel searchLabel = new JLabel("Add new user:");
        searchLabel.setBounds(30, 310, 100, 25);
        add(searchLabel);

        searchUserField = new JTextField();
        searchUserField.setBounds(130, 310, 150, 25);
        add(searchUserField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(290, 310, 90, 25);
        add(searchBtn);

        JButton addUserBtn = new JButton("Add");
        addUserBtn.setBounds(390, 310, 70, 25);
        add(addUserBtn);
        addUserBtn.setEnabled(false);

        final String[] searchedUser = {null}; // Hold valid user

        searchBtn.addActionListener(e -> {
            String searchUsername = searchUserField.getText().trim();
            if (searchUsername.equalsIgnoreCase(currentUser)) {
                JOptionPane.showMessageDialog(this, "You can't add yourself manually.");
                return;
            }

            if (isUserExists(searchUsername)) {
                searchedUser[0] = searchUsername;
                JOptionPane.showMessageDialog(this, "User found!");
                addUserBtn.setEnabled(true);
            } else {
                JOptionPane.showMessageDialog(this, "User not found!");
                addUserBtn.setEnabled(false);
            }
        });

        addUserBtn.addActionListener(e -> {
            if (searchedUser[0] != null && !selectedUsersSet.contains(searchedUser[0])) {
                selectedUserModel.addElement(searchedUser[0]);
                selectedUsersSet.add(searchedUser[0]);
                addUserBtn.setEnabled(false);
                searchUserField.setText("");
            }
        });

        JButton createBtn = new JButton("Create Group");
        createBtn.setBounds(200, 380, 150, 30);
        add(createBtn);

        createBtn.addActionListener(e -> createGroup());

        loadInteractedUsers();

        setVisible(true);
    }

    private void loadInteractedUsers() {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = """
                    SELECT DISTINCT 
                        CASE WHEN sender = ? THEN receiver ELSE sender END AS user
                    FROM messages
                    WHERE sender = ? OR receiver = ? AND message IS NOT NULL AND message != ''
                    """;
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, currentUser);
            pst.setString(2, currentUser);
            pst.setString(3, currentUser);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                String user = rs.getString("user");
                if (!user.equalsIgnoreCase(currentUser)) {
                    interactedUserModel.addElement(user);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load users.");
        }
    }

    private boolean isUserExists(String username) {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT 1 FROM user WHERE username = ?";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createGroup() {
        String groupName = groupNameField.getText().trim();
        if (groupName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Group name cannot be empty.");
            return;
        }

        if (selectedUsersSet.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one member.");
            return;
        }

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // 1. Insert group
            String groupSql = "INSERT INTO chat_groups (group_name, created_by) VALUES (?, ?)";
            PreparedStatement groupPst = con.prepareStatement(groupSql, Statement.RETURN_GENERATED_KEYS);
            groupPst.setString(1, groupName);
            groupPst.setString(2, currentUser);
            groupPst.executeUpdate();

            ResultSet rs = groupPst.getGeneratedKeys();
            int groupId = -1;
            if (rs.next()) {
                groupId = rs.getInt(1);
            }

            // 2. Insert group members
            String memberSql = "INSERT INTO chat_group_members (group_id, username) VALUES (?, ?)";
            PreparedStatement memberPst = con.prepareStatement(memberSql);

            // Add current user by default
            selectedUsersSet.add(currentUser);

            for (String user : selectedUsersSet) {
                memberPst.setInt(1, groupId);
                memberPst.setString(2, user);
                memberPst.addBatch();
            }
            memberPst.executeBatch();

            JOptionPane.showMessageDialog(this, "Group created successfully!");
            dispose();

        } catch (SQLIntegrityConstraintViolationException ex) {
            JOptionPane.showMessageDialog(this, "Group name already exists!");
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error creating group.");
        }
    }
}










//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.sql.*;
//import java.util.ArrayList;
//
//public class CreateGroup extends JFrame {
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    private String currentUser;
//    private JTextField groupNameField;
//    private JList<String> userList;
//
//    public CreateGroup(String currentUser) {
//        this.currentUser = currentUser;
//        setTitle("Create Group");
//        setSize(400, 400);
//        setLayout(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//
//        JLabel groupNameLabel = new JLabel("Group Name:");
//        groupNameLabel.setBounds(30, 30, 100, 25);
//        add(groupNameLabel);
//
//        groupNameField = new JTextField();
//        groupNameField.setBounds(130, 30, 200, 25);
//        add(groupNameField);
//
//        JLabel usersLabel = new JLabel("Add Members:");
//        usersLabel.setBounds(30, 70, 100, 25);
//        add(usersLabel);
//
//        DefaultListModel<String> listModel = new DefaultListModel<>();
//        userList = new JList<>(listModel);
//        JScrollPane scrollPane = new JScrollPane(userList);
//        scrollPane.setBounds(130, 70, 200, 200);
//        add(scrollPane);
//
//        loadUsers(listModel);
//
//        JButton createBtn = new JButton("Create Group");
//        createBtn.setBounds(130, 290, 150, 30);
//        add(createBtn);
//
//        createBtn.addActionListener(e -> createGroup());
//
//        setVisible(true);
//    }
//
//    private void loadUsers(DefaultListModel<String> listModel) {
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT username FROM user WHERE username != ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, currentUser);
//            ResultSet rs = pst.executeQuery();
//            while (rs.next()) {
//                listModel.addElement(rs.getString("username"));
//            }
//        } catch (SQLException ex) {
//            ex.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading users.", "Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private void createGroup() {
//        String groupName = groupNameField.getText().trim();
//        if (groupName.isEmpty()) {
//            JOptionPane.showMessageDialog(this, "Group name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
//            return;
//        }
//
//        ArrayList<String> selectedUsers = new ArrayList<>(userList.getSelectedValuesList());
//        selectedUsers.add(currentUser); // Add current user as creator/member
//
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            // Insert group
//            String groupSql = "INSERT INTO chat_groups (group_name, created_by) VALUES (?, ?)";
//            PreparedStatement groupPst = con.prepareStatement(groupSql, Statement.RETURN_GENERATED_KEYS);
//            groupPst.setString(1, groupName);
//            groupPst.setString(2, currentUser);
//            groupPst.executeUpdate();
//
//            ResultSet rs = groupPst.getGeneratedKeys();
//            int groupId = -1;
//            if (rs.next()) {
//                groupId = rs.getInt(1);
//            }
//
//            // Insert members
//            String memberSql = "INSERT INTO chat_group_members (group_id, username) VALUES (?, ?)";
//            PreparedStatement memberPst = con.prepareStatement(memberSql);
//            for (String user : selectedUsers) {
//                memberPst.setInt(1, groupId);
//                memberPst.setString(2, user);
//                memberPst.addBatch();
//            }
//            memberPst.executeBatch();
//
//            JOptionPane.showMessageDialog(this, "Group created successfully!");
//            dispose();
//
//        } catch (SQLIntegrityConstraintViolationException ex) {
//            JOptionPane.showMessageDialog(this, "Group name already exists!", "Error", JOptionPane.ERROR_MESSAGE);
//        } catch (SQLException ex) {
//            ex.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Failed to create group.", "Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//}
//
