package com.example.muvifaind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Random;

public class OnlineModeActivity extends AppCompatActivity {

    private EditText etRoomCode;
    private ProgressBar progressBar;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_mode);

        etRoomCode = findViewById(R.id.etRoomCode);
        progressBar = findViewById(R.id.progressBar);
        Button btnJoin = findViewById(R.id.btnJoinRoom);
        Button btnCreate = findViewById(R.id.btnCreateRoom);

        // Получаем ID пользователя
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            userId = "guest_" + System.currentTimeMillis();
        }

        btnJoin.setOnClickListener(v -> joinRoom());
        btnCreate.setOnClickListener(v -> createRoom());
    }

    private void joinRoom() {
        String roomCode = etRoomCode.getText().toString().trim().toUpperCase();
        if (roomCode.isEmpty()) {
            Toast.makeText(this, "Введите код комнаты", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Переходим сразу в лобби как гость
        goToLobby(roomCode, false);
    }

    private void createRoom() {
        progressBar.setVisibility(View.VISIBLE);
        String roomCode = generateRoomCode(4);

        // Создаем комнату
        Room newRoom = new Room(roomCode, userId);

        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        roomRef.setValue(newRoom)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        // Переходим в лобби как хост
                        goToLobby(roomCode, true);
                    } else {
                        Toast.makeText(this,
                                "Ошибка создания: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToLobby(String roomCode, boolean isHost) {
        Intent intent = new Intent(this, LobbyActivity.class);
        intent.putExtra("ROOM_ID", roomCode);
        intent.putExtra("IS_HOST", isHost);
        startActivity(intent);
        finish();
    }

    private String generateRoomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}