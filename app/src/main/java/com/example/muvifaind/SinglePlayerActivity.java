package com.example.muvifaind;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SinglePlayerActivity extends AppCompatActivity {

    // UI Components
    private TextView filmTitleTextView;
    private TextView filmGenreTextView;
    private ImageView filmPosterImageView;
    private ProgressBar progressBar;
    private CardView movieCard;
    private CardView cardOutline;
    private ImageButton btnFavorites;
    private TextView tvFilterIndicator;

    // Swipe Handling
    private float xDown, yDown;
    private VelocityTracker velocityTracker;
    private int touchSlop;
    private boolean isDragging = false;
    private boolean isAnimationRunning = false;

    // Data
    private FilmData currentFilm;
    private List<FilmData> loadedFilms = new ArrayList<>();
    private Set<String> shownFilms = new HashSet<>();
    private List<String> selectedGenres = new ArrayList<>();
    private Set<String> allGenres = new HashSet<>();

    // Threading
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile boolean isTaskRunning = false;

    // Constants
    private static final float SWIPE_VELOCITY_THRESHOLD = 1000;
    private static final float MAX_ROTATION = 20f;
    private static final String PREFS_NAME = "FavoritesPrefs";
    private static final String FILTER_PREFS = "FilterPrefs";
    private static final String SHOWN_FILMS_KEY = "shown_films";
    private static final String API_KEY = "VNEKRPQ-2TPMGKD-GJ67BDV-N9R9MAB";
    private static final String API_URL = "https://api.kinopoisk.dev/v1.3/movie?page=1&limit=500";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_player);

        ViewConfiguration vc = ViewConfiguration.get(this);
        touchSlop = vc.getScaledTouchSlop();
        initViews();
        setupClickListeners();
        setupSwipeDetection();
        setupGenreFilter();

        if (savedInstanceState != null) {
            shownFilms = new HashSet<>(savedInstanceState.getStringArrayList(SHOWN_FILMS_KEY));
        }

        SharedPreferences filterPrefs = getSharedPreferences(FILTER_PREFS, MODE_PRIVATE);
        selectedGenres = new ArrayList<>(filterPrefs.getStringSet("selectedGenres", new HashSet<>()));

        if (loadedFilms.isEmpty()) {
            loadInitialMovie();
        } else {
            showRandomFilm();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(SHOWN_FILMS_KEY, new ArrayList<>(shownFilms));
    }

    private void initViews() {
        filmTitleTextView = findViewById(R.id.filmTitleTextView);
        filmGenreTextView = findViewById(R.id.filmGenreTextView);
        filmPosterImageView = findViewById(R.id.filmPosterImageView);
        progressBar = findViewById(R.id.progressBar);
        movieCard = findViewById(R.id.movieCard);
        cardOutline = findViewById(R.id.cardOutline);
        btnFavorites = findViewById(R.id.btnFavorites);
        tvFilterIndicator = findViewById(R.id.tvFilterIndicator);

        if (movieCard == null) throw new RuntimeException("movieCard not found!");
        if (cardOutline == null) throw new RuntimeException("cardOutline not found!");

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        updateFilterIndicator();

        // Оптимизация аппаратного ускорения
        movieCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        cardOutline.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private void setupClickListeners() {
        btnFavorites.setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
    }

    private void setupGenreFilter() {
        ImageButton btnGenres = findViewById(R.id.btnGenres);
        btnGenres.setOnClickListener(v -> showGenreSelectionDialog());
    }

    private void showGenreSelectionDialog() {
        collectAllGenres();
        if (allGenres.isEmpty()) {
            Toast.makeText(this, "Жанры не загружены", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] genresArray = allGenres.toArray(new String[0]);
        final boolean[] checkedItems = new boolean[genresArray.length];

        SharedPreferences prefs = getSharedPreferences(FILTER_PREFS, MODE_PRIVATE);
        Set<String> savedGenres = prefs.getStringSet("selectedGenres", new HashSet<>());

        for (int i = 0; i < genresArray.length; i++) {
            checkedItems[i] = savedGenres.contains(genresArray[i]);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
                .setTitle("Выберите жанры")
                .setMultiChoiceItems(genresArray, checkedItems,
                        (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Применить", (dialog, which) ->
                        applyGenreFilter(genresArray, checkedItems))
                .setNegativeButton("Сбросить", (dialog, which) -> resetGenreFilter())
                .setNeutralButton("Отмена", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void applyGenreFilter(String[] genresArray, boolean[] checkedItems) {
        selectedGenres.clear();
        for (int i = 0; i < checkedItems.length; i++) {
            if (checkedItems[i]) {
                selectedGenres.add(genresArray[i]);
            }
        }

        getSharedPreferences(FILTER_PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet("selectedGenres", new HashSet<>(selectedGenres))
                .apply();

        updateFilterIndicator();
        resetAndReloadMovies();
    }

    private void resetGenreFilter() {
        selectedGenres.clear();
        getSharedPreferences(FILTER_PREFS, MODE_PRIVATE)
                .edit()
                .remove("selectedGenres")
                .apply();

        updateFilterIndicator();
        resetAndReloadMovies();
    }

    private void updateFilterIndicator() {
        tvFilterIndicator.setVisibility(!selectedGenres.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void resetAndReloadMovies() {
        shownFilms.clear();
        loadNewMovie();
    }

    private void collectAllGenres() {
        allGenres.clear();
        for (FilmData film : loadedFilms) {
            String[] genres = film.getGenre().split(",\\s*");
            for (String genre : genres) {
                if (!genre.trim().isEmpty()) {
                    allGenres.add(genre.trim());
                }
            }
        }
    }

    private void setupSwipeDetection() {
        movieCard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isAnimationRunning) return true;

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
        if (movieCard == null || cardOutline == null) {
            Log.e("SwipeError", "Card views are null!");
            return;
        }

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
            animateCardExit(deltaX > 0);
        } else {
            resetCardPosition();
        }
    }

    private void animateCardExit(boolean isRight) {
        if (currentFilm == null || isAnimationRunning) return;
        isAnimationRunning = true;

        int direction = isRight ? 1 : -1;
        int endX = (int) (direction * getResources().getDisplayMetrics().widthPixels * 1.5f);

        AnimatorSet exitAnim = new AnimatorSet();
        exitAnim.playTogether(
                createExitAnimator(movieCard, endX, direction),
                createExitAnimator(cardOutline, endX, direction)
        );

        exitAnim.setDuration(300);
        exitAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetCardPosition();
                if (isRight) saveToFavorites(currentFilm);
                loadNewMovie();
                isAnimationRunning = false;
            }
        });
        exitAnim.start();

        if (isRight) {
            Toast.makeText(this, "✓ Фильм сохранен", Toast.LENGTH_SHORT).show();
        }
    }

    private AnimatorSet createExitAnimator(View view, int endX, int direction) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, "translationX", endX),
                ObjectAnimator.ofFloat(view, "rotation", direction * MAX_ROTATION * 2),
                ObjectAnimator.ofFloat(view, "alpha", 0f)
        );
        return set;
    }

    private void resetCardPosition() {
        if (movieCard == null || cardOutline == null) return;

        movieCard.animate()
                .translationX(0)
                .rotation(0)
                .alpha(1)
                .setDuration(200)
                .start();

        cardOutline.animate()
                .translationX(0)
                .rotation(0)
                .alpha(1)
                .setDuration(200)
                .start();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void loadInitialMovie() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Нет интернет-соединения", Toast.LENGTH_LONG).show();
            return;
        }

        if (isTaskRunning) return;
        isTaskRunning = true;

        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<FilmData> films = fetchFilmsFromAPI();
                runOnUiThread(() -> {
                    if (!films.isEmpty()) {
                        loadedFilms = films;
                        collectAllGenres();
                        showRandomFilm();
                    } else {
                        Toast.makeText(this, "Фильмы не найдены", Toast.LENGTH_SHORT).show();
                    }
                    isTaskRunning = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    handleError(e);
                    isTaskRunning = false;
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

            films = new ArrayList<>(docs.length());

            for (int i = 0; i < docs.length(); i++) {
                JSONObject filmJson = docs.getJSONObject(i);
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
                    films.add(new FilmData(title, poster, genreBuilder.toString()));
                }
            }
        } finally {
            if (reader != null) reader.close();
            if (conn != null) conn.disconnect();
        }
        return films;
    }

    private void loadNewMovie() {
        if (loadedFilms.isEmpty()) {
            new FetchFilmsTask().execute();
        } else {
            showRandomFilm();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void showRandomFilm() {
        List<FilmData> availableFilms = new ArrayList<>();
        for (FilmData film : loadedFilms) {
            if (matchesGenre(film) && !shownFilms.contains(film.toKey())) {
                availableFilms.add(film);
            }
        }

        if (availableFilms.isEmpty()) {
            shownFilms.clear();
            availableFilms.addAll(loadedFilms);
        }

        if (availableFilms.isEmpty()) {
            Toast.makeText(this, "Фильмы не найдены", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentFilm = availableFilms.get(new Random().nextInt(availableFilms.size()));
        shownFilms.add(currentFilm.toKey());

        updateUI();
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

    private void updateUI() {
        filmTitleTextView.setText(currentFilm.getTitle());
        filmGenreTextView.setText(currentFilm.getGenre());
        Picasso.get()
                .load(currentFilm.getPosterUrl())
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_error)
                .noFade()
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

    private void saveToFavorites(FilmData film) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        favorites.add(film.toKey());
        prefs.edit().putStringSet("favorites", favorites).apply();
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
        Log.e("FetchFilmsError", errorMsg, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    static class FilmData {
        private final String title;
        private final String posterUrl;
        private final String genre;

        FilmData(String title, String posterUrl, String genre) {
            this.title = title != null ? title : "Неизвестный фильм";
            this.posterUrl = posterUrl != null ? posterUrl : "";
            this.genre = genre != null ? genre : "";
        }

        String getTitle() { return title; }
        String getPosterUrl() { return posterUrl; }
        String getGenre() { return genre; }

        String toKey() {
            return title + "|" + posterUrl + "|" + genre;
        }
    }

    // Оставлен для совместимости (можно удалить после полного перехода на ExecutorService)
    private class FetchFilmsTask extends AsyncTask<Void, Void, List<FilmData>> {
        private Exception exception;

        @Override
        protected List<FilmData> doInBackground(Void... voids) {
            try {
                return fetchFilmsFromAPI();
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<FilmData> films) {
            if (exception != null) {
                handleError(exception);
                return;
            }

            if (films != null && !films.isEmpty()) {
                loadedFilms = films;
                collectAllGenres();
                showRandomFilm();
            } else {
                Toast.makeText(SinglePlayerActivity.this,
                        "Фильмы не найдены", Toast.LENGTH_SHORT).show();
            }
        }
    }
}