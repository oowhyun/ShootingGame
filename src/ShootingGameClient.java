import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;
import javax.swing.*;

public class ShootingGameClient extends JPanel implements ActionListener, KeyListener {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final String clientId = "player_" + UUID.randomUUID();

    private long lastItemGenerationTime = 0;
    private static final long ITEM_GENERATION_INTERVAL = 9000;
    private Timer timer;
    private Image playerImage, backgroundImage, missileImage, hammerImage;
    private int playerX, playerY;
    private boolean[] keys;
    private List<Missile> missiles;
    private int directionX = 0;
    private int directionY = 0;
    private boolean isSpacePressed = false;
    private final Map<String, GameData> otherPlayers = new HashMap<>();
    private String playerRole;
    private boolean gameOver = false;
    private boolean gameOverPopupShown = false; // 팝업이 이미 표시되었는지 확인하는 플래그
    private boolean isWinner = false; // 승리 여부를 저장하는 변수
    private int lastDirectionX = 0;  // 마지막 X 방향
    private int lastDirectionY = -1; // 마지막 Y 방향 (기본적으로 위쪽)
    private int playerHP = 5;  // 자신의 HP
    private int speed = 5; // 기본 이동 속도
    private long speedBoostEndTime = 0; // 속도 증가 지속 시간

    private Image speedItemImage;
    private Image speedDownItemImage;
    private Image fanceImage;
    private Rectangle wallBounds; // 벽의 경계 영역
    private final List<GameData.Item> items = new ArrayList<>();


    public ShootingGameClient() {
        try {
            socket = new Socket("localhost", 12345);
            System.out.println("서버에 연결 성공!");

            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());

            GameData initialData = (GameData) in.readObject();
            playerRole = initialData.getPlayerRole();

            if ("Player1".equals(playerRole)) {
                playerImage = new ImageIcon("images/kirby.png").getImage();
                playerX = 150;
                playerY = 500;
            } else {
                playerImage = new ImageIcon("images/dididi.png").getImage();
                playerX = 450;
                playerY = 100;
            }

            backgroundImage = new ImageIcon("images/background.png").getImage();
            missileImage = new ImageIcon("images/starBullet.png").getImage();
            hammerImage = new ImageIcon("images/hammer.png").getImage();
            speedItemImage = new ImageIcon("images/speedUp.png").getImage();
            speedDownItemImage = new ImageIcon("images/speedDown.png").getImage();

            fanceImage = new ImageIcon("images/fance.png").getImage();
            // 울타리 크기 및 위치 조정
            int wallWidth = 500;  // 울타리의 너비 (맵 가로 크기와 동일)
            int wallHeight = 80;  // 울타리의 높이 (기존보다 더 높게 설정)
            int wallX = 0;        // X 위치는 맵의 왼쪽 끝
            int wallY = (600 / 2) - (wallHeight / 2);  // 맵 높이의 절반에서 울타리 높이의 절반만큼 뺌 (정중앙 배치)

            wallBounds = new Rectangle(wallX, wallY, wallWidth, wallHeight);


            keys = new boolean[256];
            missiles = new ArrayList<>();

            timer = new Timer(5, this);
            timer.start();

            addKeyListener(this);
            setFocusable(true);
            setPreferredSize(new Dimension(500, 600));

            new Thread(this::receiveData).start();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("연결 실패: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "서버에 연결할 수 없습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(backgroundImage, 0, 0, 500, 600, this);

        g.drawImage(fanceImage, wallBounds.x, wallBounds.y, wallBounds.width, wallBounds.height, this);
        g.drawImage(playerImage, playerX, playerY, 50, 50, this);

        synchronized (items) {
            for (GameData.Item item : items) {
                Image itemImage = "speed".equals(item.getType()) ? speedItemImage : speedDownItemImage;
                g.drawImage(itemImage, item.getX(), item.getY(), 30, 30, this);
            }
        }

        for (Missile missile : missiles) {
            Image currentMissileImage = "Player1".equals(playerRole) ? missileImage : hammerImage;
            g.drawImage(currentMissileImage, missile.getX() - 20, missile.getY(), 20, 20, this);
        }

        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                Rectangle player = data.getPlayer();
                if (player.x == 0 && player.y == 0) continue;
                Image otherPlayerImage = "Player1".equals(data.getPlayerRole())
                        ? new ImageIcon("images/kirby.png").getImage()
                        : new ImageIcon("images/dididi.png").getImage();
                g.drawImage(otherPlayerImage, player.x, player.y, 50, 50, this);

                for (Missile missile : data.getMissiles()) {
                    Image otherMissileImage = "Player1".equals(data.getPlayerRole()) ? missileImage : hammerImage;
                    g.drawImage(otherMissileImage, missile.getX() - 20, missile.getY(), 20, 20, this);
                }

                drawHpBar(g, player.x, player.y, data.getHp());
            }
        }

