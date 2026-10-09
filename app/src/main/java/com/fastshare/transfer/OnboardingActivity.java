package com.fastshare.transfer;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_onboarding);

        findViewById(R.id.btnGetStarted).setOnClickListener(v -> {
            startActivity(new Intent(OnboardingActivity.this, MainActivity.class));
            finish();
        });
    }
}