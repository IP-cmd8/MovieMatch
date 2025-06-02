package com.example.muvifaind;

import java.util.HashMap;
import java.util.Map;

public class Room {
    private String roomId;
    private String creatorId;
    private String player2Id = "";
    private Map<String, Object> gameState = new HashMap<>();
    private long createdAt;

    public Room() {
        this.gameState.put("status", "waiting");
    }

    public Room(String roomId, String creatorId) {
        this.roomId = roomId;
        this.creatorId = creatorId;
        this.createdAt = System.currentTimeMillis();
        this.gameState = new HashMap<>();
        this.gameState.put("status", "waiting");
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public String getPlayer2Id() {
        return player2Id != null ? player2Id : "";
    }

    public void setPlayer2Id(String player2Id) {
        this.player2Id = player2Id != null ? player2Id : "";
    }

    public Map<String, Object> getGameState() {
        return gameState != null ? gameState : new HashMap<>();
    }

    public void setGameState(Map<String, Object> gameState) {
        this.gameState = gameState != null ? gameState : new HashMap<>();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Room{" +
                "roomId='" + roomId + '\'' +
                ", creatorId='" + creatorId + '\'' +
                ", player2Id='" + player2Id + '\'' +
                ", gameState=" + gameState +
                ", createdAt=" + createdAt +
                '}';
    }
}
