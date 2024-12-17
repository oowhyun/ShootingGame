import java.awt.*;

public class HpItem extends Item {
    private boolean active;
    private long creationTime;
    private static final long LIFETIME = 10000; // 10초 후 비활성화

    public HpItem(String id, int x, int y) {
        super(id, new Rectangle(x, y, 30, 30), "hp"); // type="hp" 추가
        this.active = true;
        this.creationTime = System.currentTimeMillis();
    }

    public boolean isActive() {
        // 현재 시간과 생성 시간을 비교하여 10초가 지나면 비활성화
        if (System.currentTimeMillis() - creationTime > LIFETIME) {
            active = false;
        }
        return active;
    }

    public void deactivate() {
        active = false;
    }

    public void draw(Graphics g, Image itemImage) {
        if (active) {
            g.drawImage(itemImage, getBounds().x, getBounds().y, null);
        }
    }
}
