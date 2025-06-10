package com.chat.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import javax.swing.Timer;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

public class FriendsList extends JFrame {
    private String username;
    private DefaultListModel<FriendData> listModel;
    private JList<FriendData> friendsJList;
    private JTextField searchField;
    private Map<String, FriendData> friendDataMap;
    private Timer refreshTimer;
    private JPopupMenu popupMenu;


    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Chandu@96";

    public FriendsList(String username) {
        this.username = username;
        setTitle("My Chats - " + username);
        setSize(400, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        add(headerLabel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        friendsJList = new JList<>(listModel);
        friendsJList.setCellRenderer(new FriendListCellRenderer());

        searchField = new JTextField();
        searchField.setToolTipText("Search by name or username");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterFriendList();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterFriendList();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterFriendList();
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(searchField, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(friendsJList), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JButton chatButton = new JButton("Start Chat");
        JButton refreshButton = new JButton("Refresh");
        JButton newChatButton = new JButton("New Chat");

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(chatButton);
        bottomPanel.add(refreshButton);
        bottomPanel.add(newChatButton);
        add(bottomPanel, BorderLayout.SOUTH);

        loadFriendsFromDatabase();

        friendsJList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int index = friendsJList.locationToIndex(evt.getPoint());
                    if (index >= 0) {
                        FriendData selected = listModel.getElementAt(index);
                        openChatWindow(selected.username);
                    }
                }
            }
        });

        chatButton.addActionListener(e -> {
            FriendData selected = friendsJList.getSelectedValue();
            if (selected != null) {
                openChatWindow(selected.username);
            } else {
                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
            }
        });

        refreshButton.addActionListener(e -> loadFriendsFromDatabase());

        newChatButton.addActionListener(e -> {
            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
            if (friendUsername != null) {
                friendUsername = friendUsername.trim();
                if (!friendUsername.isEmpty()) {
                    if (friendUsername.equalsIgnoreCase(username)) {
                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
                    } else if (!friendExists(friendUsername)) {
                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
                    } else {
                        openChatWindow(friendUsername);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
                }
            }
        });
        
     // Start 5-second refresh timer
        refreshTimer = new Timer(5000, e -> loadFriendsFromDatabase());
        refreshTimer.start();

        // Add right-click popup menu
        popupMenu = new JPopupMenu();
        JMenuItem clearChatItem = new JMenuItem("Clear Chat History");
        popupMenu.add(clearChatItem);

        friendsJList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                int index = friendsJList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    friendsJList.setSelectedIndex(index);
                    popupMenu.show(friendsJList, e.getX(), e.getY());
                }
            }
        });

        clearChatItem.addActionListener(e -> {
            FriendData selected = friendsJList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(
                    FriendsList.this,
                    "Are you sure you want to clear chat history with " + selected.fullname + "?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    clearChatHistory(selected.username);
                    loadFriendsFromDatabase();  // refresh list
                }
            }
        });


        setVisible(true);
    }
    
    private void clearChatHistory(String friendUsername) {
        int choice = JOptionPane.showConfirmDialog(this, "Clear chat history with " + friendUsername + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                // 1. Delete messages
                String deleteSql = "DELETE FROM messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)";
                PreparedStatement deletePst = con.prepareStatement(deleteSql);
                deletePst.setString(1, username);
                deletePst.setString(2, friendUsername);
                deletePst.setString(3, friendUsername);
                deletePst.setString(4, username);
                deletePst.executeUpdate();

                // 2. Insert dummy message to preserve relation (will be sorted at bottom due to null time)
                String insertSql = "INSERT INTO messages (sender, receiver, message, seen_status, timestamp) VALUES (?, ?, '', ?, NULL)";
                PreparedStatement insertPst = con.prepareStatement(insertSql);
                insertPst.setString(1, username);
                insertPst.setString(2, friendUsername);
                insertPst.setBoolean(3, true); // ✅ CORRECT: uses actual boolean value
                insertPst.executeUpdate();

                loadFriendsFromDatabase();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    
    private boolean friendExists(String friendUsername) {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, friendUsername);
            ResultSet rs = pst.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error while checking user.");
        }
        return false;
    }

    private void openChatWindow(String friendUsername) {
        if (friendUsername.equalsIgnoreCase(username)) {
            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
            return;
        }
        new ChatClient(username, friendUsername);
    }

    private void loadFriendsFromDatabase() {
        friendDataMap = new LinkedHashMap<>();

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String query = """
                SELECT 
                    CASE 
                        WHEN sender = ? THEN receiver 
                        ELSE sender 
                    END AS friendUsername,
                    MAX(timestamp) AS last_chat_time
                FROM messages
                WHERE sender = ? OR receiver = ?
                GROUP BY friendUsername
                ORDER BY last_chat_time DESC
            """;

            PreparedStatement pst = con.prepareStatement(query);
            pst.setString(1, username);
            pst.setString(2, username);
            pst.setString(3, username);

            ResultSet rs = pst.executeQuery();

            List<FriendData> tempList = new ArrayList<>();

            while (rs.next()) {
                String friendUsername = rs.getString("friendUsername");
                FriendData fdata = getFriendData(con, friendUsername);
                if (fdata != null) {
                    tempList.add(fdata);
                }
            }

            // Sort manually: empty timestamp should go last
            tempList.sort((a, b) -> {
                if (a.time.isEmpty()) return 1;
                if (b.time.isEmpty()) return -1;
                return b.rawTimestamp.compareTo(a.rawTimestamp);
            });

            for (FriendData f : tempList) {
                friendDataMap.put(f.username, f);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
        }

        refreshFriendList();
    }


    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
        String sql = "SELECT fullname, status FROM user WHERE username = ?";
        PreparedStatement pst = con.prepareStatement(sql);
        pst.setString(1, friendUsername);
        ResultSet rs = pst.executeQuery();
        if (rs.next()) {
            String fullname = rs.getString("fullname");
            String status = rs.getString("status");
            boolean isOnline = "online".equalsIgnoreCase(status);

            String msgSql = """
                SELECT message, timestamp, seen_status
                FROM messages
                WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
                ORDER BY timestamp DESC
                LIMIT 1
            """;
            PreparedStatement msgPst = con.prepareStatement(msgSql);
            msgPst.setString(1, username);
            msgPst.setString(2, friendUsername);
            msgPst.setString(3, friendUsername);
            msgPst.setString(4, username);
            ResultSet msgRs = msgPst.executeQuery();

            String lastMessage = "";
            String time = "";
            Timestamp ts = null;
            int unreadCount = 0;

            if (msgRs.next()) {
                lastMessage = msgRs.getString("message");
                ts = msgRs.getTimestamp("timestamp");
                if (ts != null) {
                    time = new SimpleDateFormat("dd MMM HH:mm").format(ts);
                } else {
                    time = "";
                }
            }

            String unreadSql = "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND seen_status = 'unseen'";
            PreparedStatement unreadPst = con.prepareStatement(unreadSql);
            unreadPst.setString(1, friendUsername);
            unreadPst.setString(2, username);
            ResultSet unreadRs = unreadPst.executeQuery();
            if (unreadRs.next()) {
                unreadCount = unreadRs.getInt(1);
            }

            return new FriendData(friendUsername, fullname, isOnline, lastMessage, time, unreadCount, ts);
        }
        return null;
    }


    private void refreshFriendList() {
        listModel.clear();
        for (FriendData fdata : friendDataMap.values()) {
            listModel.addElement(fdata);
        }
    }

    private void filterFriendList() {
        String query = searchField.getText().toLowerCase();
        listModel.clear();
        for (FriendData fdata : friendDataMap.values()) {
            if (fdata.fullname.toLowerCase().contains(query) || fdata.username.toLowerCase().contains(query)) {
                listModel.addElement(fdata);
            }
        }
    }

    private static class FriendData {
        String username, fullname, lastMessage, time;
        boolean isOnline;
        int unreadCount;
        Timestamp rawTimestamp;

        FriendData(String username, String fullname, boolean isOnline, String lastMessage, String time, int unreadCount, Timestamp rawTimestamp) {
            this.username = username;
            this.fullname = fullname;
            this.isOnline = isOnline;
            this.lastMessage = lastMessage;
            this.time = time;
            this.unreadCount = unreadCount;
            this.rawTimestamp = rawTimestamp;
        }
    }


    private static class FriendListCellRenderer extends DefaultListCellRenderer {
        private final Color onlineColor = new Color(0, 150, 0);
        private final Color offlineColor = Color.RED;

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            FriendData f = (FriendData) value;

            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            panel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            JLabel iconLabel = new JLabel(createStatusIcon(f.isOnline ? onlineColor : offlineColor));
            JLabel nameLabel = new JLabel("<html><b>" + f.fullname + "</b> (" + f.username + ")</html>");
            JLabel msgLabel = new JLabel(truncate(f.lastMessage, 30));
            JLabel timeLabel = new JLabel(f.time);
            timeLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            timeLabel.setForeground(Color.GRAY);

            JPanel textPanel = new JPanel(new BorderLayout());
            textPanel.setOpaque(false);
            textPanel.add(nameLabel, BorderLayout.NORTH);
            textPanel.add(msgLabel, BorderLayout.CENTER);

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.setOpaque(false);
            rightPanel.add(timeLabel, BorderLayout.NORTH);

            if (f.unreadCount > 0) {
                JLabel unreadLabel = new JLabel(String.valueOf(f.unreadCount));
                unreadLabel.setOpaque(true);
                unreadLabel.setBackground(Color.BLUE);
                unreadLabel.setForeground(Color.WHITE);
                unreadLabel.setFont(new Font("Arial", Font.BOLD, 10));
                unreadLabel.setHorizontalAlignment(SwingConstants.CENTER);
                unreadLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                unreadLabel.setPreferredSize(new Dimension(30, 20));
                rightPanel.add(unreadLabel, BorderLayout.CENTER);
            }

            panel.add(iconLabel, BorderLayout.WEST);
            panel.add(textPanel, BorderLayout.CENTER);
            panel.add(rightPanel, BorderLayout.EAST);

            return panel;
        }

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

        private String truncate(String text, int max) {
            return text.length() <= max ? text : text.substring(0, max) + "...";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FriendsList("chandu"));
    }
}










