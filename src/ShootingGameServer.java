import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ShootingGameServer {
    private static final int PORT = 12345;
    private final List<Room> rooms = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Room> clientRoomMap = Collections.synchronizedMap(new HashMap<>());
    private final ServerGUI gui;

    public ShootingGameServer() {
        gui = new ServerGUI();
    }

    public void start() {
        System.out.println("서버가 시작되었습니다.");
        System.out.println("클라이언트를 기다리는 중...");
        gui.log("서버가 시작되었습니다.\n클라이언트를 기다리는 중...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("새 클라이언트 연결: " + socket.getInetAddress().getHostAddress());
                gui.log("새 클라이언트 연결: " + socket.getInetAddress().getHostAddress());
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            gui.log("서버 에러: " + e.getMessage());
            System.out.println("서버 에러: " + e.getMessage());
        }
    }

    private synchronized Room findOrCreateRoom(ClientHandler client) {
        for (Room room : rooms) {
            if (!room.isFull()) {
                room.addPlayer(client);
                clientRoomMap.put(client.getClientId(), room);
                updateRoomTable();
                return room;
            }
        }

        Room newRoom = new Room(UUID.randomUUID().toString());
        newRoom.addPlayer(client);
        rooms.add(newRoom);
        clientRoomMap.put(client.getClientId(), newRoom);
        updateRoomTable();
        return newRoom;
    }

    private void updateRoomTable() {
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel model = gui.getRoomTableModel();
            model.setRowCount(0);
            synchronized (rooms) {
                for (Room room : rooms) {
                    model.addRow(new Object[]{room.getRoomId(), room.getPlayerCount()});
                }
            }
        });
    }


    protected class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String clientId;
        private int hp = 5;
        private String playerRole;
        private GameData gameData;

        public int getHp() {
            return hp;
        }
        public void setHp(int hp) {
            this.hp = hp;
        }

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = UUID.randomUUID().toString(); // clientId 생성
            this.gameData = new GameData(clientId, new Rectangle(0, 0, 50, 50), new ArrayList<>(), new ArrayList<>(), null, null, hp);
        }
        public GameData getGameData() {
            return gameData;
        }

        public String getClientId() {
            return clientId;
        }

        public String getPlayerRole() {
            return playerRole;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                clientId = UUID.randomUUID().toString();

                Room room = findOrCreateRoom(this);
                playerRole = room.getPlayerRole(this);

                gui.log("클라이언트 연결됨: " + clientId + " (방: " + room.getRoomId() + ")");
                gui.addClient(clientId, room.getRoomId());

                GameData initialData = new GameData(clientId, new Rectangle(), new ArrayList<>(), new ArrayList<>(), room.getRoomId(), playerRole, hp);
                out.writeObject(initialData);
                out.flush();

                while (true) {
                    GameData data = (GameData) in.readObject();
                    data.setClientId(clientId);
                    // HP 감소 처리
                    if (data.getHp() < hp) {
                        hp = data.getHp();
                        if (hp < 0) hp = 0;
                    }
                    room.processGameData(data, this);
                    room.checkGameOver();
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("클라이언트 연결 종료: " + clientId);
                gui.log("클라이언트 연결 종료: " + clientId);

                Room room = clientRoomMap.get(clientId);
                if (room != null) {
                    room.removePlayer(this);
                    if (room.isEmpty()) {
                        rooms.remove(room);
                    }
                    updateRoomTable();
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

    private void broadcastItemRemoval(String itemId) {
        for (ShootingGameServer.ClientHandler player : players) {
            try {
                GameData removalData = player.getGameData();
                removalData.setItemRemoved(itemId);
                player.sendData(removalData);
            } catch (IOException e) {
                System.out.println("아이템 삭제 정보 전송 오류: " + player.getClientId());
            }
        }
    }

    public synchronized void addPlayer(ShootingGameServer.ClientHandler player) {
        if (players.size() < 2) {
            players.add(player);
            if (players.size() == 2) {
                broadcastGameStart();
            }
        }
    }

    private void broadcastGameStart() {
        for (ShootingGameServer.ClientHandler player : players) {
            try {
                GameData gameStartData = new GameData(player.getClientId(), null, null, null, roomId, player.getPlayerRole(), player.getHp());
                gameStartData.setGameStarted(true);
                player.sendData(gameStartData);
            } catch (IOException e) {
                System.out.println("게임 시작 메시지 전송 오류: " + player.getClientId());
            }
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

    public synchronized int getPlayerCount() {
        return players.size();
    }

    public String getPlayerRole(ShootingGameServer.ClientHandler player) {
        return players.indexOf(player) == 0 ? "Player1" : "Player2";
    }

    public synchronized void processGameData(GameData data, ShootingGameServer.ClientHandler sender) {
        // 미사일 처리
        for (Missile missile : data.getMissiles()) {
            missile.update();

            if (missile.isProcessed()) continue; // 이미 처리된 미사일은 건너뜀

            for (ShootingGameServer.ClientHandler player : players) {
                if (!player.equals(sender)) {
                    Rectangle playerBounds = new Rectangle(player.getGameData().getPlayer().x, player.getGameData().getPlayer().y, 50, 50);

                    if (playerBounds.intersects(missile.getBounds())) {
                        player.setHp(Math.max(player.getHp() - 1, 0)); // HP 감소, 최소값 0
                        missile.setProcessed(true); // 충돌 처리 완료
                        break;
                    }
                }
            }
        }

        // 아이템 처리
        for (Iterator<GameData.Item> iterator = data.getItems().iterator(); iterator.hasNext();) {
            GameData.Item item = iterator.next();

            if (item.isProcessed()) continue; // 이미 처리된 아이템은 건너뜀

            Rectangle itemBounds = new Rectangle(item.getX(), item.getY(), 30, 30);
            if (data.getPlayer().intersects(itemBounds)) {
                item.setProcessed(true); // 아이템 처리 완료
                iterator.remove();
                broadcastItemRemoval(item.getId());
            }
        }

        // 상태 업데이트
        data.setHp(sender.getHp());
        broadcast(data, sender);
    }

    public void broadcast(GameData data, ShootingGameServer.ClientHandler sender) {
        for (ShootingGameServer.ClientHandler player : players) {
            if (player != sender) {
                try {
                    player.sendData(data);
                } catch (IOException e) {
                    System.out.println("데이터 전송 오류: " + player.getClientId());
                }
            }
        }
    }

    public synchronized void checkGameOver() {
        if (players.size() != 2) return;

        ShootingGameServer.ClientHandler player1 = players.get(0);
        ShootingGameServer.ClientHandler player2 = players.get(1);

        int player1Hp = player1.getHp();
        int player2Hp = player2.getHp();

        if (player1Hp <= 0 || player2Hp <= 0) {
            String winner = player1Hp > 0 ? "Player1" : "Player2";
            broadcastGameOver(winner);
        }
    }

    private void broadcastGameOver(String winner) {
        for (ShootingGameServer.ClientHandler player : players) {
            try {
                GameData gameOverData = new GameData(player.getClientId(), null, null, null, roomId, player.getPlayerRole(), player.getHp());
                gameOverData.setGameOver(true);
                gameOverData.setWinner(winner.equals(player.getPlayerRole()));
                player.sendData(gameOverData);
            } catch (IOException e) {
                System.out.println("게임 종료 메시지 전송 오류: " + player.getClientId());
            }
        }
    }
}


class ServerGUI {
    private final JFrame frame;
    private final JTextArea logArea;
    private final JTable roomTable;
    private final DefaultTableModel roomTableModel;

    public ServerGUI() {
        frame = new JFrame("Shooting Game - Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 400);
        frame.setLayout(new BorderLayout());

        // 로그 창
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("서버 로그"));

        // 방 정보 테이블
        roomTableModel = new DefaultTableModel(new Object[]{"방 ID", "플레이어 수"}, 0);
        roomTable = new JTable(roomTableModel);
        JScrollPane tableScrollPane = new JScrollPane(roomTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("방 상태"));

        frame.add(logScrollPane, BorderLayout.CENTER);
        frame.add(tableScrollPane, BorderLayout.EAST);
        frame.setVisible(true);
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    public void addClient(String clientId, String roomId) {
        log("클라이언트 추가: " + clientId + " (방: " + roomId + ")");
    }

    public DefaultTableModel getRoomTableModel() {
        return roomTableModel;
    }
}