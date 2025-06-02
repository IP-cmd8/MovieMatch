package com.example.muvifaind;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnlineGameActivity extends AppCompatActivity {
    private TextView filmTitleTextView;
    private TextView filmGenreTextView;
    private ImageView filmPosterImageView;
    private ProgressBar progressBar;
    private CardView movieCard;
    private CardView cardOutline;
    private TextView tvMatchFound;
    private Button btnLeaveGame;
    private ImageButton btnFilter;
    private TextView tvLoadingStatus;
    private CardView matchMovieCard; // Новая карточка для совпадения
    private ImageView matchFilmPosterImageView;
    private TextView matchFilmTitleTextView;
    private TextView matchFilmGenreTextView;

    private float xDown, yDown;
    private VelocityTracker velocityTracker;
    private int touchSlop;
    private boolean isDragging = false;
    private boolean isAnimationRunning = false;

    private FilmData currentFilm;
    private final List<FilmData> allFilms = new ArrayList<>();
    private List<FilmData> gameFilms = new ArrayList<>();
    private Queue<FilmData> filmsQueue = new LinkedList<>();
    private final Set<String> likedFilms = new HashSet<>();
    private final Set<String> opponentLikedFilms = new HashSet<>();
    private boolean isMatchFound = false;
    private Set<String> selectedGenres = new HashSet<>();
    private final Set<String> allGenres = new HashSet<>();
    private boolean isFilterApplied = false;

    private String roomId;
    private boolean isHost;
    private DatabaseReference gameRef;
    private ValueEventListener gameListener;
    private String playerId;
    private String opponentId = "";

    private static final String API_KEY = "VNEKRPQ-2TPMGKD-GJ67BDV-N9R9MAB";
    private static final String API_URL = "https://api.kinopoisk.dev/v1.3/movie?page=1&limit=500";
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    private static final float SWIPE_VELOCITY_THRESHOLD = 1000;
    private static final float MAX_ROTATION = 20f;
    private static final String TAG = "OnlineGameActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        roomId = getIntent().getStringExtra("ROOM_ID");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);

        playerId = isHost ? "player1" : "player2";
        opponentId = isHost ? "player2" : "player1";

        initViews();
        setupSwipeDetection();
        setupFirebaseListener();
        loadFilms();
    }

    private void initViews() {
        filmTitleTextView = findViewById(R.id.filmTitleTextView);
        filmGenreTextView = findViewById(R.id.filmGenreTextView);
        filmPosterImageView = findViewById(R.id.filmPosterImageView);
        progressBar = findViewById(R.id.progressBar);
        movieCard = findViewById(R.id.movieCard);
        cardOutline = findViewById(R.id.cardOutline);
        tvMatchFound = findViewById(R.id.tvMatchFound);
        btnLeaveGame = findViewById(R.id.btnLeaveGame);
        btnFilter = findViewById(R.id.btnFilter);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);

        matchMovieCard = findViewById(R.id.matchMovieCard);
        matchFilmPosterImageView = findViewById(R.id.matchFilmPosterImageView);
        matchFilmTitleTextView = findViewById(R.id.matchFilmTitleTextView);
        matchFilmGenreTextView = findViewById(R.id.matchFilmGenreTextView);

        tvMatchFound.setTextColor(getResources().getColor(android.R.color.white));
        tvMatchFound.setTextSize(32);
        tvMatchFound.setVisibility(View.GONE);

        matchMovieCard.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        tvLoadingStatus.setVisibility(View.GONE);

        btnLeaveGame.setOnClickListener(v -> {
            leaveGame();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        btnFilter.setOnClickListener(v -> {
            if (allGenres.isEmpty()) {
                Toast.makeText(this, "Жанры еще не загружены", Toast.LENGTH_SHORT).show();
            } else {
                showGenreSelectionDialog();
            }
        });
    }

    private void loadFilms() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Нет интернет-соединения", Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvLoadingStatus.setText("Загрузка фильмов...");
        tvLoadingStatus.setVisibility(View.VISIBLE);
        movieCard.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                List<FilmData> films = fetchFilmsFromAPI();
                runOnUiThread(() -> {
                    if (!films.isEmpty()) {
                        allFilms.clear();
                        allFilms.addAll(films);
                        collectAllGenres();
                        applyFilter();
                    } else {
                        Toast.makeText(this, "Фильмы не найдены", Toast.LENGTH_SHORT).show();
                    }
                    progressBar.setVisibility(View.GONE);
                    tvLoadingStatus.setVisibility(View.GONE);
                    movieCard.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    handleError(e);
                    progressBar.setVisibility(View.GONE);
                    tvLoadingStatus.setVisibility(View.GONE);
                    movieCard.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private List<FilmData> fetchFilmsFromAPI() throws IOException, JSONException {
        List<FilmData> films = new ArrayList<>();
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-API-KEY", API_KEY);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) {
                throw new IOException("HTTP error code: " + conn.getResponseCode());
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JSONObject json = new JSONObject(response.toString());
            JSONArray docs = json.getJSONArray("docs");

            for (int i = 0; i < docs.length(); i++) {
                JSONObject filmJson = docs.getJSONObject(i);
                String id = filmJson.getString("id");
                String title = filmJson.getString("name");
                String poster = filmJson.optJSONObject("poster").optString("url", "");

                if (!poster.isEmpty()) {
                    JSONArray genres = filmJson.optJSONArray("genres");
                    StringBuilder genreBuilder = new StringBuilder();
                    if (genres != null) {
                        for (int j = 0; j < genres.length(); j++) {
                            if (j > 0) genreBuilder.append(", ");
                            genreBuilder.append(genres.getJSONObject(j).getString("name"));
                        }
                    }
                    films.add(new FilmData(id, title, poster, genreBuilder.toString()));
                }
            }
        } finally {
            if (reader != null) reader.close();
            if (conn != null) conn.disconnect();
        }
        return films;
    }

    private void handleError(Exception e) {
        String errorMsg;
        if (e instanceof IOException) {
            errorMsg = "Ошибка сети: проверьте подключение";
        } else if (e instanceof JSONException) {
            errorMsg = "Ошибка формата данных";
        } else {
            errorMsg = "Ошибка: " + e.getMessage();
        }

        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        Log.e(TAG, errorMsg, e);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void collectAllGenres() {
        allGenres.clear();
        for (FilmData film : allFilms) {
            String[] genres = film.getGenre().split(",\\s*");
            for (String genre : genres) {
                if (!genre.trim().isEmpty()) {
                    allGenres.add(genre.trim());
                }
            }
        }
    }

    private void showGenreSelectionDialog() {
        if (allGenres.isEmpty()) {
            Toast.makeText(this, "Жанры не загружены", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] genresArray = allGenres.toArray(new String[0]);
        final boolean[] checkedItems = new boolean[genresArray.length];

        for (int i = 0; i < genresArray.length; i++) {
            checkedItems[i] = selectedGenres.contains(genresArray[i]);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
                .setTitle("Выберите жанры")
                .setMultiChoiceItems(genresArray, checkedItems,
                        (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Применить", (dialog, which) -> {
                    // Save selected genres
                    selectedGenres.clear();
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            selectedGenres.add(genresArray[i]);
                        }
                    }

                    sendFilterToFirebase();
                })
                .setNegativeButton("Сбросить", (dialog, which) -> {
                    selectedGenres.clear();
                    sendFilterToFirebase();
                })
                .setNeutralButton("Отмена", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void sendFilterToFirebase() {
        isFilterApplied = !selectedGenres.isEmpty();
        gameRef.child("filterApplied").setValue(isFilterApplied);
        gameRef.child("selectedGenres").setValue(new ArrayList<>(selectedGenres));
    }

    private void applyFilter() {
        if (allFilms.isEmpty()) {
            return;
        }

        resetGameState();

        if (isFilterApplied) {
            gameFilms.clear();
            for (FilmData film : allFilms) {
                if (matchesGenre(film)) {
                    gameFilms.add(film);
                }
            }
        } else {
            gameFilms = new ArrayList<>(allFilms);
        }

        if (gameFilms.isEmpty()) {
            Toast.makeText(this, "Нет фильмов по выбранным жанрам", Toast.LENGTH_SHORT).show();
            gameFilms = new ArrayList<>(allFilms);
        }

        Collections.shuffle(gameFilms, new Random(roomId.hashCode()));

        filmsQueue.clear();
        filmsQueue.addAll(gameFilms);

        showNextFilm();
    }

    private void resetGameState() {
        likedFilms.clear();
        opponentLikedFilms.clear();
        isMatchFound = false;
        filmsQueue.clear();
        currentFilm = null;

        tvMatchFound.setVisibility(View.GONE);
        matchMovieCard.setVisibility(View.GONE);
        movieCard.setVisibility(View.VISIBLE);
        cardOutline.setVisibility(View.VISIBLE);

        if (gameRef != null) {
            gameRef.child("actions").child(playerId).removeValue();
        }
    }

    private boolean matchesGenre(FilmData film) {
        if (selectedGenres.isEmpty()) return true;

        String[] filmGenres = film.getGenre().split(",\\s*");
        for (String genre : filmGenres) {
            if (selectedGenres.contains(genre.trim())) {
                return true;
            }
        }
        return false;
    }

    private void showNextFilm() {
        if (filmsQueue.isEmpty()) {
            checkForMatches();
            return;
        }

        currentFilm = filmsQueue.poll();
        updateUI();
    }

    private void updateUI() {
        if (currentFilm == null) return;

        filmTitleTextView.setText(currentFilm.getTitle());
        filmGenreTextView.setText(currentFilm.getGenre());

        progressBar.setVisibility(View.VISIBLE);
        Picasso.get()
                .load(currentFilm.getPosterUrl())
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_error)
                .into(filmPosterImageView, new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        progressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onError(Exception e) {
                        progressBar.setVisibility(View.GONE);
                        filmPosterImageView.setImageResource(R.drawable.ic_error);
                    }
                });
    }

    private void setupSwipeDetection() {
        ViewConfiguration vc = ViewConfiguration.get(this);
        touchSlop = vc.getScaledTouchSlop();

        movieCard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isAnimationRunning || isMatchFound) return true;

                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        xDown = event.getX();
                        yDown = event.getY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float xMove = event.getX();
                        float yMove = event.getY();

                        if (!isDragging) {
                            float dx = Math.abs(xMove - xDown);
                            float dy = Math.abs(yMove - yDown);
                            isDragging = dx > touchSlop || dy > touchSlop;
                        }

                        if (isDragging) {
                            float deltaX = xMove - xDown;
                            updateCardPosition(deltaX);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            handleSwipeRelease();
                        }
                        recycleVelocityTracker();
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void updateCardPosition(float deltaX) {
        if (movieCard == null || cardOutline == null) return;

        float screenWidth = getResources().getDisplayMetrics().widthPixels;
        float maxOffset = screenWidth * 0.4f;
        float clampedDelta = Math.max(-maxOffset, Math.min(deltaX, maxOffset));
        float progress = clampedDelta / maxOffset;

        movieCard.setTranslationX(clampedDelta);
        movieCard.setRotation(progress * MAX_ROTATION);
        movieCard.setAlpha(1 - Math.abs(progress) * 0.3f);

        cardOutline.setTranslationX(clampedDelta);
        cardOutline.setRotation(progress * MAX_ROTATION);
        cardOutline.setAlpha(1 - Math.abs(progress) * 0.2f);
    }

    private void handleSwipeRelease() {
        if (movieCard == null || cardOutline == null) return;

        velocityTracker.computeCurrentVelocity(1000);
        float velocityX = velocityTracker.getXVelocity();
        float deltaX = movieCard.getTranslationX();
        float screenWidth = getResources().getDisplayMetrics().widthPixels;

        boolean isFastEnough = Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD;
        boolean isFarEnough = Math.abs(deltaX) > screenWidth * 0.25f;

        if (isFastEnough || isFarEnough) {
            boolean isLike = deltaX > 0;
            handleSwipeAction(isLike);
            animateCardExit(isLike);
        } else {
            resetCardPosition();
        }
    }

    private void handleSwipeAction(boolean isLike) {
        if (currentFilm == null) return;

        String filmKey = currentFilm.toKey();
        if (isLike) {
            likedFilms.add(filmKey);
            if (opponentLikedFilms.contains(filmKey)) {
                showMatchFound(currentFilm);
            }
        }

        gameRef.child("actions").child(playerId).child(filmKey).setValue(isLike ? "like" : "dislike");
    }

    private void animateCardExit(boolean isLike) {
        if (isAnimationRunning) return;
        isAnimationRunning = true;

        int direction = isLike ? 1 : -1;
        int endX = (int) (direction * getResources().getDisplayMetrics().widthPixels * 1.5f);

        AnimatorSet exitAnim = new AnimatorSet();
        exitAnim.playTogether(
                ObjectAnimator.ofFloat(movieCard, "translationX", 0, endX),
                ObjectAnimator.ofFloat(movieCard, "rotation", 0, direction * MAX_ROTATION * 2),
                ObjectAnimator.ofFloat(movieCard, "alpha", 1, 0f),
                ObjectAnimator.ofFloat(cardOutline, "translationX", 0, endX),
                ObjectAnimator.ofFloat(cardOutline, "rotation", 0, direction * MAX_ROTATION * 2),
                ObjectAnimator.ofFloat(cardOutline, "alpha", 1, 0f)
        );

        exitAnim.setDuration(300);
        exitAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isMatchFound) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        showNextFilm();
                        resetCardPosition();
                        isAnimationRunning = false;
                    }, 300);
                } else {
                    isAnimationRunning = false;
                }
            }
        });
        exitAnim.start();
    }

    private void resetCardPosition() {
        if (movieCard == null || cardOutline == null) return;

        movieCard.setTranslationX(0);
        movieCard.setRotation(0);
        movieCard.setAlpha(1);

        cardOutline.setTranslationX(0);
        cardOutline.setRotation(0);
        cardOutline.setAlpha(1);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void setupFirebaseListener() {
        gameRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId).child("gameState");

        gameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                DataSnapshot opponentActions = snapshot.child("actions").child(opponentId);
                if (opponentActions.exists()) {
                    for (DataSnapshot filmSnapshot : opponentActions.getChildren()) {
                        String filmKey = filmSnapshot.getKey();
                        String action = filmSnapshot.getValue(String.class);
                        if ("like".equals(action)) {
                            opponentLikedFilms.add(filmKey);
                            if (likedFilms.contains(filmKey)) {
                                // Find film object
                                for (FilmData film : gameFilms) {
                                    if (film.toKey().equals(filmKey)) {
                                        showMatchFound(film);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (snapshot.child("filterApplied").exists()) {
                    boolean newFilterApplied = snapshot.child("filterApplied").getValue(Boolean.class);
                    Set<String> newSelectedGenres = new HashSet<>();

                    if (snapshot.child("selectedGenres").exists()) {
                        for (DataSnapshot genreSnapshot : snapshot.child("selectedGenres").getChildren()) {
                            String genre = genreSnapshot.getValue(String.class);
                            if (genre != null) {
                                newSelectedGenres.add(genre);
                            }
                        }
                    }

                    if (newFilterApplied != isFilterApplied || !newSelectedGenres.equals(selectedGenres)) {
                        isFilterApplied = newFilterApplied;
                        selectedGenres = newSelectedGenres;
                        applyFilter();
                    }
                }

                if ("ended".equals(snapshot.child("status").getValue(String.class))) {
                    leaveGame();
                    Toast.makeText(OnlineGameActivity.this,
                            "Соперник покинул игру", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
            }
        };

        gameRef.addValueEventListener(gameListener);
    }

    private void showMatchFound(FilmData film) {
        if (isMatchFound) return;
        isMatchFound = true;

        tvMatchFound.setVisibility(View.VISIBLE);
        matchMovieCard.setVisibility(View.VISIBLE);
        movieCard.setVisibility(View.GONE);
        cardOutline.setVisibility(View.GONE);

        tvMatchFound.setText("Совпадение!");

        matchFilmTitleTextView.setText(film.getTitle());
        matchFilmGenreTextView.setText(film.getGenre());
        Picasso.get()
                .load(film.getPosterUrl())
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_error)
                .into(matchFilmPosterImageView);

        animateMatchCelebration();

        gameRef.child("match").setValue(film.toKey());
    }

    private void animateMatchCelebration() {
        tvMatchFound.setScaleX(0.8f);
        tvMatchFound.setScaleY(0.8f);
        tvMatchFound.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(500)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> tvMatchFound.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start())
                .start();

        matchMovieCard.setScaleX(0.8f);
        matchMovieCard.setScaleY(0.8f);
        matchMovieCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .start();
    }

    private void checkForMatches() {
        if (isMatchFound) return;

        Set<String> commonFilms = new HashSet<>(likedFilms);
        commonFilms.retainAll(opponentLikedFilms);

        if (!commonFilms.isEmpty()) {
            // Find the first matching film
            String matchedKey = commonFilms.iterator().next();
            for (FilmData film : gameFilms) {
                if (film.toKey().equals(matchedKey)) {
                    showMatchFound(film);
                    break;
                }
            }
        } else {
            showNoMatchDialog();
        }
    }

    private void showNoMatchDialog() {
        new MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
                .setTitle("Совпадений не найдено")
                .setMessage("К сожалению, вы не нашли общих фильмов с соперником")
                .setPositiveButton("OK", (dialog, which) -> {
                    leaveGame();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void leaveGame() {
        if (gameRef != null && gameListener != null) {
            gameRef.removeEventListener(gameListener);
        }

        isFilterApplied = false;
        selectedGenres.clear();
        gameRef.child("filterApplied").setValue(false);
        gameRef.child("selectedGenres").removeValue();

        if (!isMatchFound) {
            gameRef.child("status").setValue("ended");
        }

        if (isHost) {
            FirebaseDatabase.getInstance().getReference("rooms").child(roomId).removeValue();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        leaveGame();
    }

    static class FilmData {
        private final String id;
        private final String title;
        private final String posterUrl;
        private final String genre;

        FilmData(String id, String title, String posterUrl, String genre) {
            this.id = id;
            this.title = title;
            this.posterUrl = posterUrl;
            this.genre = genre;
        }

        String getTitle() { return title; }
        String getPosterUrl() { return posterUrl; }
        String getGenre() { return genre; }

        String toKey() {
            return id;
        }
    }
}