//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.sql.*;
//import java.text.SimpleDateFormat;
//import java.util.*;
//import java.util.List;
//
//public class FriendsList extends JFrame {
//    private String username;
//    private DefaultListModel<String> listModel;
//    private JList<String> friendsJList;
//    private JTextField searchField;
//    private Map<String, FriendData> friendDataMap; // username -> FriendData
//
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public FriendsList(String username) {
//        this.username = username;
//        setTitle("My Chats - " + username);
//        setSize(400, 600);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
//        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
//        add(headerLabel, BorderLayout.NORTH);
//
//        listModel = new DefaultListModel<>();
//        friendsJList = new JList<>(listModel);
//        friendsJList.setCellRenderer(new FriendListCellRenderer());
//
//        // Search bar
//        searchField = new JTextField();
//        searchField.setToolTipText("Search by name or username");
//        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
//            public void insertUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void removeUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void changedUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//        });
//
//        JPanel centerPanel = new JPanel(new BorderLayout());
//        centerPanel.add(searchField, BorderLayout.NORTH);
//        centerPanel.add(new JScrollPane(friendsJList), BorderLayout.CENTER);
//        add(centerPanel, BorderLayout.CENTER);
//
//        // Buttons
//        JButton chatButton = new JButton("Start Chat");
//        JButton refreshButton = new JButton("Refresh");
//        JButton newChatButton = new JButton("New Chat");
//
//        JPanel bottomPanel = new JPanel();
//        bottomPanel.add(chatButton);
//        bottomPanel.add(refreshButton);
//        bottomPanel.add(newChatButton);
//        add(bottomPanel, BorderLayout.SOUTH);
//
//        loadFriendsFromDatabase();
//
//        // Double-click to chat
//        friendsJList.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int index = friendsJList.locationToIndex(evt.getPoint());
//                    if (index >= 0) {
//                        String display = listModel.getElementAt(index);
//                        String friendUsername = extractUsernameFromDisplay(display);
//                        openChatWindow(friendUsername);
//                    }
//                }
//            }
//        });
//
//        // Button listeners
//        chatButton.addActionListener(e -> {
//            String display = friendsJList.getSelectedValue();
//            if (display != null) {
//                String friendUsername = extractUsernameFromDisplay(display);
//                openChatWindow(friendUsername);
//            } else {
//                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
//            }
//        });
//
//        refreshButton.addActionListener(e -> loadFriendsFromDatabase());
//
//        newChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//                    } else if (!friendExists(friendUsername)) {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
//                    } else {
//                        openChatWindow(friendUsername);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
//                }
//            }
//        });
//
//        setVisible(true);
//    }
//
//    private boolean friendExists(String friendUsername) {
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, friendUsername);
//            ResultSet rs = pst.executeQuery();
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error while checking user.");
//        }
//        return false;
//    }
//
//    private void openChatWindow(String friendUsername) {
//        if (friendUsername.equalsIgnoreCase(username)) {
//            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//            return;
//        }
//        new ChatClient(username, friendUsername);
//    }
//
//    private void loadFriendsFromDatabase() {
//        friendDataMap = new LinkedHashMap<>();
//
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String query = """
//                SELECT 
//                    CASE 
//                        WHEN sender = ? THEN receiver 
//                        ELSE sender 
//                    END AS friendUsername,
//                    MAX(timestamp) AS last_chat_time
//                FROM messages
//                WHERE sender = ? OR receiver = ?
//                GROUP BY friendUsername
//                ORDER BY last_chat_time DESC
//            """;
//
//            PreparedStatement pst = con.prepareStatement(query);
//            pst.setString(1, username);
//            pst.setString(2, username);
//            pst.setString(3, username);
//
//            ResultSet rs = pst.executeQuery();
//
//            while (rs.next()) {
//                String friendUsername = rs.getString("friendUsername");
//                FriendData fdata = getFriendData(con, friendUsername);
//                if (fdata != null) {
//                    friendDataMap.put(friendUsername, fdata);
//                }
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
//        }
//
//        refreshFriendList();
//    }
//
//    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
//        String sql = "SELECT fullname, status FROM user WHERE username = ?";
//        PreparedStatement pst = con.prepareStatement(sql);
//        pst.setString(1, friendUsername);
//        ResultSet rs = pst.executeQuery();
//        if (rs.next()) {
//            String fullname = rs.getString("fullname");
//            String status = rs.getString("status");
//            boolean isOnline = "online".equalsIgnoreCase(status);
//
//            // Fetch last message and timestamp
//            String msgSql = """
//                SELECT message, timestamp, seen_status
//                FROM messages
//                WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
//                ORDER BY timestamp DESC
//                LIMIT 1
//            """;
//            PreparedStatement msgPst = con.prepareStatement(msgSql);
//            msgPst.setString(1, username);
//            msgPst.setString(2, friendUsername);
//            msgPst.setString(3, friendUsername);
//            msgPst.setString(4, username);
//            ResultSet msgRs = msgPst.executeQuery();
//
//            String lastMessage = "";
//            String time = "";
//            int unreadCount = 0;
//
//            if (msgRs.next()) {
//                lastMessage = msgRs.getString("message");
//                Timestamp ts = msgRs.getTimestamp("timestamp");
//                time = new SimpleDateFormat("dd MMM HH:mm").format(ts);
//            }
//
//            // Count unread messages
//            String unreadSql = "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND seen_status = 'unseen'";
//            PreparedStatement unreadPst = con.prepareStatement(unreadSql);
//            unreadPst.setString(1, friendUsername);
//            unreadPst.setString(2, username);
//            ResultSet unreadRs = unreadPst.executeQuery();
//            if (unreadRs.next()) {
//                unreadCount = unreadRs.getInt(1);
//            }
//
//            return new FriendData(friendUsername, fullname, isOnline, lastMessage, time, unreadCount);
//        }
//        return null;
//    }
//
//    private void refreshFriendList() {
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            StringBuilder sb = new StringBuilder("<html><b>")
//                    .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                    .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//            if (!fdata.time.isEmpty()) {
//                sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//            }
//
//            if (fdata.unreadCount > 0) {
//                sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//            }
//
//            sb.append("</html>");
//            listModel.addElement(sb.toString());
//        }
//    }
//
//    private void filterFriendList() {
//        String query = searchField.getText().toLowerCase();
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            if (fdata.fullname.toLowerCase().contains(query) || fdata.username.toLowerCase().contains(query)) {
//                StringBuilder sb = new StringBuilder("<html><b>")
//                        .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                        .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//                if (!fdata.time.isEmpty()) {
//                    sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//                }
//
//                if (fdata.unreadCount > 0) {
//                    sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//                }
//
//                sb.append("</html>");
//                listModel.addElement(sb.toString());
//            }
//        }
//    }
//
//    private String truncate(String text, int max) {
//        return text.length() <= max ? text : text.substring(0, max) + "...";
//    }
//
//    private String extractUsernameFromDisplay(String display) {
//        int start = display.indexOf('(');
//        int end = display.indexOf(')');
//        if (start >= 0 && end > start) {
//            return display.substring(start + 1, end);
//        }
//        return display;
//    }
//
//    private class FriendListCellRenderer extends DefaultListCellRenderer {
//        private final Color onlineColor = new Color(0, 150, 0);
//        private final Color offlineColor = Color.RED;
//
//        @Override
//        public Component getListCellRendererComponent(
//                JList<?> list, Object value, int index,
//                boolean isSelected, boolean cellHasFocus) {
//
//            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            String display = (String) value;
//            String friendUsername = extractUsernameFromDisplay(display);
//            FriendData fdata = friendDataMap.get(friendUsername);
//            boolean isOnline = (fdata != null && fdata.isOnline);
//
//            label.setIcon(createStatusIcon(isOnline ? onlineColor : offlineColor));
//            label.setIconTextGap(10);
//            return label;
//        }
//    }
//
//    private Icon createStatusIcon(Color color) {
//        int diameter = 12;
//        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
//        Graphics2D g2 = img.createGraphics();
//        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2.setColor(color);
//        g2.fillOval(0, 0, diameter, diameter);
//        g2.dispose();
//        return new ImageIcon(img);
//    }
//
//    private static class FriendData {
//        String username, fullname, lastMessage, time;
//        boolean isOnline;
//        int unreadCount;
//
//        FriendData(String username, String fullname, boolean isOnline, String lastMessage, String time, int unreadCount) {
//            this.username = username;
//            this.fullname = fullname;
//            this.isOnline = isOnline;
//            this.lastMessage = lastMessage;
//            this.time = time;
//            this.unreadCount = unreadCount;
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new FriendsList("chandu")); // Example
//    }
//}











