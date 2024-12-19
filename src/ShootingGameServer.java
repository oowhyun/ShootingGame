import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
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

        public int getHp() {
            return hp;
        }

        // HP setter
        public void setHp(int hp) {
            this.hp = hp;
        }
        public ClientHandler(Socket socket) {
            this.socket = socket;
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
    private final List<Item> items = new ArrayList<>(); // 아이템 리스트
    private static final long ITEM_LIFETIME = 10000; // 10초 후 아이템 파괴
    private static final int ITEM_SPAWN_INTERVAL = 5000; // 아이템 생성 주기 (5초마다 생성)
    private Timer itemTimer;
    private boolean gameStarted = false;

    public Room(String roomId) {
        this.roomId = roomId;
    }

    private void startItemManagement() {
        // 아이템 생성 및 파괴 관리
        itemTimer = new Timer();
        itemTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 아이템 생성
                spawnItem();

                // 아이템 만료 처리
                removeExpiredItems();
            }
        }, 0, ITEM_SPAWN_INTERVAL);
    }

    private synchronized void spawnItem() {
        Random random = new Random();
        int x = random.nextInt(100, 400); // 랜덤 x 위치 (100~400)
        int y = random.nextInt(100, 400); // 랜덤 y 위치 (100~400)

        String itemType = random.nextBoolean() ? "hp" : "speed"; // 50% 확률로 HP 또는 Speed 아이템 생성
        Item newItem = new Item("item" + UUID.randomUUID(), new Rectangle(x, y, 30, 30), itemType);

        items.add(newItem);
        broadcastItemUpdate(newItem);  // 모든 플레이어에게 아이템 업데이트 전송
    }

    private synchronized void removeExpiredItems() {
        long currentTime = System.currentTimeMillis();
        Iterator<Item> iterator = items.iterator();

        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (currentTime - item.getCreationTime() > ITEM_LIFETIME) {
                iterator.remove(); // 아이템 제거
                broadcastItemRemoval(item.getId()); // 아이템 제거 정보 브로드캐스트
            }
        }
    }

    private void broadcastItemUpdate(Item newItem) {
        GameData itemData = new GameData(null, null, null, items, roomId, null, 0);
        itemData.setNewItem(newItem);

        // 모든 플레이어에게 아이템 생성 정보를 전송
        for (ShootingGameServer.ClientHandler player : players) {
            try {
                player.sendData(itemData);
            } catch (IOException e) {
                System.out.println("아이템 정보 전송 오류: " + player.getClientId());
            }
        }
    }

    private void broadcastItemRemoval(String itemId) {
        GameData removalData = new GameData(null, null, null, items, roomId, null, 0);
        removalData.setItemRemoved(itemId);

        // 모든 플레이어에게 아이템 제거 정보를 전송
        for (ShootingGameServer.ClientHandler player : players) {
            try {
                player.sendData(removalData);
            } catch (IOException e) {
                System.out.println("아이템 삭제 정보 전송 오류: " + player.getClientId());
            }
        }
    }

    public synchronized void addPlayer(ShootingGameServer.ClientHandler player) {
        if (players.size() < 2) {
            players.add(player);
            // 두 명의 플레이어가 모두 접속했을 때 게임을 시작하도록 체크
            if (players.size() == 2) {
                gameStarted = true;  // 게임 시작
                startItemManagement();  // 아이템 생성 시작
                broadcastGameStart();  // 게임 시작 메시지 전송
            }
        }
    }
    private void broadcastGameStart() {
        // 게임 시작 메시지
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

            // 상대 플레이어 충돌 처리
            for (ShootingGameServer.ClientHandler player : players) {
                if (!player.equals(sender)) {
                    Rectangle playerBounds = new Rectangle(50, 50, 50, 50); // 플레이어 크기 설정
                    if (playerBounds.intersects(missile.getBounds())) {
                        data.setHp(player.getHp());  // HP 변경
                        break;
                    }
                }
            }
        }

        // 아이템 충돌 처리
        for (Iterator<Item> iterator = items.iterator(); iterator.hasNext();) {
            Item item = iterator.next();
            if (data.getPlayer().intersects(item.getBounds())) {
                if (item.getType().equals("hp")) {
                    // HP 아이템 획득 시 체력 증가
                    sender.setHp(Math.min(sender.getHp() + 1, 5)); // 최대 HP는 5로 제한
                }
                iterator.remove(); // 아이템 제거
                broadcastItemRemoval(item.getId()); // 아이템 제거 정보 브로드캐스트
            }
        }

        // HP 갱신 후 데이터 전송
        data.setHp(sender.getHp());  // 갱신된 HP 반영
        broadcast(data, sender);  // 다른 플레이어들에게 업데이트된 데이터 전송
    }

    public void broadcast(GameData data, ShootingGameServer.ClientHandler sender) {
        for (ShootingGameServer.ClientHandler player : players) {
            if (player != sender) {
                try {
                    player.sendData(data);  // 갱신된 데이터 전송
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

    public List<Item> getItems() {
        return items;
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