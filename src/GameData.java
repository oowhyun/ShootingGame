import java.awt.*;
import java.io.Serializable;
import java.util.List;

class GameData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String clientId;
    private Rectangle player;
    private List<Missile> missiles;
    private String roomId;
    private String playerRole;

    public GameData(String clientId, Rectangle player, List<Missile> missiles, String roomId, String playerRole) {
        this.clientId = clientId;
        this.player = player;
        this.missiles = missiles;
        this.roomId = roomId;
        this.playerRole = playerRole;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Rectangle getPlayer() {
        return player;
    }

    public List<Missile> getMissiles() {
        return missiles;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPlayerRole() {
        return playerRole;
    }
}
