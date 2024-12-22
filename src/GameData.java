import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GameData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String clientId;
    private Rectangle player;  // 플레이어의 위치와 크기
    private List<Missile> missiles;
    private List<String> actions;
    private String roomId;
    private String playerRole;
    private int hp;
    private boolean gameOver; // 게임 종료 여부
    private boolean winner;   // 승리 여부
    private boolean gameStarted;

    // 아이템 관리 통합
    private List<Item> items; // 아이템 리스트
    private String itemRemoved; // 제거된 아이템 ID

    // 생성자
    public GameData(String clientId, Rectangle player, List<Missile> missiles, List<Item> items, String roomId, String playerRole, int hp) {
        this.clientId = clientId;
        this.player = player != null ? player : new Rectangle(0, 0, 50, 50);
        this.missiles = missiles != null ? missiles : new ArrayList<>();
        this.items = items != null ? items : new ArrayList<>();
        this.actions = new ArrayList<>(); // 기본값으로 빈 리스트
        this.roomId = roomId;
        this.playerRole = playerRole;
        this.hp = hp;
    }


    // Getter 및 Setter 메서드들
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Rectangle getPlayer() {
        return player;
    }

    public void setPlayer(Rectangle player) {
        this.player = player;
    }

    public List<Missile> getMissiles() {
        return missiles;
    }

    public void setMissiles(List<Missile> missiles) {
        this.missiles = missiles;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPlayerRole() {
        return playerRole;
    }

    public void setPlayerRole(String playerRole) {
        this.playerRole = playerRole;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }


    // 아이템 관련 메서드
    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public void addItem(Item item) {
        boolean isDuplicate = false;

        // 아이템이 이미 리스트에 있는지 확인
        for (Item existingItem : items) {
            if (existingItem.getId().equals(item.getId())) {
                isDuplicate = true;
                break;
            }
        }

        if (!isDuplicate) {
            items.add(item);
            System.out.println("아이템 추가: " + item.getId());
        } else {
            System.out.println("중복 아이템 발견: " + item.getId());
        }
    }



    // 아이템 리스트 업데이트 (전체 교체)
    public void updateItems(List<Item> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
    }

    public boolean removeItemById(String itemId) {
        boolean removed = items.removeIf(item -> item.getId().equals(itemId));
        if (removed) {
            this.itemRemoved = itemId; // 마지막으로 제거된 아이템 ID 기록
        }
        return removed;
    }


    public String getItemRemoved() {
        return itemRemoved;
    }

    public void setItemRemoved(String itemId) {
        this.itemRemoved = itemId;
    }

    // 내부 클래스 또는 독립적으로 정의 가능
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id; // 고유 ID
        private int x;
        private int y;
        private String type; // "Speed" 또는 "HP"
        private Rectangle bounds;
        private long creationTime;
        private boolean processed = false;

        public Item(String id, Rectangle bounds, String type) {
            this.id = id;
            this.x = bounds.x;
            this.y = bounds.y;
            this.type = type;
            this.bounds = bounds;
            this.creationTime = System.currentTimeMillis();
        }

        public boolean isProcessed() {
            return processed;
        }

        public void setProcessed(boolean processed) {
            this.processed = processed;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Rectangle getBounds() {
            return bounds;
        }

        public long getCreationTime() {
            return creationTime;
        }

        @Override
        public String toString() { //아이템 전달 확인용 코드
            return "Item{" +
                    "id='" + id + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", type='" + type + '\'' +
                    ", bounds=" + bounds +
                    ", creationTime=" + creationTime +
                    '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Item item = (Item) obj;
            return id != null && id.equals(item.id); // id로 비교
        }

        @Override
        public int hashCode() {
            return Objects.hash(id); // id를 기준으로 해시 코드 생성
        }

    }
}
