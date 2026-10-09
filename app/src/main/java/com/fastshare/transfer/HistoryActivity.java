package com.fastshare.transfer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private ListView historyListView;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> historyList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Transfer History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        historyListView = findViewById(R.id.historyListView);
        if (historyListView == null) { historyListView = new ListView(this); setContentView(historyListView); }

        historyList = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("FastShareHistory", MODE_PRIVATE);
        int count = prefs.getInt("count", 0);
        for (int i = count; i >= 1; i--) historyList.add(prefs.getString("item_" + i, ""));
        if (historyList.isEmpty()) historyList.add("No transfer history found.");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        historyListView.setAdapter(adapter);

        setupBannerAd();
    }

    private void setupBannerAd() {
        if (!UnityAds.isInitialized()) UnityAds.initialize(getApplicationContext(), "6073571", false, null);
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView.getChildCount() == 0) return;
        View originalRoot = contentView.getChildAt(0);
        if (originalRoot.getTag() != null && originalRoot.getTag().equals("banner_added")) return;

        contentView.removeView(originalRoot);
        LinearLayout wrapperLayout = new LinearLayout(this); wrapperLayout.setTag("banner_added"); wrapperLayout.setOrientation(LinearLayout.VERTICAL);
        wrapperLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        originalRoot.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)); wrapperLayout.addView(originalRoot);

        RelativeLayout bannerContainer = new RelativeLayout(this);
        int heightPx = (int) (50 * getResources().getDisplayMetrics().density);
        bannerContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        bannerContainer.setGravity(Gravity.CENTER); wrapperLayout.addView(bannerContainer); contentView.addView(wrapperLayout);

        BannerView bottomBanner = new BannerView(this, "Banner_Android", new UnityBannerSize(320, 50));
        bannerContainer.addView(bottomBanner);

        if (UnityAds.isInitialized()) { bottomBanner.load(); } 
        else {
            UnityAds.initialize(getApplicationContext(), "6073571", false, new IUnityAdsInitializationListener() {
                @Override public void onInitializationComplete() { runOnUiThread(() -> bottomBanner.load()); }
                @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String msg) {}
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static void saveHistory(Context context, String fileName, String type) {
        SharedPreferences prefs = context.getSharedPreferences("FastShareHistory", Context.MODE_PRIVATE);
        int count = prefs.getInt("count", 0) + 1;
        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        prefs.edit().putInt("count", count).putString("item_" + count, type + " • " + fileName + "\n" + date).apply();
    }
}