package com.example.andonoperatorapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Splash screen with logo animation.
 * Redirects to Login, Config, or Home based on session state.
 */
public class SplashScreen extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2500; // milliseconds

    private ImageView logo;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide Action Bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_splash_screen);

        initViews();
        startAnimations();
        startSplashTimer();
    }

    private void initViews() {
        logo = findViewById(R.id.iv_logo);
        progressBar = findViewById(R.id.progressBar);

        // Initially hide progress bar
        progressBar.setVisibility(View.GONE);
    }

    private void startAnimations() {
        // Fade animation for splash image
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logo.startAnimation(fadeIn);
        logo.setAlpha(1f);

        // Show progress bar after small delay
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                progressBar.setVisibility(View.VISIBLE), 700);
    }

    private void startSplashTimer() {
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNext,
                SPLASH_DURATION);
    }

    /**
     * Determines the next screen based on session status:
     * - Not logged in → LoginActivity
     * - Logged in but no machine selected → ConfigActivity
     * - Logged in with a saved machine → HomeActivity
     */
    private void navigateToNext() {
        SessionManager sessionManager = new SessionManager(this);
        Intent intent;

        if (sessionManager.isLoggedIn()) {
            // Check if a machine was already selected
            if (sessionManager.getSelectedMachine() != null) {
                intent = new Intent(SplashScreen.this, HomeActivity.class);
            } else {
                intent = new Intent(SplashScreen.this, ConfigActivity.class);
            }
        } else {
            intent = new Intent(SplashScreen.this, LoginActivity.class);
        }

        // Clear the stack so user can't go back to splash
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}