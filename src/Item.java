import java.awt.Rectangle;
import java.io.Serializable;

class Item implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final Rectangle bounds;
    private final long creationTime; // 생성 시간
    private final String type;

    public Item(String id, Rectangle bounds, String type) {
        this.id = id;
        this.bounds = bounds;
        this.creationTime = System.currentTimeMillis();
        this.type = type; // "hp" 또는 "speed"
    }

    public String getId() {
        return id;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public long getCreationTime() {
        return creationTime; // 생성된 시간 반환
    }

    public String getType() {
        return type;
    }
}
