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

    private Timer timer;
    private Image playerImage, backgroundImage, missileImage, hammerImage, heartImage;
    private int playerX, playerY;
    private boolean[] keys;
    private List<Missile> missiles;
    private int directionX = 0; // x축 방향 (-1: 왼쪽, 1: 오른쪽, 0: 정지)
    private int directionY = 0;
    private boolean isSpacePressed = false;
    private int playerHP = 3; // 현재 플레이어의 HP
    private final Map<String, Integer> otherPlayerHP = new HashMap<>();
    private final Map<String, GameData> otherPlayers = new HashMap<>();
    private String playerRole;

    public ShootingGameClient() {
        try {
            socket = new Socket("localhost", 12345);
            System.out.println("서버에 연결 성공!");

            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());

            GameData initialData = (GameData) in.readObject();
            playerRole = initialData.getPlayerRole();  // 플레이어 역할을 받아옵니다.

            // 플레이어 역할에 따라 이미지 및 초기 위치 설정
            if ("Player1".equals(playerRole)) {
                playerImage = new ImageIcon("images/kirby.png").getImage();
                playerX = 450;  // 플레이어 1은 화면 아래쪽
                playerY = 800;
            } else {
                playerImage = new ImageIcon("images/dididi.png").getImage();
                playerX = 450;  // 플레이어 2는 화면 위쪽
                playerY = 150;
            }

            backgroundImage = new ImageIcon("images/background.png").getImage();
            missileImage = new ImageIcon("images/starBullet.png").getImage();
            hammerImage = new ImageIcon("images/hammer.png").getImage();
            heartImage = new ImageIcon("images/heart.png").getImage();

            keys = new boolean[256];
            missiles = new ArrayList<>();

            timer = new Timer(15, this);
            timer.start();

            addKeyListener(this);
            setFocusable(true);
            setPreferredSize(new Dimension(960, 1000));

            new Thread(this::receiveData).start();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("연결 실패: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "서버에 연결할 수 없습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(backgroundImage, 0, 0, this);
        g.drawImage(playerImage, playerX, playerY, this);

        // 현재 클라이언트의 미사일 렌더링
        for (Missile missile : missiles) {
            Image currentMissileImage = "Player1".equals(playerRole) ? missileImage : hammerImage;
            g.drawImage(currentMissileImage, missile.getX(), missile.getY(), this);
        }

        // 다른 플레이어 렌더링 및 미사일
        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                Rectangle player = data.getPlayer();
                Image otherPlayerImage = "Player1".equals(data.getPlayerRole())
                        ? new ImageIcon("images/kirby.png").getImage()
                        : new ImageIcon("images/dididi.png").getImage();
                g.drawImage(otherPlayerImage, player.x, player.y, this);

                for (Missile missile : data.getMissiles()) {
                    Image otherMissileImage = "Player1".equals(data.getPlayerRole()) ? missileImage : hammerImage;
                    g.drawImage(otherMissileImage, missile.getX(), missile.getY(), this);
                }

                // 다른 플레이어의 HP 표시 (heart.png로 렌더링)
                String clientId = data.getClientId();
                int hp = otherPlayerHP.getOrDefault(clientId, 3);
                if ("Player1".equals(playerRole) && "Player2".equals(data.getPlayerRole())) {
                    // 오른쪽 상단에 표시
                    for (int i = 0; i < hp; i++) {
                        g.drawImage(heartImage, getWidth() - 30 - (i * 30), 10, 30, 30, this);
                    }
                } else if ("Player2".equals(playerRole) && "Player1".equals(data.getPlayerRole())) {
                    // 왼쪽 아래에 표시
                    for (int i = 0; i < hp; i++) {
                        g.drawImage(heartImage, 10 + (i * 30), getHeight() - 30, 30, 30, this);
                    }
                }
            }
        }

        // 자신의 HP 표시 (heart.png로 렌더링)
        if ("Player1".equals(playerRole)) {
            for (int i = 0; i < playerHP; i++) {
                g.drawImage(heartImage, 10 + (i * 30), getHeight() - 30, 30, 30, this);
            }
        } else {
            for (int i = 0; i < playerHP; i++) {
                g.drawImage(heartImage, getWidth() - 30 - (i * 30), 10, 30, 30, this);
            }
        }
    }


    public void actionPerformed(ActionEvent e) {
        // 이동 처리
        if (keys[KeyEvent.VK_LEFT]) {
            playerX -= 5;
            directionX = -1;
        } else if (keys[KeyEvent.VK_RIGHT]) {
            playerX += 5;
            directionX = 1;
        } else {
            directionX = 0;
        }

        if (keys[KeyEvent.VK_UP]) {
            playerY -= 5;
            directionY = -1;
        } else if (keys[KeyEvent.VK_DOWN]) {
            playerY += 5;
            directionY = 1;
        } else if (!keys[KeyEvent.VK_UP] && !keys[KeyEvent.VK_DOWN]) {
            directionY = 0;
        }

        // 화면 경계 내로 이동 제한
        playerX = Math.max(0, Math.min(playerX, getWidth() - playerImage.getWidth(null)));
        playerY = Math.max(0, Math.min(playerY, getHeight() - playerImage.getHeight(null)));

        // 미사일 업데이트
        missiles.forEach(Missile::update);
        missiles.removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));

        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                List<Missile> otherMissiles = data.getMissiles();
                otherMissiles.forEach(Missile::update);
                otherMissiles.removeIf(missile -> missile.isOutOfBounds(getWidth(), getHeight()));
            }
        }

        // 충돌 감지: 다른 플레이어의 미사일과 자신 간 충돌
        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                List<Missile> otherMissiles = data.getMissiles();
                for (Iterator<Missile> it = otherMissiles.iterator(); it.hasNext(); ) {
                    Missile missile = it.next();
                    if (missile.getX() >= playerX && missile.getX() <= playerX + playerImage.getWidth(null) &&
                            missile.getY() >= playerY && missile.getY() <= playerY + playerImage.getHeight(null)) {
                        playerHP--;
                        it.remove();

                        // 게임 종료 확인
                        if (playerHP <= 0) {
                            JOptionPane.showMessageDialog(this, "You lose!", "Game Over", JOptionPane.ERROR_MESSAGE);
                            System.exit(0);
                        }
                    }
                }
            }
        }

        // 충돌 감지: 자신의 미사일과 다른 플레이어 간 충돌
        synchronized (otherPlayers) {
            for (Iterator<Missile> it = missiles.iterator(); it.hasNext(); ) {
                Missile missile = it.next();
                for (GameData data : otherPlayers.values()) {
                    Rectangle otherPlayer = data.getPlayer();
                    if (otherPlayer != null && missile.getX() >= otherPlayer.x && missile.getX() <= otherPlayer.x + otherPlayer.width &&
                            missile.getY() >= otherPlayer.y && missile.getY() <= otherPlayer.y + otherPlayer.height) {

                        String clientId = data.getClientId();
                        otherPlayerHP.put(clientId, otherPlayerHP.getOrDefault(clientId, 3) - 1);
                        it.remove();

                        // 게임 종료 확인
                        if (otherPlayerHP.get(clientId) <= 0) {
                            JOptionPane.showMessageDialog(this, "You win!", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                            System.exit(0);
                        }
                    }
                }
            }
        }

        // 데이터 전송 및 화면 갱신
        sendPlayerData();
        repaint();
    }

    private void sendPlayerData() {
        try {
            GameData data = new GameData(null, new Rectangle(playerX, playerY, playerImage.getWidth(null), playerImage.getHeight(null)), new ArrayList<>(missiles), null, playerRole);
            out.writeObject(data);
            out.flush();
        } catch (IOException e) {
            System.err.println("데이터 전송 오류: " + e.getMessage());
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
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버와의 연결이 끊겼습니다.");
        }
    }

    public void keyPressed(KeyEvent e) {
        keys[e.getKeyCode()] = true;

        if (e.getKeyCode() == KeyEvent.VK_SPACE && !isSpacePressed) {
            // 미사일의 방향 설정
            int missileDX = directionX;
            int missileDY = directionY;

            // 기본 방향 설정: 아무 키도 누르지 않았을 경우
            if (missileDX == 0 && missileDY == 0) {
                missileDY = "Player2".equals(playerRole) ? 1 : -1; // Player2는 아래로, Player1은 위로 발사
            }

            missiles.add(new Missile(
                    playerX + playerImage.getWidth(null) / 2 - 2,
                    playerY + playerImage.getHeight(null) / 2 - 2,
                    missileDX,
                    missileDY
            ));
            isSpacePressed = true;
        }
    }

    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            isSpacePressed = false;
        }
    }

    public void keyTyped(KeyEvent e) {}

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