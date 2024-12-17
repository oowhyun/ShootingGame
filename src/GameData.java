import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GameData implements Serializable {
    private String clientId;
    private Rectangle player;  // 플레이어의 위치와 크기
    private List<Missile> missiles;
    private List<String> actions;
    private String roomId;
    private String playerRole;
    private int hp;
    private boolean gameOver; // 게임 종료 여부
    private boolean winner;   // 승리 여부
    private List<Item> items; // 아이템 정보 추가
    private Item newItem;
    private String itemRemoved; // 제거된 아이템 ID
    private List<SpeedItem> speedItems;


    // 기본 생성자
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

    // HP를 감소시키는 메서드
    public void decreaseHp(int amount) {
        this.hp -= amount;
        if (this.hp < 0) {
            this.hp = 0;  // HP는 0 이하로 내려가지 않도록 보장
        }
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

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Item> getItems() {
        return items;
    }
    public void setNewItem(Item newItem) {
        this.newItem = newItem;
    }

    public void setItemRemoved(String itemId) {
        this.itemRemoved = itemId;
    }

    public String getItemRemoved() {
        return itemRemoved;
    }
    public List<SpeedItem> getSpeedItems() {
        return speedItems;
    }

}
