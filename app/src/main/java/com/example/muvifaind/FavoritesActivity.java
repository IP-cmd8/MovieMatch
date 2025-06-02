package com.example.muvifaind;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesActivity extends AppCompatActivity {
    private RecyclerView rvFavorites;
    private TextView tvEmpty;
    private ImageButton btnBack, btnHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        initViews();
        setupClickListeners();
        loadFavorites();
    }

    private void initViews() {
        rvFavorites = findViewById(R.id.rvFavorites);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBackFavorites);
        btnHome = findViewById(R.id.btnHomeFavorites);

        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        rvFavorites.setAdapter(new FavoriteAdapter(new ArrayList<>()));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences("FavoritesPrefs", MODE_PRIVATE);
        Set<String> favorites = prefs.getStringSet("favorites", new HashSet<>());

        List<SinglePlayerActivity.FilmData> films = new ArrayList<>();
        for (String item : favorites) {
            String[] parts = item.split("\\|");
            if (parts.length >= 3) {
                films.add(new SinglePlayerActivity.FilmData(
                        parts[0],
                        parts[1],
                        parts[2]
                ));
            }
        }

        if (films.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvFavorites.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvFavorites.setVisibility(View.VISIBLE);
            ((FavoriteAdapter) rvFavorites.getAdapter()).updateData(films);
        }
    }

    class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

        private List<SinglePlayerActivity.FilmData> films;

        public FavoriteAdapter(List<SinglePlayerActivity.FilmData> films) {
            this.films = films;
        }

        public void updateData(List<SinglePlayerActivity.FilmData> newFilms) {
            films = newFilms;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SinglePlayerActivity.FilmData film = films.get(position);
            holder.tvTitle.setText(film.getTitle());
            holder.tvGenre.setText(film.getGenre());
            Picasso.get()
                    .load(film.getPosterUrl())
                    .into(holder.ivPoster);
        }

        @Override
        public int getItemCount() {
            return films.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPoster;
            TextView tvTitle, tvGenre;

            ViewHolder(View itemView) {
                super(itemView);
                ivPoster = itemView.findViewById(R.id.ivPoster);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvGenre = itemView.findViewById(R.id.tvGenre);
            }
        }
    }
}