import java.io.Serializable;

public class Missile implements Serializable {
    private int x, y;
    private int speed = 10;
    private int directionX, directionY;

    public Missile(int x, int y, int directionX, int directionY) {
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
    }

    public void update() {
        x += directionX * speed; // directionX에 따라 수평으로 이동
        y += directionY * speed; // directionY에 따라 수직으로 이동
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isOutOfBounds(int width, int height) {
        return x < 0 || x > width || y < 0 || y > height;
    }
}