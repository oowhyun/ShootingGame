import java.awt.*;
import java.io.Serializable;

public class Missile implements Serializable {
    private int x, y;
    private int speed = 10;
    private int directionX, directionY;
    private int width = 15; // 미사일의 기본 너비
    private int height = 20; // 미사일의 기본 높이
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

    // 미사일 위치를 업데이트하는 메서드
    public void update() {
        x += directionX * speed; // directionX에 따라 수평으로 이동
        y += directionY * speed; // directionY에 따라 수직으로 이동
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
