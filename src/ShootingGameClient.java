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
    private static final long ITEM_GENERATION_INTERVAL = 5000; // 5초
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
    private Random random;  // 랜덤 객체 추가
    private long globalSeed = 123456789L;
    private int playerHP = 5;  // 자신의 HP
    private int speed = 5; // 기본 이동 속도
    private long speedBoostEndTime = 0; // 속도 증가 지속 시간
    private Image speedItemImage; // 아이템 이미지
    private List<SpeedItem> speedItems; // 아이템 리스트
    private Image hpItemImage;  // heart.png 이미지
    private List<HpItem> hpItems; // HP 아이템 리스트



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
            speedItemImage = new ImageIcon("images/speed.png").getImage();
            speedItems = new ArrayList<>();
            hpItemImage = new ImageIcon("images/heart.png").getImage();
            hpItems = new ArrayList<>();


            random = new Random(globalSeed);

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

        g.drawImage(playerImage, playerX, playerY, 50, 50, this);

        synchronized (speedItems) {
            for (SpeedItem item : speedItems) {
                if (item.isActive()) {
                    item.draw(g, speedItemImage);
                }
            }
        }
        synchronized (hpItems) {
            for (HpItem item : hpItems) {
                if (item.isActive()) {
                    item.draw(g, hpItemImage);
                }
            }
        }

        // 자신의 미사일 렌더링
        for (Missile missile : missiles) {
            Image currentMissileImage = "Player1".equals(playerRole) ? missileImage : hammerImage;
            g.drawImage(currentMissileImage, missile.getX()-20, missile.getY(), 20, 20, this);
        }

        // 다른 플레이어 렌더링 및 미사일
        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                Rectangle player = data.getPlayer();
                Image otherPlayerImage = "Player1".equals(data.getPlayerRole())
                        ? new ImageIcon("images/kirby.png").getImage()
                        : new ImageIcon("images/dididi.png").getImage();
                g.drawImage(otherPlayerImage, player.x, player.y, 50, 50, this);

                // 다른 플레이어의 미사일 렌더링
                for (Missile missile : data.getMissiles()) {
                    Image otherMissileImage = "Player1".equals(data.getPlayerRole()) ? missileImage : hammerImage;
                    g.drawImage(otherMissileImage, missile.getX()-20, missile.getY(), 20, 20, this);
                }

                // 다른 플레이어의 HP 표시
                int hp = data.getHp();
                drawHpBar(g, player.x, player.y, hp);
            }
        }

        // 자신의 HP 표시
        drawHpBar(g, playerX, playerY, playerHP);

        // 승리/패배에 따라 팝업 메시지 표시
        if (gameOver && !gameOverPopupShown) {
            gameOverPopupShown = true; // 팝업이 한 번만 표시되도록 설정
            String message = isWinner ? "게임 오버! You Win!" : "게임 오버! You Lose!";
            JOptionPane.showMessageDialog(this, message);
            System.exit(0); // 게임 종료
        }
    }

    private void  drawHpBar(Graphics g, int playerX, int playerY, int hp) {
        int barWidth = 50;
        int barHeight = 5;

        g.setColor(Color.BLACK);
        g.fillRect(playerX, playerY - 10, barWidth, barHeight);

        int currentBarWidth = (int) (barWidth * ((double) hp / 5));
        g.setColor(Color.RED);
        g.fillRect(playerX, playerY - 10, currentBarWidth, barHeight);
    }

    public void actionPerformed(ActionEvent e) {
        if (System.currentTimeMillis() - lastItemGenerationTime > ITEM_GENERATION_INTERVAL) {
            generateRandomItems();  // 아이템 생성
            lastItemGenerationTime = System.currentTimeMillis();  // 마지막 생성 시간 업데이트
        }
        checkItemExpiration();  // 아이템 만료 체크
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
            speed = 5; // 기본 속도로 복귀
        }

        playerX = Math.max(0, Math.min(playerX, getWidth() - playerImage.getWidth(null)));
        playerY = Math.max(0, Math.min(playerY, getHeight() - playerImage.getHeight(null)));

        // 미사일 업데이트
        missiles.forEach(Missile::update);
        missiles.removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));

        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                data.getMissiles().forEach(Missile::update);
                data.getMissiles().removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));
            }
        }

        detectCollisions();
        checkItemCollisions();
        sendPlayerData();
        repaint();
    }
    private void checkItemCollisions() {
        synchronized (speedItems) {
            for (SpeedItem item : speedItems) {
                if (item.isActive() && item.getBounds().intersects(new Rectangle(playerX, playerY, 50, 50))) {
                    item.deactivate();
                    speed = 8; // 이동 속도 증가
                    speedBoostEndTime = System.currentTimeMillis() + 5000; // 5초 지속
                    sendItemPickupToServer(item); // 서버에 아이템 획득 알림
                }
            }
        }

        synchronized (hpItems) {
            for (HpItem item : hpItems) {
                if (item.isActive() && item.getBounds().intersects(new Rectangle(playerX, playerY, 50, 50))) {
                    item.deactivate();
                    playerHP = Math.min(playerHP + 2, 5); // HP를 최대 5까지만 증가
                    sendItemPickupToServer(item); // 서버에 아이템 획득 알림
                    repaint();
                }
            }
        }
    }

    private void sendItemPickupToServer(Item item) {
        try {
            List<Item> itemList = new ArrayList<>(); // 리스트 생성
            itemList.add(item);  // 아이템을 리스트에 추가

            out.writeObject(new GameData(playerRole, null, null, itemList, null, playerRole, playerHP));
            out.flush();
        } catch (IOException e) {
            System.err.println("아이템 상태 전송 오류: " + e.getMessage());
        }
    }





    private void detectCollisions() {
        synchronized (otherPlayers) {
            for (Iterator<Missile> it = missiles.iterator(); it.hasNext(); ) {
                Missile missile = it.next();
                for (GameData data : otherPlayers.values()) {
                    Rectangle otherPlayer = data.getPlayer();
                    if (otherPlayer != null && missile.getX() >= otherPlayer.x && missile.getX() <= otherPlayer.x + otherPlayer.width &&
                            missile.getY() >= otherPlayer.y && missile.getY() <= otherPlayer.y + otherPlayer.height) {
                        data.decreaseHp(1);  // 다른 플레이어 HP 감소
                        it.remove();
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
                        playerHP--;  // 자신의 HP 감소
                        it.remove();
                        sendPlayerData();
                    }
                }
            }
        }

        // 플레이어의 HP가 0이면 게임 종료
        if (playerHP <= 0) {
            gameOver = true;
        }
    }

    private void sendPlayerData() {
        try {
            String roomId = "room_1";  // 고정된 방 ID 예시

            GameData data = new GameData(clientId, new Rectangle(playerX, playerY, playerImage.getWidth(null), playerImage.getHeight(null)),
                    new ArrayList<>(missiles), new ArrayList<>(speedItems), roomId, playerRole, playerHP);
            out.writeObject(data);
            out.flush();
        } catch (IOException e) {
            System.err.println("데이터 전송 오류: " + e.getMessage());
        }
    }
    private void generateRandomItems() {
        int x = random.nextInt(300) + 100; // 100 ~ 400 범위 (가로)
        int y = random.nextInt(400) + 100; // 100 ~ 500 범위 (세로)

        // 랜덤으로 SpeedItem 또는 HpItem 생성
        if (random.nextBoolean()) {
            SpeedItem newItem = new SpeedItem("speedItem_" + UUID.randomUUID().toString(), x, y);
            synchronized (speedItems) {
                speedItems.add(newItem);
            }
        } else {
            HpItem newItem = new HpItem("hpItem_" + UUID.randomUUID().toString(), x, y);
            synchronized (hpItems) {
                hpItems.add(newItem);
            }
        }
    }


    private void checkItemExpiration() {
        synchronized (speedItems) {
            Iterator<SpeedItem> iterator = speedItems.iterator();
            while (iterator.hasNext()) {
                SpeedItem item = iterator.next();
                if (!item.isActive()) {
                    iterator.remove(); // 비활성화된 아이템 제거
                }
            }
        }
    }


    private void receiveData() {
        try {
            while (true) {
                GameData serverData = (GameData) in.readObject();

                synchronized (otherPlayers) {
                    if (serverData.getPlayer() == null) {
                        otherPlayers.remove(serverData.getClientId());
                    } else {
                        otherPlayers.put(serverData.getClientId(), serverData);
                    }
                }

                // 아이템 정보 업데이트
                if (serverData.getSpeedItems() != null) {
                    synchronized (speedItems) {
                        speedItems.clear();
                        speedItems.addAll(serverData.getSpeedItems());
                    }
                }

                // 게임 종료 및 승리 정보
                if (serverData.isGameOver()) {
                    gameOver = true;
                    isWinner = serverData.isWinner();
                }

                // 다른 플레이어의 HP 업데이트
                synchronized (otherPlayers) {
                    otherPlayers.get(serverData.getClientId()).setHp(serverData.getHp());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버와의 연결이 끊겼습니다.");
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