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
    private Image playerImage, backgroundImage, missileImage, hammerImage;
    private int playerX, playerY;
    private boolean[] keys;
    private List<Missile> missiles;

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

            // 플레이어 역할에 따라 이미지 설정
            if ("Player1".equals(playerRole)) {
                playerImage = new ImageIcon("images/kirby.png").getImage();
            } else {
                playerImage = new ImageIcon("images/dididi.png").getImage();
            }

            backgroundImage = new ImageIcon("images/background.png").getImage();
            missileImage = new ImageIcon("images/starBullet.png").getImage();
            hammerImage = new ImageIcon("images/hammer.png").getImage();


            playerX = 180;
            playerY = 600;
            keys = new boolean[256];
            missiles = new ArrayList<>();

            timer = new Timer(15, this);
            timer.start();

            addKeyListener(this);
            setFocusable(true);
            setPreferredSize(new Dimension(900, 900));

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
            // 현재 클라이언트의 역할에 따라 미사일 이미지 결정
            Image currentMissileImage = "Player1".equals(playerRole) ? missileImage : hammerImage;
            g.drawImage(currentMissileImage, missile.getX(), missile.getY(), this);
        }

        // 다른 플레이어의 미사일도 렌더링
        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                Rectangle player = data.getPlayer();
                // 다른 플레이어의 이미지
                Image otherPlayerImage = "Player1".equals(data.getPlayerRole())
                        ? new ImageIcon("images/kirby.png").getImage()
                        : new ImageIcon("images/dididi.png").getImage();
                g.drawImage(otherPlayerImage, player.x, player.y, this);

                // 다른 플레이어의 미사일 렌더링
                for (Missile missile : data.getMissiles()) {
                    Image missileImageToDraw = "Player1".equals(data.getPlayerRole()) ? missileImage : hammerImage;
                    g.drawImage(missileImageToDraw, missile.getX(), missile.getY(), this);
                }
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        // 자신의 키 입력에 따른 이동
        if (keys[KeyEvent.VK_LEFT]) playerX -= 5;
        if (keys[KeyEvent.VK_RIGHT]) playerX += 5;
        if (keys[KeyEvent.VK_UP]) playerY -= 5;
        if (keys[KeyEvent.VK_DOWN]) playerY += 5;

        // 화면 경계 내로 제한
        playerX = Math.max(0, Math.min(playerX, getWidth() - playerImage.getWidth(null)));
        playerY = Math.max(0, Math.min(playerY, getHeight() - playerImage.getHeight(null)));

        // 자신의 미사일 업데이트
        missiles.removeIf(missile -> missile.isOutOfBounds(getHeight()));
        missiles.forEach(Missile::update);

        // **다른 플레이어의 미사일 업데이트 추가**
        synchronized (otherPlayers) {
            for (GameData data : otherPlayers.values()) {
                List<Missile> otherMissiles = data.getMissiles();
                otherMissiles.removeIf(missile -> missile.isOutOfBounds(getHeight()));
                otherMissiles.forEach(Missile::update); // 이동 처리
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
                        System.out.println("수신된 데이터: " + serverData.getMissiles());
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버와의 연결이 끊겼습니다.");
        }
    }

    public void keyPressed(KeyEvent e) {
        keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            missiles.add(new Missile(playerX + playerImage.getWidth(null) / 2 - 2, playerY));
        }
    }

    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
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