//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.sql.*;
//import java.text.SimpleDateFormat;
//import java.util.*;
//
//public class FriendsList extends JFrame {
//    private String username;
//    private DefaultListModel<String> listModel;
//    private JList<String> friendsJList;
//    private JTextField searchField;
//    private Map<String, FriendData> friendDataMap;
//
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public FriendsList(String username) {
//        this.username = username;
//        setTitle("My Chats - " + username);
//        setSize(400, 600);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
//        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
//        add(headerLabel, BorderLayout.NORTH);
//
//        listModel = new DefaultListModel<>();
//        friendsJList = new JList<>(listModel);
//        friendsJList.setCellRenderer(new FriendListCellRenderer());
//
//        // Search bar
//        searchField = new JTextField();
//        searchField.setToolTipText("Search by name or username");
//        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
//            public void insertUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void removeUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void changedUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//        });
//
//        JPanel centerPanel = new JPanel(new BorderLayout());
//        centerPanel.add(searchField, BorderLayout.NORTH);
//        centerPanel.add(new JScrollPane(friendsJList), BorderLayout.CENTER);
//        add(centerPanel, BorderLayout.CENTER);
//
//        // Buttons
//        JButton chatButton = new JButton("Start Chat");
//        JButton refreshButton = new JButton("Refresh");
//        JButton newChatButton = new JButton("New Chat");
//
//        JPanel bottomPanel = new JPanel();
//        bottomPanel.add(chatButton);
//        bottomPanel.add(refreshButton);
//        bottomPanel.add(newChatButton);
//        add(bottomPanel, BorderLayout.SOUTH);
//
//        loadFriendsFromDatabase();
//
//        // Double-click to chat
//        friendsJList.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int index = friendsJList.locationToIndex(evt.getPoint());
//                    if (index >= 0) {
//                        String display = listModel.getElementAt(index);
//                        String friendUsername = extractUsernameFromDisplay(display);
//                        openChatWindow(friendUsername);
//                    }
//                }
//            }
//        });
//
//        // Button listeners
//        chatButton.addActionListener(e -> {
//            String display = friendsJList.getSelectedValue();
//            if (display != null) {
//                String friendUsername = extractUsernameFromDisplay(display);
//                openChatWindow(friendUsername);
//            } else {
//                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
//            }
//        });
//
//        refreshButton.addActionListener(e -> loadFriendsFromDatabase());
//
//        newChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//                    } else if (!friendExists(friendUsername)) {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
//                    } else {
//                        openChatWindow(friendUsername);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
//                }
//            }
//        });
//
//        setVisible(true);
//    }
//
//    private boolean friendExists(String friendUsername) {
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, friendUsername);
//            ResultSet rs = pst.executeQuery();
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error while checking user.");
//        }
//        return false;
//    }
//
//    private void openChatWindow(String friendUsername) {
//        if (friendUsername.equalsIgnoreCase(username)) {
//            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//            return;
//        }
//        new ChatClient(username, friendUsername);
//    }
//
//    private void loadFriendsFromDatabase() {
//        friendDataMap = new LinkedHashMap<>();
//
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String query = """
//                SELECT 
//                    CASE 
//                        WHEN sender = ? THEN receiver 
//                        ELSE sender 
//                    END AS friendUsername,
//                    MAX(timestamp) AS last_chat_time
//                FROM messages
//                WHERE sender = ? OR receiver = ?
//                GROUP BY friendUsername
//                ORDER BY last_chat_time DESC
//            """;
//
//            PreparedStatement pst = con.prepareStatement(query);
//            pst.setString(1, username);
//            pst.setString(2, username);
//            pst.setString(3, username);
//
//            ResultSet rs = pst.executeQuery();
//
//            while (rs.next()) {
//                String friendUsername = rs.getString("friendUsername");
//                FriendData fdata = getFriendData(con, friendUsername);
//                if (fdata != null) {
//                    friendDataMap.put(friendUsername, fdata);
//                }
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
//        }
//
//        refreshFriendList();
//    }
//
//    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
//        String sql = "SELECT fullname, status FROM user WHERE username = ?";
//        PreparedStatement pst = con.prepareStatement(sql);
//        pst.setString(1, friendUsername);
//        ResultSet rs = pst.executeQuery();
//        if (rs.next()) {
//            String fullname = rs.getString("fullname");
//            String status = rs.getString("status");
//            boolean isOnline = "online".equalsIgnoreCase(status);
//
//            String msgSql = """
//                SELECT message, timestamp, seen_status
//                FROM messages
//                WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
//                ORDER BY timestamp DESC
//                LIMIT 1
//            """;
//            PreparedStatement msgPst = con.prepareStatement(msgSql);
//            msgPst.setString(1, username);
//            msgPst.setString(2, friendUsername);
//            msgPst.setString(3, friendUsername);
//            msgPst.setString(4, username);
//            ResultSet msgRs = msgPst.executeQuery();
//
//            String lastMessage = "";
//            String time = "";
//
//            if (msgRs.next()) {
//                lastMessage = msgRs.getString("message");
//                Timestamp ts = msgRs.getTimestamp("timestamp");
//                time = new SimpleDateFormat("dd MMM HH:mm").format(ts);
//            }
//
//            // Unread count
//            String unreadSql = "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND seen_status = 'unseen'";
//            PreparedStatement unreadPst = con.prepareStatement(unreadSql);
//            unreadPst.setString(1, friendUsername);
//            unreadPst.setString(2, username);
//            ResultSet unreadRs = unreadPst.executeQuery();
//            int unreadCount = 0;
//            if (unreadRs.next()) {
//                unreadCount = unreadRs.getInt(1);
//            }
//
//            return new FriendData(friendUsername, fullname, isOnline, lastMessage, time, unreadCount);
//        }
//        return null;
//    }
//
//    private void refreshFriendList() {
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            StringBuilder sb = new StringBuilder("<html><b>")
//                    .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                    .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//            if (!fdata.time.isEmpty()) {
//                sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//            }
//
//            if (fdata.unreadCount > 0) {
//                sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//            }
//
//            sb.append("</html>");
//            listModel.addElement(sb.toString());
//        }
//    }
//
//    private void filterFriendList() {
//        String query = searchField.getText().toLowerCase();
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            if (fdata.fullname.toLowerCase().contains(query) || fdata.username.toLowerCase().contains(query)) {
//                StringBuilder sb = new StringBuilder("<html><b>")
//                        .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                        .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//                if (!fdata.time.isEmpty()) {
//                    sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//                }
//
//                if (fdata.unreadCount > 0) {
//                    sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//                }
//
//                sb.append("</html>");
//                listModel.addElement(sb.toString());
//            }
//        }
//    }
//
//    private String truncate(String text, int max) {
//        return text.length() <= max ? text : text.substring(0, max) + "...";
//    }
//
//    private String extractUsernameFromDisplay(String display) {
//        int start = display.indexOf('(');
//        int end = display.indexOf(')');
//        if (start >= 0 && end > start) {
//            return display.substring(start + 1, end);
//        }
//        return display;
//    }
//
//    private class FriendListCellRenderer extends DefaultListCellRenderer {
//        private final Color onlineColor = new Color(0, 150, 0);
//        private final Color offlineColor = Color.RED;
//
//        @Override
//        public Component getListCellRendererComponent(
//                JList<?> list, Object value, int index,
//                boolean isSelected, boolean cellHasFocus) {
//
//            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            String display = (String) value;
//            String friendUsername = extractUsernameFromDisplay(display);
//            FriendData fdata = friendDataMap.get(friendUsername);
//            boolean isOnline = (fdata != null && fdata.isOnline);
//
//            label.setIcon(createStatusIcon(isOnline ? onlineColor : offlineColor));
//            label.setIconTextGap(10);
//            return label;
//        }
//    }
//
//    private Icon createStatusIcon(Color color) {
//        int diameter = 12;
//        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
//        Graphics2D g2 = img.createGraphics();
//        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2.setColor(color);
//        g2.fillOval(0, 0, diameter, diameter);
//        g2.dispose();
//        return new ImageIcon(img);
//    }
//
//    private static class FriendData {
//        String username, fullname, lastMessage, time;
//        boolean isOnline;
//        int unreadCount;
//
//        FriendData(String username, String fullname, boolean isOnline, String lastMessage, String time, int unreadCount) {
//            this.username = username;
//            this.fullname = fullname;
//            this.isOnline = isOnline;
//            this.lastMessage = lastMessage;
//            this.time = time;
//            this.unreadCount = unreadCount;
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new FriendsList("chandu"));
//    }
//}
//









