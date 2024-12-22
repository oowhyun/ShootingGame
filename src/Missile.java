import java.awt.*;
import java.io.Serializable;

public class Missile implements Serializable {
    private int x, y;
    private int speed = 10;
    private int directionX, directionY;
    private int width = 15;
    private int height = 20;
    private boolean processed = false;

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Missile(int x, int y, int directionX, int directionY) {
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
    }

    // 플레이어가 바라보는 방향으로 미사일 발사
    public void update() {
        x += directionX * speed;
        y += directionY * speed;
    }

    // 현재 x 좌표 반환
    public int getX() {
        return x;
    }

    // 현재 y 좌표 반환
    public int getY() {
        return y;
    }

    // 화면 경계를 벗어났는지 확인하는 메서드
    public boolean isOutOfBounds(int width, int height) {
        return x < 0 || x > width || y < 0 || y > height;
    }

    // 미사일의 경계(Rectangle) 반환
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
}
