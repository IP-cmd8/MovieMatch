package com.example.muvifaind;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LobbyActivity extends AppCompatActivity {

    private static final String TAG = "LobbyActivity";

    private String roomId;
    private boolean isHost;
    private DatabaseReference roomRef;
    private ValueEventListener roomListener;
    private boolean isGameStarting = false;
    private String userId;

    private TextView tvRoomCode;
    private TextView tvStatus;
    private Button btnStartGame;
    private Button btnCopyCode;
    private Button btnLeave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        // Получаем данные из Intent
        roomId = getIntent().getStringExtra("ROOM_ID");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);

        // Получаем ID пользователя
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            userId = "guest_" + System.currentTimeMillis();
        }

        // Инициализация UI
        tvRoomCode = findViewById(R.id.tvRoomCode);
        tvStatus = findViewById(R.id.tvStatus);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnCopyCode = findViewById(R.id.btnCopyCode);
        btnLeave = findViewById(R.id.btnLeave);

        tvRoomCode.setText("Код комнаты: " + roomId);
        btnStartGame.setVisibility(isHost ? View.VISIBLE : View.GONE);
        btnStartGame.setEnabled(false);

        setupRoomListener();
        setupButtons();

        // Если гость - присоединяемся к комнате
        if (!isHost) {
            GameRoomManager gameRoomManager = new GameRoomManager();
            gameRoomManager.joinRoom(roomId, userId, new GameRoomManager.GameRoomCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Успешно присоединился к комнате");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Ошибка при присоединении к комнате", e);
                    Toast.makeText(LobbyActivity.this, "Ошибка подключения к комнате", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Log.d(TAG, "Lobby created: roomId=" + roomId + ", isHost=" + isHost + ", userId=" + userId);
    }

    private void setupRoomListener() {
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        // Таймаут 30 секунд на подключение
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isGameStarting && isHost) {
                Toast.makeText(this, "Не удалось подключить второго игрока", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, 30000);

        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    showErrorAndFinish("Комната была удалена");
                    return;
                }

                Log.d(TAG, "Room data changed: " + snapshot.toString());

                Room room = snapshot.getValue(Room.class);
                if (room != null) {
                    updateRoomState(room);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showErrorAndFinish("Ошибка: " + error.getMessage());
            }
        };

        roomRef.addValueEventListener(roomListener);
    }

    private void updateRoomState(Room room) {
        // Проверяем подключение второго игрока
        boolean hasSecondPlayer = room.getPlayer2Id() != null &&
                !room.getPlayer2Id().isEmpty() &&
                !room.getPlayer2Id().equals(room.getCreatorId());

        Log.d(TAG, "Room state: creator=" + room.getCreatorId() +
                ", player2=" + room.getPlayer2Id() +
                ", hasSecondPlayer=" + hasSecondPlayer);

        if (hasSecondPlayer) {
            tvStatus.setText("Игрок подключен!");
            if (isHost) {
                btnStartGame.setEnabled(true);

                // Автоматический старт через 2 секунды
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isGameStarting) {
                        startGame();
                    }
                }, 2000);
            }
        } else {
            tvStatus.setText("Ожидание игрока...");
            btnStartGame.setEnabled(false);
        }

        // Проверка старта игры
        if (room.getGameState() != null &&
                "started".equals(room.getGameState().get("status"))) {
            startOnlineGame();
        }
    }

    /*private void joinRoom() {
        Log.d(TAG, "Joining room as guest: " + roomId);

        roomRef.child("player2Id").setValue(userId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Successfully joined room");
                    } else {
                        Log.e(TAG, "Error joining room", task.getException());
                        Toast.makeText(LobbyActivity.this,
                                "Ошибка подключения к комнате", Toast.LENGTH_SHORT).show();
                    }
                });
    }*/

    private void setupButtons() {
        btnCopyCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Код комнаты", roomId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Код скопирован", Toast.LENGTH_SHORT).show();
        });

        btnStartGame.setOnClickListener(v -> startGame());

        btnLeave.setOnClickListener(v -> finish());
    }

    private void startGame() {
        if (isGameStarting) return;
        isGameStarting = true;

        Log.d(TAG, "Starting game in room: " + roomId);

        roomRef.child("gameState").child("status").setValue("started")
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(LobbyActivity.this,
                                "Ошибка запуска", Toast.LENGTH_SHORT).show();
                        isGameStarting = false;
                    }
                });
    }

    private void startOnlineGame() {
        Log.d(TAG, "Moving to online game");
        isGameStarting = true;
        Intent intent = new Intent(this, OnlineGameActivity.class);
        intent.putExtra("ROOM_ID", roomId);
        intent.putExtra("IS_HOST", isHost);
        startActivity(intent);
        finish();
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying LobbyActivity");

        if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }

        // Удаляем комнату только если хост и игра не началась
        if (isHost && !isGameStarting) {
            Log.d(TAG, "Deleting room: " + roomId);
            FirebaseDatabase.getInstance().getReference("rooms").child(roomId).removeValue();
        }
    }
}