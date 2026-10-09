package com.fastshare.transfer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide action bar if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 3 second (3000ms) ke baad aage badhega
        new Handler().postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("FastShareProfile", MODE_PRIVATE);
            boolean isFirstTime = prefs.getString("user_name", "").isEmpty();

            // Agar user pehli baar aaya hai to Onboarding pe bhejo, warna Main screen pe
            if (isFirstTime) {
                startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            finish();
        }, 3000L);
    }
}