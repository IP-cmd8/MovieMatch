package com.example.muvifaind;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSingle = findViewById(R.id.btnSinglePlayer);
        Button btnMulti = findViewById(R.id.btnMultiplayer);

        btnSingle.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SinglePlayerActivity.class);
            startActivity(intent);
        });

        btnMulti.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, OnlineModeActivity.class);
            startActivity(intent);
        });
    }
}