        drawHpBar(g, playerX, playerY, playerHP);

        if (gameOver && !gameOverPopupShown) {
            gameOverPopupShown = true;
            String message = isWinner ? "게임 오버! You Win!" : "게임 오버! You Lose!";//게임 종료시 팝업 표시해줌
            JOptionPane.showMessageDialog(this, message);
            System.exit(0);
        }
    }

    private void drawHpBar(Graphics g, int playerX, int playerY, int hp) { //캐릭터 위 hp바
        int barWidth = 50;
        int barHeight = 5;

        g.setColor(Color.BLACK);
        g.fillRect(playerX, playerY - 10, barWidth, barHeight);

        int currentBarWidth = (int) (barWidth * ((double) hp / 5));
        g.setColor(Color.RED);
        g.fillRect(playerX, playerY - 10, currentBarWidth, barHeight);
    }

    public void actionPerformed(ActionEvent e) {
        if (System.currentTimeMillis() - lastItemGenerationTime > ITEM_GENERATION_INTERVAL && lastItemGenerationTime > 0) {
            generateItems();
        }
        if (System.currentTimeMillis() - lastItemGenerationTime > ITEM_GENERATION_INTERVAL) {
            lastItemGenerationTime = System.currentTimeMillis();
        }
        if (keys[KeyEvent.VK_LEFT]) {
            playerX -= speed;
            directionX = -1;
        } else if (keys[KeyEvent.VK_RIGHT]) {
            playerX += speed;
            directionX = 1;
        } else {
            directionX = 0;
        }

        if (keys[KeyEvent.VK_UP]) {
            playerY -= speed;
            directionY = -1;
        } else if (keys[KeyEvent.VK_DOWN]) {
            playerY += speed;
            directionY = 1;
        } else {
            directionY = 0;
        }

        if (directionX != 0 || directionY != 0) {
            lastDirectionX = directionX;
            lastDirectionY = directionY;
        }
        if (System.currentTimeMillis() > speedBoostEndTime) {
            speed = 5;
        }

        // X축 이동 제한 (맵 가로 경계)
        playerX = Math.max(5, Math.min(playerX, getWidth() - playerImage.getWidth(null)+30));

        // Y축 이동 제한 (플레이어 역할에 따른 영역 제한)
        if ("Player1".equals(playerRole)) {
            // Player1은 울타리 아래쪽에서만 이동 가능
            playerY = Math.max(
                    wallBounds.y + wallBounds.height - 5, // 울타리 아래쪽 경계
                    Math.min(playerY, getHeight() - playerImage.getHeight(null)+30) // 화면 하단 경계
            );
        } else {
            // Player2는 울타리 위쪽에서만 이동 가능
            playerY = Math.max(
                    10, // 화면 위쪽 경계
                    Math.min(playerY, wallBounds.y - 20) // 울타리 상단 경계까지 이동 가능
            );
        }

        missiles.forEach(Missile::update);
        missiles.removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));

        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                data.getMissiles().forEach(Missile::update);
                data.getMissiles().removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));
            }
        }

        detectCollisions();
        sendPlayerData();
        repaint();
    }

    private void sendItemRemovalToServer(GameData.Item item) {
        try {
            // 제거된 아이템의 ID를 itemRemoved로 설정
            GameData data = new GameData(
                    clientId,
                    null, // 플레이어 정보는 전달하지 않음
                    null, // 미사일 정보는 전달하지 않음
                    null, // 추가 아이템 없음
                    "room_1", // 현재 방 ID
                    playerRole,
                    playerHP
            );
            data.setItemRemoved(item.getId()); // 제거된 아이템 ID 설정
            out.writeObject(data); // 서버로 데이터 전송
            out.flush();
        } catch (IOException e) {
            System.err.println("아이템 제거 전송 오류: " + e.getMessage());
        }
    }

    private void detectCollisions() {
        // 1. 미사일 충돌 처리
        synchronized (otherPlayers) {
            for (Iterator<Missile> it = missiles.iterator(); it.hasNext(); ) {
                Missile missile = it.next();
                for (GameData data : otherPlayers.values()) {
                    Rectangle otherPlayer = data.getPlayer();
                    if (otherPlayer != null && missile.getX() >= otherPlayer.x && missile.getX() <= otherPlayer.x + otherPlayer.width &&
                            missile.getY() >= otherPlayer.y && missile.getY() <= otherPlayer.y + otherPlayer.height) {
                        it.remove(); // 미사일 제거
                        break; // 더 이상 충돌 체크 불필요
                    }
                }
            }
        }

        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                List<Missile> otherMissiles = data.getMissiles();
                for (Iterator<Missile> it = otherMissiles.iterator(); it.hasNext(); ) {
                    Missile missile = it.next();
                    if (missile.getX() >= playerX && missile.getX() <= playerX + playerImage.getWidth(null) &&
                            missile.getY() >= playerY && missile.getY() <= playerY + playerImage.getHeight(null)) {
                        playerHP--; // 자신의 HP 감소
                        it.remove(); // 미사일 제거
                        sendPlayerData(); // 자신의 HP 정보 서버로 전송
                    }
                }
            }
        }

        // 2. 아이템 충돌 처리 (단순 제거 요청)
        synchronized (items) {
            Iterator<GameData.Item> itemIterator = items.iterator();
            while (itemIterator.hasNext()) {
                GameData.Item item = itemIterator.next();
                Rectangle itemBounds = new Rectangle(item.getX(), item.getY(), 30, 30);
                Rectangle playerBounds = new Rectangle(playerX, playerY, 50, 50);

                if (itemBounds.intersects(playerBounds)) {
                    if ("speedDown".equals(item.getType())) {
                        speed = 2; // 이동 속도 감소
                        speedBoostEndTime = System.currentTimeMillis() + 5000; // 5초 동안 지속
                    } else if ("speed".equals(item.getType())) {
                        speed = 7; // 이동 속도 증가
                        speedBoostEndTime = System.currentTimeMillis() + 5000; // 5초 동안 지속
                    }
                    itemIterator.remove(); // 아이템 제거
                    sendItemRemovalToServer(item); // 서버에 아이템 제거 요청
                }
            }
        }

        // 3. 플레이어의 HP가 0이면 게임 종료
        if (playerHP <= 0) {
            gameOver = true;
        }
    }

    private void sendPlayerData() {
        try {
            GameData data = new GameData(
                    clientId,
                    new Rectangle(playerX, playerY, playerImage.getWidth(null), playerImage.getHeight(null)),
                    new ArrayList<>(missiles),
                    null, // 아이템 제거는 별도로 처리하므로 null
                    "room_1", // 고정된 방 ID
                    playerRole,
                    playerHP
            );
            out.writeObject(data); // 서버로 데이터 전송
            out.flush();
        } catch (IOException e) {
            System.err.println("데이터 전송 오류: " + e.getMessage());
        }
    }

    private void generateItems() {
        // 5초마다 아이템을 두 개 생성
        if (System.currentTimeMillis() - lastItemGenerationTime > ITEM_GENERATION_INTERVAL) {
            lastItemGenerationTime = System.currentTimeMillis();

            // 아이템 크기 설정
            int itemWidth = 30;
            int itemHeight = 30;

            // 울타리 위쪽과 아래쪽 Y 위치 랜덤화
            int upperY = (int) (Math.random() * (wallBounds.y)); // 울타리 위쪽에서 랜덤 위치
            int lowerY = (int) (Math.random() * (getHeight() - wallBounds.y - wallBounds.height) + (wallBounds.y + wallBounds.height)); // 울타리 아래쪽에서 랜덤 위치

            // X 위치는 화면 너비 내에서 랜덤으로 설정
            int upperX = (int) (Math.random() * (getWidth() - itemWidth)); // 화면 너비 내에서 랜덤 위치
            int lowerX = (int) (Math.random() * (getWidth() - itemWidth)); // 화면 너비 내에서 랜덤 위치

            // 아이템 종류 랜덤 (speedUp, speedDown)
            String[] itemTypes = {"speed", "speedDown"};
            String itemType1 = itemTypes[(int) (Math.random() * itemTypes.length)];
            String itemType2 = itemTypes[(int) (Math.random() * itemTypes.length)];

            // Rectangle 객체로 bounds 생성
            Rectangle bounds1 = new Rectangle(upperX, upperY, itemWidth, itemHeight);
            Rectangle bounds2 = new Rectangle(lowerX, lowerY, itemWidth, itemHeight);

            // 아이템 생성
            GameData.Item item1 = new GameData.Item(UUID.randomUUID().toString(), bounds1, itemType1);
            GameData.Item item2 = new GameData.Item(UUID.randomUUID().toString(), bounds2, itemType2);

            // 생성된 아이템 리스트에 추가
            synchronized (items) {
                items.add(item1);
                items.add(item2);
            }

            // 생성된 아이템 서버에 전송
            sendItemToServer(item1);
            sendItemToServer(item2);
        }
    }

    private void sendItemToServer(GameData.Item item) {
        try {
            GameData data = new GameData(
                    clientId,
                    null, // 플레이어 정보는 전달하지 않음
                    null, // 미사일 정보는 전달하지 않음
                    Collections.singletonList(item), // 아이템 정보만 전송
                    "room_1", // 현재 방 ID
                    playerRole,
                    playerHP
            );
            out.writeObject(data);
            out.flush();
        } catch (IOException e) {
            System.err.println("아이템 전송 오류: " + e.getMessage());
        }
    }



    private void receiveData() {
        try {
            while (true) {
                GameData serverData = (GameData) in.readObject();

                // 다른 플레이어 정보 업데이트
                synchronized (otherPlayers) {
                    if (serverData.getPlayer() == null) {
                        otherPlayers.remove(serverData.getClientId());
                    } else {
                        otherPlayers.put(serverData.getClientId(), serverData);
                    }
                }

                // 아이템 정보 업데이트
                synchronized (items) {
                    if (serverData.getItems() != null) {
                        for (GameData.Item newItem : serverData.getItems()) {
                            boolean exists = items.stream().anyMatch(existingItem -> existingItem.getId().equals(newItem.getId()));
                            if (!exists) {
                                items.add(newItem);
                            }
                        }
                        System.out.println("수신된 아이템: " + serverData.getItems());

                    }

                    // 제거 요청된 아이템 삭제
                    if (serverData.getItemRemoved() != null) {
                        items.removeIf(item -> item.getId().equals(serverData.getItemRemoved()));
                    }

                    // 디버깅용: 현재 아이템 리스트 출력
                    System.out.println("현재 아이템 리스트: " + items);
                }



                // 플레이어 HP 및 기타 정보 업데이트
                synchronized (otherPlayers) {
                    GameData otherPlayerData = otherPlayers.get(serverData.getClientId());
                    if (otherPlayerData != null) {
                        if (serverData.getClientId().equals(clientId)) {
                            // 자신의 데이터인 경우 HP 동기화
                            playerHP = serverData.getHp();
                        } else {
                            // 다른 플레이어 데이터 업데이트
                            otherPlayerData.setHp(serverData.getHp());
                        }
                    }
                }

                // 게임 종료 상태 처리
                if (serverData.isGameOver()) {
                    gameOver = true;
                    isWinner = serverData.isWinner();
                }

                repaint(); // 화면 갱신
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버와의 연결이 끊겼습니다: " + e.getMessage());
        }
    }




    // 키 이벤트 처리
    public void keyPressed(KeyEvent e) {
        keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE && !isSpacePressed) {
            isSpacePressed = true;
            shootMissile();
        }
    }

    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            isSpacePressed = false;
        }
    }

    public void keyTyped(KeyEvent e) {}

    private void shootMissile() {
        int missileX = playerX + playerImage.getWidth(null) / 2 - missileImage.getWidth(null) / 2;
        int missileY = playerY + playerImage.getHeight(null) / 2 - missileImage.getHeight(null) / 2;

        // 마지막 저장된 방향으로 미사일 발사
        Missile newMissile = new Missile(missileX, missileY, lastDirectionX, lastDirectionY);
        missiles.add(newMissile);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game - Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        ShootingGameClient gamePanel = new ShootingGameClient();
        frame.add(gamePanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        gamePanel.requestFocusInWindow();
    }
}