//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.sql.*;
//import java.text.SimpleDateFormat;
//import java.util.*;
//import java.util.List;
//
//public class FriendsList extends JFrame {
//    private String username;
//    private DefaultListModel<String> listModel;
//    private JList<String> friendsJList;
//    private JTextField searchField;
//    private Map<String, FriendData> friendDataMap; // username -> FriendData
//
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public FriendsList(String username) {
//        this.username = username;
//        setTitle("My Chats - " + username);
//        setSize(400, 600);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
//        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
//        add(headerLabel, BorderLayout.NORTH);
//
//        listModel = new DefaultListModel<>();
//        friendsJList = new JList<>(listModel);
//        friendsJList.setCellRenderer(new FriendListCellRenderer());
//
//        // Search bar
//        searchField = new JTextField();
//        searchField.setToolTipText("Search by name or username");
//        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
//            public void insertUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void removeUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//
//            public void changedUpdate(javax.swing.event.DocumentEvent e) {
//                filterFriendList();
//            }
//        });
//
//        JPanel centerPanel = new JPanel(new BorderLayout());
//        centerPanel.add(searchField, BorderLayout.NORTH);
//        centerPanel.add(new JScrollPane(friendsJList), BorderLayout.CENTER);
//        add(centerPanel, BorderLayout.CENTER);
//
//        // Buttons
//        JButton chatButton = new JButton("Start Chat");
//        JButton refreshButton = new JButton("Refresh");
//        JButton newChatButton = new JButton("New Chat");
//
//        JPanel bottomPanel = new JPanel();
//        bottomPanel.add(chatButton);
//        bottomPanel.add(refreshButton);
//        bottomPanel.add(newChatButton);
//        add(bottomPanel, BorderLayout.SOUTH);
//
//        loadFriendsFromDatabase();
//
//        // Double-click to chat
//        friendsJList.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int index = friendsJList.locationToIndex(evt.getPoint());
//                    if (index >= 0) {
//                        String display = listModel.getElementAt(index);
//                        String friendUsername = extractUsernameFromDisplay(display);
//                        openChatWindow(friendUsername);
//                    }
//                }
//            }
//        });
//
//        // Button listeners
//        chatButton.addActionListener(e -> {
//            String display = friendsJList.getSelectedValue();
//            if (display != null) {
//                String friendUsername = extractUsernameFromDisplay(display);
//                openChatWindow(friendUsername);
//            } else {
//                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
//            }
//        });
//
//        refreshButton.addActionListener(e -> loadFriendsFromDatabase());
//
//        newChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//                    } else if (!friendExists(friendUsername)) {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
//                    } else {
//                        openChatWindow(friendUsername);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
//                }
//            }
//        });
//
//        setVisible(true);
//    }
//
//    private boolean friendExists(String friendUsername) {
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, friendUsername);
//            ResultSet rs = pst.executeQuery();
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error while checking user.");
//        }
//        return false;
//    }
//
//    private void openChatWindow(String friendUsername) {
//        if (friendUsername.equalsIgnoreCase(username)) {
//            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//            return;
//        }
//        new ChatClient(username, friendUsername);
//    }
//
//    private void loadFriendsFromDatabase() {
//        friendDataMap = new LinkedHashMap<>();
//
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String query = """
//                SELECT 
//                    CASE 
//                        WHEN sender = ? THEN receiver 
//                        ELSE sender 
//                    END AS friendUsername,
//                    MAX(timestamp) AS last_chat_time
//                FROM messages
//                WHERE sender = ? OR receiver = ?
//                GROUP BY friendUsername
//                ORDER BY last_chat_time DESC
//            """;
//
//            PreparedStatement pst = con.prepareStatement(query);
//            pst.setString(1, username);
//            pst.setString(2, username);
//            pst.setString(3, username);
//
//            ResultSet rs = pst.executeQuery();
//
//            while (rs.next()) {
//                String friendUsername = rs.getString("friendUsername");
//                FriendData fdata = getFriendData(con, friendUsername);
//                if (fdata != null) {
//                    friendDataMap.put(friendUsername, fdata);
//                }
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
//        }
//
//        refreshFriendList();
//    }
//
//    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
//        String sql = "SELECT fullname, status FROM user WHERE username = ?";
//        PreparedStatement pst = con.prepareStatement(sql);
//        pst.setString(1, friendUsername);
//        ResultSet rs = pst.executeQuery();
//        if (rs.next()) {
//            String fullname = rs.getString("fullname");
//            String status = rs.getString("status");
//            boolean isOnline = "online".equalsIgnoreCase(status);
//
//            // Fetch last message and timestamp
//            String msgSql = """
//                SELECT message, timestamp, seen_status
//                FROM messages
//                WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
//                ORDER BY timestamp DESC
//                LIMIT 1
//            """;
//            PreparedStatement msgPst = con.prepareStatement(msgSql);
//            msgPst.setString(1, username);
//            msgPst.setString(2, friendUsername);
//            msgPst.setString(3, friendUsername);
//            msgPst.setString(4, username);
//            ResultSet msgRs = msgPst.executeQuery();
//
//            String lastMessage = "";
//            String time = "";
//            int unreadCount = 0;
//
//            if (msgRs.next()) {
//                lastMessage = msgRs.getString("message");
//                Timestamp ts = msgRs.getTimestamp("timestamp");
//                time = new SimpleDateFormat("dd MMM HH:mm").format(ts);
//            }
//
//            // Count unread messages
//            String unreadSql = "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND seen_status = 'unseen'";
//            PreparedStatement unreadPst = con.prepareStatement(unreadSql);
//            unreadPst.setString(1, friendUsername);
//            unreadPst.setString(2, username);
//            ResultSet unreadRs = unreadPst.executeQuery();
//            if (unreadRs.next()) {
//                unreadCount = unreadRs.getInt(1);
//            }
//
//            return new FriendData(friendUsername, fullname, isOnline, lastMessage, time, unreadCount);
//        }
//        return null;
//    }
//
//    private void refreshFriendList() {
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            StringBuilder sb = new StringBuilder("<html><b>")
//                    .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                    .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//            if (!fdata.time.isEmpty()) {
//                sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//            }
//
//            if (fdata.unreadCount > 0) {
//                sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//            }
//
//            sb.append("</html>");
//            listModel.addElement(sb.toString());
//        }
//    }
//
//    private void filterFriendList() {
//        String query = searchField.getText().toLowerCase();
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            if (fdata.fullname.toLowerCase().contains(query) || fdata.username.toLowerCase().contains(query)) {
//                StringBuilder sb = new StringBuilder("<html><b>")
//                        .append(fdata.fullname).append("</b> (").append(fdata.username).append(")<br>")
//                        .append("<font color='gray'>").append(truncate(fdata.lastMessage, 30)).append("</font>");
//
//                if (!fdata.time.isEmpty()) {
//                    sb.append(" - <font color='gray'>").append(fdata.time).append("</font>");
//                }
//
//                if (fdata.unreadCount > 0) {
//                    sb.append("<br><font color='blue'>").append(fdata.unreadCount).append(" unread</font>");
//                }
//
//                sb.append("</html>");
//                listModel.addElement(sb.toString());
//            }
//        }
//    }
//
//    private String truncate(String text, int max) {
//        return text.length() <= max ? text : text.substring(0, max) + "...";
//    }
//
//    private String extractUsernameFromDisplay(String display) {
//        int start = display.indexOf('(');
//        int end = display.indexOf(')');
//        if (start >= 0 && end > start) {
//            return display.substring(start + 1, end);
//        }
//        return display;
//    }
//
//    private class FriendListCellRenderer extends DefaultListCellRenderer {
//        private final Color onlineColor = new Color(0, 150, 0);
//        private final Color offlineColor = Color.RED;
//
//        @Override
//        public Component getListCellRendererComponent(
//                JList<?> list, Object value, int index,
//                boolean isSelected, boolean cellHasFocus) {
//
//            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            String display = (String) value;
//            String friendUsername = extractUsernameFromDisplay(display);
//            FriendData fdata = friendDataMap.get(friendUsername);
//            boolean isOnline = (fdata != null && fdata.isOnline);
//
//            label.setIcon(createStatusIcon(isOnline ? onlineColor : offlineColor));
//            label.setIconTextGap(10);
//            return label;
//        }
//    }
//
//    private Icon createStatusIcon(Color color) {
//        int diameter = 12;
//        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
//        Graphics2D g2 = img.createGraphics();
//        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2.setColor(color);
//        g2.fillOval(0, 0, diameter, diameter);
//        g2.dispose();
//        return new ImageIcon(img);
//    }
//
//    private static class FriendData {
//        String username, fullname, lastMessage, time;
//        boolean isOnline;
//        int unreadCount;
//
//        FriendData(String username, String fullname, boolean isOnline, String lastMessage, String time, int unreadCount) {
//            this.username = username;
//            this.fullname = fullname;
//            this.isOnline = isOnline;
//            this.lastMessage = lastMessage;
//            this.time = time;
//            this.unreadCount = unreadCount;
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new FriendsList("chandu")); // Example
//    }
//}











