package com.chat.app;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int SERVER_PORT = 12345;

    // Use ConcurrentHashMap for thread-safe online users map
    private static final Map<String, ObjectOutputStream> onlineUsers = new ConcurrentHashMap<>();

    // Use ConcurrentHashMap for offlineMessages map too
    private static final Map<String, List<String>> offlineMessages = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Server started...");
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/chatapp";
        String user = "root";
        String password = "Chandu@96";
        return DriverManager.getConnection(url, user, password);
    }

    private static void saveMessageToDB(String sender, String receiver, String message) {
        String sql = "INSERT INTO messages (sender, receiver, message) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteMessageForEveryone(String sender, String message) {
        // This query may not be supported by all DBs (LIMIT in UPDATE). Adjust if needed.
        String sql = "UPDATE messages SET message = 'This message was deleted.' WHERE sender = ? AND message = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Notify all online users about the deletion (including sender)
        broadcastDeletion(sender, message);
    }

    private static void broadcastDeletion(String sender, String message) {
        for (Map.Entry<String, ObjectOutputStream> entry : onlineUsers.entrySet()) {
            try {
                entry.getValue().writeObject("[DELETE_NOTIFICATION]" + sender + ":" + message);
                entry.getValue().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void deleteMessageForMe(String username, String message) {
        String sql = "INSERT INTO deleted_messages (username, message) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void sendMessageToReceiver(String receiver, String sender, String message) {
        saveMessageToDB(sender, receiver, message);
        try {
            ObjectOutputStream receiverOut = onlineUsers.get(receiver);
            if (receiverOut != null) {
                receiverOut.writeObject(sender + ": " + message);
                receiverOut.flush();
            } else {
                System.out.println("Receiver not online: " + receiver);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void handleOfflineMessage(String sender, String receiver, String message) {
        saveMessageToDB(sender, receiver, message);
        offlineMessages.computeIfAbsent(receiver, k -> new ArrayList<>()).add(sender + ": " + message);
    }

    public static void sendUnseenMessages(String username) {
        List<String> messages = offlineMessages.get(username);
        if (messages != null && !messages.isEmpty()) {
            try {
                ObjectOutputStream out = onlineUsers.get(username);
                if (out != null) {
                    for (String message : messages) {
                        out.writeObject("Offline message: " + message);
                    }
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            offlineMessages.remove(username);
        }
    }

    public static List<String> loadChatHistory(String user, String friend) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM messages " +
                "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                "ORDER BY timestamp ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user);
            pstmt.setString(2, friend);
            pstmt.setString(3, friend);
            pstmt.setString(4, user);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg = rs.getString("message");
                Timestamp ts = rs.getTimestamp("timestamp");

                if (isMessageDeletedForUser(user, msg)) continue;

                history.add("[" + ts.toString() + "] " + sender + ": " + msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return history;
    }

    public static boolean isMessageDeletedForUser(String username, String message) {
        String sql = "SELECT * FROM deleted_messages WHERE username = ? AND message = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, message);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Broadcast online/offline status update to all online users
    public static void broadcastStatus(String username, String status) {
        for (Map.Entry<String, ObjectOutputStream> entry : onlineUsers.entrySet()) {
            try {
                entry.getValue().writeObject("[STATUS]" + username + ":" + status);
                entry.getValue().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Static method to check if a user is online (used by other classes)
    public static boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Receive logged-in username and friend username
                username = (String) in.readObject();
                String friendUsername = (String) in.readObject();

                // Add user to online users
                onlineUsers.put(username, out);
                System.out.println(username + " is now online.");

                // Send friend's status immediately (fix status check issue)
                String friendStatus = isUserOnline(friendUsername) ? "online" : "offline";
                out.writeObject("[STATUS]" + friendUsername + ":" + friendStatus);
                out.flush();

                // Notify all users that this user is online
                broadcastStatus(username, "online");

                // Send chat history between user and friend
                List<String> history = loadChatHistory(username, friendUsername);
                for (String msg : history) {
                    out.writeObject(msg);
                }
                out.flush();

                // Send unseen offline messages to this user
                sendUnseenMessages(username);

                // Main loop to receive messages and commands
                while (true) {
                    try {
                        Object input = in.readObject();

                        if (input instanceof String strInput) {
                            if (strInput.equals("DELETE_FOR_ME") || strInput.equals("DELETE_FOR_EVERYONE")) {
                                String msgToDelete = (String) in.readObject();
                                if (strInput.equals("DELETE_FOR_ME")) {
                                    deleteMessageForMe(username, msgToDelete);
                                } else {
                                    deleteMessageForEveryone(username, msgToDelete);
                                }
                                continue;
                            }
                        }

                        // Normal message sending: first read receiver then message
                        String receiver = (String) input;
                        String message = (String) in.readObject();

                        if (onlineUsers.containsKey(receiver)) {
                            sendMessageToReceiver(receiver, username, message);
                        } else {
                            handleOfflineMessage(username, receiver, message);
                            out.writeObject("User " + receiver + " is offline. Your message has been saved.");
                            out.flush();
                        }
                    } catch (EOFException e) {
                        System.out.println(username + " has disconnected.");
                        break;
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            try {
                if (username != null) {
                    onlineUsers.remove(username);
                    System.out.println(username + " has gone offline.");
                    broadcastStatus(username, "offline");
                }
                if (socket != null) socket.close();
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
