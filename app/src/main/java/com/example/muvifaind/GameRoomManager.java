package com.example.muvifaind;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class GameRoomManager {
    private final DatabaseReference databaseRef;

    public GameRoomManager() {
        databaseRef = FirebaseDatabase.getInstance().getReference();
    }

    public void joinRoom(String roomId, String playerId, GameRoomCallback callback) {
        databaseRef.child("rooms").child(roomId).child("player2Id").setValue(playerId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    public interface GameRoomCallback {
        void onSuccess();
        void onError(Exception e);
    }
}