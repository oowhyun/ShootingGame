import java.io.Serializable;

public class Missile implements Serializable {
    private int x, y;
    private int speed = 10;

    public Missile(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        y -= speed;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isOutOfBounds(int height) {
        return y < 0;
    }
}