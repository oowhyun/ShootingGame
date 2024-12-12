import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.awt.*;

public class ShootingGameServer {
    private static final int PORT = 12345;
    private final List<Room> rooms = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Room> clientRoomMap = Collections.synchronizedMap(new HashMap<>());

    public void start() {
        System.out.println("서버가 시작되었습니다. 클라이언트를 기다리는 중...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("새 클라이언트 연결: " + socket.getInetAddress().getHostAddress());
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.out.println("서버 에러: " + e.getMessage());
        }
    }

    private synchronized Room findOrCreateRoom(ClientHandler client) {
        for (Room room : rooms) {
            if (!room.isFull()) {
                room.addPlayer(client);
                clientRoomMap.put(client.getClientId(), room);
                return room;
            }
        }

        Room newRoom = new Room(UUID.randomUUID().toString());
        newRoom.addPlayer(client);
        rooms.add(newRoom);
        clientRoomMap.put(client.getClientId(), newRoom);
        return newRoom;
    }

    protected class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getClientId() {
            return clientId;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                clientId = UUID.randomUUID().toString();

                Room room = findOrCreateRoom(this);
                String playerRole = room.getPlayerRole(this);

                out.writeObject(new GameData(clientId, null, null, room.getRoomId(), playerRole));
                out.flush();

                while (true) {
                    GameData data = (GameData) in.readObject();
                    data.setClientId(clientId);
                    room.broadcast(data, this);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("클라이언트 연결 종료: " + clientId);

                Room room = clientRoomMap.get(clientId);
                if (room != null) {
                    room.removePlayer(this);
                    if (room.isEmpty()) {
                        rooms.remove(room);
                    }
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("소켓 닫기 오류: " + e.getMessage());
                }
            }
        }

        public void sendData(GameData data) throws IOException {
            out.writeObject(data);
            out.flush();
        }
    }

    public static void main(String[] args) {
        new ShootingGameServer().start();
    }
}

class Room {
    private final String roomId;
    private final List<ShootingGameServer.ClientHandler> players = new ArrayList<>(2);

    public Room(String roomId) {
        this.roomId = roomId;
    }

    public synchronized void addPlayer(ShootingGameServer.ClientHandler player) {
        if (players.size() < 2) {
            players.add(player);
        }
    }

    public synchronized void removePlayer(ShootingGameServer.ClientHandler player) {
        players.remove(player);
    }

    public synchronized boolean isFull() {
        return players.size() == 2;
    }

    public synchronized boolean isEmpty() {
        return players.isEmpty();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPlayerRole(ShootingGameServer.ClientHandler player) {
        return players.indexOf(player) == 0 ? "Player1" : "Player2";
    }

    public void broadcast(GameData data, ShootingGameServer.ClientHandler sender) {
        for (ShootingGameServer.ClientHandler player : players) {
            if (player != sender) {
                try {
                    player.sendData(data); // 데이터 전송
                } catch (IOException e) {
                    System.out.println("데이터 전송 오류: " + player.getClientId());
                }
            }
        }
    }
}
