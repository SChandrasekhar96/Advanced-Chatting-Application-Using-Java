package com.chat.app;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int SERVER_PORT = 12345;
    private static final Map<String, ObjectOutputStream> onlineUsers = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> offlineMessages = new HashMap<>();

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

    private static void deleteMessageForEveryone(String sender, String message) {
        String sql = "UPDATE messages SET message = 'This message was deleted.' WHERE sender = ? AND message = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, ObjectOutputStream> entry : onlineUsers.entrySet()) {
            String username = entry.getKey();
            ObjectOutputStream oos = entry.getValue();

            if (!username.equals(sender)) {
                try {
                    oos.writeObject("[DELETE_NOTIFICATION]" + sender + ":" + message);
                    oos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void deleteMessageForMe(String username, String message) {
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

    private static void sendMessageToReceiver(String receiver, String sender, String message) {
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

    private static void handleOfflineMessage(String sender, String receiver, String message) {
        saveMessageToDB(sender, receiver, message);
        synchronized (offlineMessages) {
            offlineMessages.computeIfAbsent(receiver, k -> new ArrayList<>()).add(sender + ": " + message);
        }
    }

    private static void sendUnseenMessages(String username) {
        List<String> messages = offlineMessages.get(username);
        if (messages != null && !messages.isEmpty()) {
            for (String message : messages) {
                try {
                    ObjectOutputStream out = onlineUsers.get(username);
                    if (out != null) {
                        out.writeObject("Offline message: " + message);
                        out.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            offlineMessages.remove(username);
        }
    }

    private static List<String> loadChatHistory(String user, String friend) {
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

    private static boolean isMessageDeletedForUser(String username, String message) {
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

    // ---- NEW METHOD to broadcast online/offline status to all users
    private static void broadcastStatus(String username, String status) {
        for (Map.Entry<String, ObjectOutputStream> entry : onlineUsers.entrySet()) {
            try {
                entry.getValue().writeObject("[STATUS]" + username + ":" + status);
                entry.getValue().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
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

                // 1. Read logged-in username and friend username
                username = (String) in.readObject();
                String friendUsername = (String) in.readObject();

                // 2. Add user to online users
                onlineUsers.put(username, out);
                System.out.println(username + " is now online.");

                // 3. Send friend status immediately (fix for "checking" status issue)
                String friendStatus = onlineUsers.containsKey(friendUsername) ? "online" : "offline";
                out.writeObject("[STATUS]" + friendUsername + ":" + friendStatus);
                out.flush();

                // 4. Notify everyone that this user is online
                ChatServer.broadcastStatus(username, "online");

                // 5. Send chat history between user and friend
                List<String> history = ChatServer.loadChatHistory(username, friendUsername);
                for (String msg : history) {
                    out.writeObject(msg);
                }
                out.flush();

                // 6. Send any unseen offline messages for this user
                ChatServer.sendUnseenMessages(username);

                // 7. Main loop to receive messages and commands
                while (true) {
                    try {
                        Object input = in.readObject();

                        // Handle delete commands
                        if (input instanceof String strInput &&
                                (strInput.equals("DELETE_FOR_ME") || strInput.equals("DELETE_FOR_EVERYONE"))) {
                            String msgToDelete = (String) in.readObject();
                            if (strInput.equals("DELETE_FOR_ME")) {
                                ChatServer.deleteMessageForMe(username, msgToDelete);
                            } else {
                                ChatServer.deleteMessageForEveryone(username, msgToDelete);
                            }
                            continue;
                        }

                        // Normal message sending: read receiver and message
                        String receiver = (String) input;
                        String message = (String) in.readObject();

                        if (onlineUsers.containsKey(receiver)) {
                            ChatServer.sendMessageToReceiver(receiver, username, message);
                        } else {
                            ChatServer.handleOfflineMessage(username, receiver, message);
                            out.writeObject("User " + receiver + " is offline. Your message has been saved.");
                            out.flush();
                        }
                    } catch (EOFException e) {
                        System.out.println(username + " has disconnected.");
                        break;
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
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
                    // Broadcast offline status to all users
                    ChatServer.broadcastStatus(username, "offline");
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