//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.sql.*;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//public class FriendsList extends JFrame {
//    private String username;
//    private DefaultListModel<String> listModel;
//    private JList<String> friendsJList;
//    private Map<String, FriendData> friendDataMap;
//    private JTextField searchField;
//
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public FriendsList(String username) {
//        this.username = username;
//        setTitle("Friends List - " + username);
//        setSize(400, 520);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        // Search bar panel
//        JPanel searchPanel = new JPanel(new BorderLayout());
//        searchField = new JTextField();
//        searchField.setToolTipText("Search friends...");
//        searchPanel.add(searchField, BorderLayout.CENTER);
//        add(searchPanel, BorderLayout.NORTH);
//
//        // Header below search
//        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
//        headerLabel.setFont(new Font("Arial", Font.BOLD, 16));
//        JPanel headerPanel = new JPanel(new BorderLayout());
//        headerPanel.add(headerLabel, BorderLayout.CENTER);
//        add(headerPanel, BorderLayout.AFTER_LINE_ENDS);
//
//        listModel = new DefaultListModel<>();
//        friendsJList = new JList<>(listModel);
//        friendsJList.setCellRenderer(new FriendListCellRenderer());
//        add(new JScrollPane(friendsJList), BorderLayout.CENTER);
//
//        JButton chatButton = new JButton("Start Chat");
//        JButton refreshButton = new JButton("Refresh");
//        JButton newChatButton = new JButton("New Chat");
//
//        JPanel bottomPanel = new JPanel();
//        bottomPanel.add(chatButton);
//        bottomPanel.add(refreshButton);
//        bottomPanel.add(newChatButton);
//        add(bottomPanel, BorderLayout.SOUTH);
//
//        searchField.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyReleased(KeyEvent e) {
//                refreshFriendList(); // filter on typing
//            }
//        });
//
//        loadFriendsFromDatabase();
//
//        friendsJList.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int index = friendsJList.locationToIndex(evt.getPoint());
//                    if (index >= 0) {
//                        String display = listModel.getElementAt(index);
//                        String friendUsername = extractUsernameFromDisplay(display);
//                        openChatWindow(friendUsername);
//                    }
//                }
//            }
//        });
//
//        chatButton.addActionListener(e -> {
//            String display = friendsJList.getSelectedValue();
//            if (display != null) {
//                String friendUsername = extractUsernameFromDisplay(display);
//                openChatWindow(friendUsername);
//            } else {
//                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
//            }
//        });
//
//        refreshButton.addActionListener(e -> loadFriendsFromDatabase());
//
//        newChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//                    } else if (!friendExists(friendUsername)) {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
//                    } else {
//                        openChatWindow(friendUsername);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
//                }
//            }
//        });
//
//        setVisible(true);
//    }
//
//    private boolean friendExists(String friendUsername) {
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, friendUsername);
//            ResultSet rs = pst.executeQuery();
//            return rs.next() && rs.getInt(1) > 0;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error while checking user.");
//            return false;
//        }
//    }
//
//    private void openChatWindow(String friendUsername) {
//        if (friendUsername.equalsIgnoreCase(username)) {
//            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//        } else {
//            new ChatClient(username, friendUsername);
//        }
//    }
//
//    private void loadFriendsFromDatabase() {
//        friendDataMap = new LinkedHashMap<>();
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String query = """
//                SELECT 
//                    CASE 
//                        WHEN sender = ? THEN receiver 
//                        ELSE sender 
//                    END AS friendUsername,
//                    MAX(timestamp) AS last_chat_time
//                FROM messages
//                WHERE sender = ? OR receiver = ?
//                GROUP BY friendUsername
//                ORDER BY last_chat_time DESC
//            """;
//
//            PreparedStatement pst = con.prepareStatement(query);
//            pst.setString(1, username);
//            pst.setString(2, username);
//            pst.setString(3, username);
//            ResultSet rs = pst.executeQuery();
//
//            while (rs.next()) {
//                String friendUsername = rs.getString("friendUsername");
//                FriendData fdata = getFriendData(con, friendUsername);
//                if (fdata != null) {
//                    friendDataMap.put(friendUsername, fdata);
//                }
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
//        }
//
//        refreshFriendList();
//    }
//
//    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
//        String sql = "SELECT fullname, status FROM user WHERE username = ?";
//        PreparedStatement pst = con.prepareStatement(sql);
//        pst.setString(1, friendUsername);
//        ResultSet rs = pst.executeQuery();
//        if (rs.next()) {
//            String fullname = rs.getString("fullname");
//            String status = rs.getString("status");
//            boolean isOnline = "online".equalsIgnoreCase(status);
//            return new FriendData(friendUsername, fullname, isOnline);
//        }
//        return null;
//    }
//
//    private void refreshFriendList() {
//        listModel.clear();
//        String search = searchField.getText().trim().toLowerCase();
//
//        for (FriendData fdata : friendDataMap.values()) {
//            String display = String.format("%s (%s)", fdata.fullname, fdata.username);
//            if (search.isEmpty() || display.toLowerCase().contains(search)) {
//                listModel.addElement(display);
//            }
//        }
//    }
//
//    private String extractUsernameFromDisplay(String display) {
//        int start = display.lastIndexOf('(');
//        int end = display.lastIndexOf(')');
//        if (start >= 0 && end > start) {
//            return display.substring(start + 1, end);
//        }
//        return display;
//    }
//
//    private class FriendListCellRenderer extends DefaultListCellRenderer {
//        private final Color onlineColor = new Color(0, 150, 0);
//        private final Color offlineColor = Color.RED;
//
//        @Override
//        public Component getListCellRendererComponent(
//                JList<?> list, Object value, int index,
//                boolean isSelected, boolean cellHasFocus) {
//
//            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            String display = (String) value;
//            String friendUsername = extractUsernameFromDisplay(display);
//            FriendData fdata = friendDataMap.get(friendUsername);
//            boolean isOnline = (fdata != null && fdata.isOnline);
//
//            label.setIcon(createStatusIcon(isOnline ? onlineColor : offlineColor));
//            label.setIconTextGap(10);
//            return label;
//        }
//    }
//
//    private Icon createStatusIcon(Color color) {
//        int diameter = 12;
//        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
//        Graphics2D g2 = img.createGraphics();
//        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2.setColor(color);
//        g2.fillOval(0, 0, diameter, diameter);
//        g2.dispose();
//        return new ImageIcon(img);
//    }
//
//    private static class FriendData {
//        String username;
//        String fullname;
//        boolean isOnline;
//
//        FriendData(String username, String fullname, boolean isOnline) {
//            this.username = username;
//            this.fullname = fullname;
//            this.isOnline = isOnline;
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new FriendsList("chandu"));
//    }
//}










//package com.chat.app;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.sql.*;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//public class FriendsList extends JFrame {
//    private String username;
//    private DefaultListModel<String> listModel;
//    private JList<String> friendsJList;
//    private Map<String, FriendData> friendDataMap; // username -> FriendData (fullname, status)
//    
//    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatapp";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "Chandu@96";
//
//    public FriendsList(String username) {
//        this.username = username;
//        setTitle("Friends List - " + username);
//        setSize(400, 520);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        JLabel headerLabel = new JLabel("My Chats", SwingConstants.CENTER);
//        headerLabel.setFont(new Font("Arial", Font.BOLD, 16));
//        add(headerLabel, BorderLayout.NORTH);
//
//        listModel = new DefaultListModel<>();
//        friendsJList = new JList<>(listModel);
//        friendsJList.setCellRenderer(new FriendListCellRenderer());
//        add(new JScrollPane(friendsJList), BorderLayout.CENTER);
//
//        JButton chatButton = new JButton("Start Chat");
//        JButton refreshButton = new JButton("Refresh");
//        JButton newChatButton = new JButton("New Chat");
//
//        JPanel bottomPanel = new JPanel();
//        bottomPanel.add(chatButton);
//        bottomPanel.add(refreshButton);
//        bottomPanel.add(newChatButton);
//        add(bottomPanel, BorderLayout.SOUTH);
//
//        loadFriendsFromDatabase();
//
//        // Double click to chat
//        friendsJList.addMouseListener(new MouseAdapter() {
//            public void mouseClicked(MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int index = friendsJList.locationToIndex(evt.getPoint());
//                    if (index >= 0) {
//                        String display = listModel.getElementAt(index);
//                        String friendUsername = extractUsernameFromDisplay(display);
//                        openChatWindow(friendUsername);
//                    }
//                }
//            }
//        });
//
//        // Start Chat button uses selected friend in list
//        chatButton.addActionListener(e -> {
//            String display = friendsJList.getSelectedValue();
//            if (display != null) {
//                String friendUsername = extractUsernameFromDisplay(display);
//                openChatWindow(friendUsername);
//            } else {
//                JOptionPane.showMessageDialog(this, "Select a friend from the list.");
//            }
//        });
//
//        // Refresh friends list
//        refreshButton.addActionListener(e -> loadFriendsFromDatabase());
//
//        // New Chat button prompts input username
//        newChatButton.addActionListener(e -> {
//            String friendUsername = JOptionPane.showInputDialog(this, "Enter username to start chat:");
//            if (friendUsername != null) {
//                friendUsername = friendUsername.trim();
//                if (!friendUsername.isEmpty()) {
//                    if (friendUsername.equalsIgnoreCase(username)) {
//                        JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//                    } else if (!friendExists(friendUsername)) {
//                        JOptionPane.showMessageDialog(this, "User '" + friendUsername + "' does not exist.");
//                    } else {
//                        openChatWindow(friendUsername);
//                    }
//                } else {
//                    JOptionPane.showMessageDialog(this, "Username cannot be empty.");
//                }
//            }
//        });
//
//        setVisible(true);
//    }
//
//    private boolean friendExists(String friendUsername) {
//        // Check in DB if user exists in users table
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            String sql = "SELECT COUNT(*) FROM user WHERE username = ?";
//            PreparedStatement pst = con.prepareStatement(sql);
//            pst.setString(1, friendUsername);
//            ResultSet rs = pst.executeQuery();
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Database error while checking user.");
//        }
//        return false;
//    }
//
//    private void openChatWindow(String friendUsername) {
//        if (friendUsername.equalsIgnoreCase(username)) {
//            JOptionPane.showMessageDialog(this, "You cannot chat with yourself.");
//            return;
//        }
//        new ChatClient(username, friendUsername);
//    }
//
//    private void loadFriendsFromDatabase() {
//        friendDataMap = new LinkedHashMap<>();
//
//        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
//            // Query distinct friends ordered by last message timestamp desc
//            String query = """
//                SELECT 
//                    CASE 
//                        WHEN sender = ? THEN receiver 
//                        ELSE sender 
//                    END AS friendUsername,
//                    MAX(timestamp) AS last_chat_time
//                FROM messages
//                WHERE sender = ? OR receiver = ?
//                GROUP BY friendUsername
//                ORDER BY last_chat_time DESC
//            """;
//
//            PreparedStatement pst = con.prepareStatement(query);
//            pst.setString(1, username);
//            pst.setString(2, username);
//            pst.setString(3, username);
//
//            ResultSet rs = pst.executeQuery();
//
//            while (rs.next()) {
//                String friendUsername = rs.getString("friendUsername");
//                FriendData fdata = getFriendData(con, friendUsername);
//                if (fdata != null) {
//                    friendDataMap.put(friendUsername, fdata);
//                }
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Error loading friends from database.");
//        }
//
//        refreshFriendList();
//    }
//
//    private FriendData getFriendData(Connection con, String friendUsername) throws SQLException {
//        String sql = "SELECT fullname, status FROM user WHERE username = ?";
//        PreparedStatement pst = con.prepareStatement(sql);
//        pst.setString(1, friendUsername);
//        ResultSet rs = pst.executeQuery();
//        if (rs.next()) {
//            String fullname = rs.getString("fullname");
//            String status = rs.getString("status");
//            boolean isOnline = "online".equalsIgnoreCase(status);
//            return new FriendData(friendUsername, fullname, isOnline);
//        }
//        return null;
//    }
//
//    private void refreshFriendList() {
//        listModel.clear();
//        for (FriendData fdata : friendDataMap.values()) {
//            String display = String.format("%s (%s)", fdata.fullname, fdata.username);
//            listModel.addElement(display);
//        }
//    }
//
//    // Extract username from display string "Fullname (username)"
//    private String extractUsernameFromDisplay(String display) {
//        int start = display.lastIndexOf('(');
//        int end = display.lastIndexOf(')');
//        if (start >= 0 && end > start) {
//            return display.substring(start + 1, end);
//        }
//        return display;
//    }
//
//    private class FriendListCellRenderer extends DefaultListCellRenderer {
//        private final Color onlineColor = new Color(0, 150, 0);
//        private final Color offlineColor = Color.RED;
//
//        @Override
//        public Component getListCellRendererComponent(
//                JList<?> list, Object value, int index,
//                boolean isSelected, boolean cellHasFocus) {
//
//            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            String display = (String) value;
//            String friendUsername = extractUsernameFromDisplay(display);
//            FriendData fdata = friendDataMap.get(friendUsername);
//            boolean isOnline = (fdata != null && fdata.isOnline);
//
//            label.setIcon(createStatusIcon(isOnline ? onlineColor : offlineColor));
//            label.setIconTextGap(10);
//            return label;
//        }
//    }
//
//    private Icon createStatusIcon(Color color) {
//        int diameter = 12;
//        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
//        Graphics2D g2 = img.createGraphics();
//        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2.setColor(color);
//        g2.fillOval(0, 0, diameter, diameter);
//        g2.dispose();
//        return new ImageIcon(img);
//    }
//
//    private static class FriendData {
//        String username;
//        String fullname;
//        boolean isOnline;
//
//        FriendData(String username, String fullname, boolean isOnline) {
//            this.username = username;
//            this.fullname = fullname;
//            this.isOnline = isOnline;
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new FriendsList("chandu")); // example username
//    }
//}
