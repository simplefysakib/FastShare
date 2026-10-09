package com.fastshare.transfer;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQ_CODE = 100;
    private int[] avatarIcons;
    private SharedPreferences prefs;
    private int selectedAvatarIndex;

    private String unityGameID = "6073571";
    private Boolean testMode = false; 
    private String interstitialAdUnitId = "Interstitial_Android";
    private String bannerAdUnitId = "Banner_Android";

    public MainActivity() {
        this.avatarIcons = new int[]{ R.drawable.avatar_boy, R.drawable.avatar_girl, R.drawable.avatar_robot, R.drawable.avatar_cyber, R.drawable.avatar_space };
        this.selectedAvatarIndex = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("FastShareProfile", MODE_PRIVATE);

        UnityAds.initialize(getApplicationContext(), unityGameID, testMode, new IUnityAdsInitializationListener() {
            @Override public void onInitializationComplete() { loadUnityAd(); }
            @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {}
        });
        
        loadProfileData(); checkAndRequestPermissions(); updateStorageUI();

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            if (checkNetworkStatus()) {
                if (UnityAds.isInitialized()) {
                    UnityAds.show(MainActivity.this, interstitialAdUnitId, new IUnityAdsShowListener() {
                        @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) { openFilePicker(); }
                        @Override public void onUnityAdsShowStart(String placementId) {}
                        @Override public void onUnityAdsShowClick(String placementId) {}
                        @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) { loadUnityAd(); openFilePicker(); }
                    });
                } else openFilePicker();
            }
        });

        findViewById(R.id.btnReceive).setOnClickListener(v -> {
            if (checkNetworkStatus()) {
                if (UnityAds.isInitialized()) {
                    UnityAds.show(MainActivity.this, interstitialAdUnitId, new IUnityAdsShowListener() {
                        @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) { openDeviceDiscovery(); }
                        @Override public void onUnityAdsShowStart(String placementId) {}
                        @Override public void onUnityAdsShowClick(String placementId) {}
                        @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) { loadUnityAd(); openDeviceDiscovery(); }
                    });
                } else openDeviceDiscovery();
            }
        });

        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        findViewById(R.id.cardStorage).setOnClickListener(v -> openStorageManager());
        findViewById(R.id.layoutProfile).setOnClickListener(v -> showProfileEditDialog());

        checkProfileSetup();
        setupBannerAd();
    }

    private void setupBannerAd() {
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView.getChildCount() == 0) return;
        View originalRoot = contentView.getChildAt(0);
        if (originalRoot.getTag() != null && originalRoot.getTag().equals("banner_added")) return;

        contentView.removeView(originalRoot);
        LinearLayout wrapperLayout = new LinearLayout(this);
        wrapperLayout.setTag("banner_added"); wrapperLayout.setOrientation(LinearLayout.VERTICAL);
        wrapperLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        originalRoot.setLayoutParams(contentParams); wrapperLayout.addView(originalRoot);

        RelativeLayout bannerContainer = new RelativeLayout(this);
        // FIX: Reserve exactly 50dp height so the banner space never collapses
        int heightPx = (int) (50 * getResources().getDisplayMetrics().density);
        bannerContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        bannerContainer.setGravity(Gravity.CENTER); wrapperLayout.addView(bannerContainer); contentView.addView(wrapperLayout);

        BannerView bottomBanner = new BannerView(this, bannerAdUnitId, new UnityBannerSize(320, 50));
        bannerContainer.addView(bottomBanner);

        if (UnityAds.isInitialized()) { bottomBanner.load(); } 
        else {
            UnityAds.initialize(getApplicationContext(), unityGameID, testMode, new IUnityAdsInitializationListener() {
                @Override public void onInitializationComplete() { runOnUiThread(() -> bottomBanner.load()); }
                @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {}
            });
        }
    }

    private void checkAndRequestPermissions() {
        List<String> reqPerms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) { reqPerms.add(Manifest.permission.BLUETOOTH_SCAN); reqPerms.add(Manifest.permission.BLUETOOTH_ADVERTISE); reqPerms.add(Manifest.permission.BLUETOOTH_CONNECT); }
        if (Build.VERSION.SDK_INT >= 33) { reqPerms.add(Manifest.permission.NEARBY_WIFI_DEVICES); reqPerms.add(Manifest.permission.READ_MEDIA_IMAGES); reqPerms.add(Manifest.permission.READ_MEDIA_VIDEO); } 
        else { reqPerms.add(Manifest.permission.READ_EXTERNAL_STORAGE); reqPerms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE); }
        reqPerms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        List<String> missingPerms = new ArrayList<>();
        for (String p : reqPerms) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missingPerms.add(p);
        if (!missingPerms.isEmpty()) ActivityCompat.requestPermissions(this, missingPerms.toArray(new String[0]), PERMISSION_REQ_CODE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this).setTitle("Storage Permission Required").setMessage("FastShare needs 'All Files Access' to read apps and save received files correctly. Please allow this permission.")
                .setPositiveButton("Allow", (dialog, which) -> { try { Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION); intent.setData(Uri.parse("package:" + getPackageName())); startActivity(intent); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); } }).setNegativeButton("Later", null).setCancelable(false).show();
        }
    }

    private boolean checkNetworkStatus() {
        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter(); LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE); WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (ba != null && !ba.isEnabled()) { showTurnOnDialog("Bluetooth Required", "Please turn on Bluetooth.", "android.settings.BLUETOOTH_SETTINGS"); return false; }
        if (lm != null && !lm.isLocationEnabled()) { showTurnOnDialog("Location Required", "Android requires Location services.", "android.settings.LOCATION_SOURCE_SETTINGS"); return false; }
        if (wm != null && !wm.isWifiEnabled()) { showTurnOnDialog("Wi-Fi Required", "Please turn on Wi-Fi.", "android.settings.WIFI_SETTINGS"); return false; }
        return true;
    }

    private void showTurnOnDialog(String title, String msg, String action) { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Turn On", (dialog, which) -> { try { startActivity(new Intent(action)); } catch (Exception e) {} }).setNegativeButton("Cancel", null).show(); }
    private void checkProfileSetup() { if (prefs.getString("user_name", "").isEmpty()) showProfileEditDialog(); }
    private void loadProfileData() {
        selectedAvatarIndex = prefs.getInt("user_avatar_index", 0); if (selectedAvatarIndex < 0 || selectedAvatarIndex >= avatarIcons.length) selectedAvatarIndex = 0;
        ((TextView) findViewById(R.id.txtProfileName)).setText(prefs.getString("user_name", Build.MODEL)); ((ImageView) findViewById(R.id.imgProfileAvatar)).setImageResource(avatarIcons[selectedAvatarIndex]);
    }

    private void showProfileEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this); builder.setTitle("Edit Profile");
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 20);
        EditText inputName = new EditText(this); inputName.setHint("Enter your name"); inputName.setText(prefs.getString("user_name", Build.MODEL)); layout.addView(inputName);
        TextView avatarHint = new TextView(this); avatarHint.setText("Choose Avatar (Swipe Right ->)"); avatarHint.setPadding(0, 30, 0, 10); layout.addView(avatarHint);
        HorizontalScrollView scrollView = new HorizontalScrollView(this); scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout avatarContainer = new LinearLayout(this); avatarContainer.setOrientation(LinearLayout.HORIZONTAL);
        final int[] tempIdx = {selectedAvatarIndex};
        for (int i = 0; i < avatarIcons.length; i++) {
            ImageView img = new ImageView(this); img.setImageResource(avatarIcons[i]); img.setPadding(15, 15, 15, 15); img.setBackgroundColor(i == tempIdx[0] ? 0xFFE8EAF6 : 0x00000000);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(180, 180); p.setMargins(10, 10, 10, 10); img.setLayoutParams(p);
            final int index = i; img.setOnClickListener(v -> { tempIdx[0] = index; for (int j = 0; j < avatarContainer.getChildCount(); j++) avatarContainer.getChildAt(j).setBackgroundColor(0x00000000); v.setBackgroundColor(0xFFE8EAF6); }); avatarContainer.addView(img);
        }
        scrollView.addView(avatarContainer); layout.addView(scrollView); builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> { prefs.edit().putString("user_name", inputName.getText().toString()).putInt("user_avatar_index", tempIdx[0]).apply(); loadProfileData(); }); builder.setNegativeButton("Cancel", null).show();
    }

    private void updateStorageUI() {
        try { StatFs statFs = new StatFs(Environment.getDataDirectory().getPath()); long totalGB = statFs.getTotalBytes() / 1073741824L;
            long displayGB = totalGB <= 16L ? 16L : totalGB <= 32L ? 32L : totalGB <= 64L ? 64L : totalGB <= 128L ? 128L : totalGB <= 256L ? 256L : totalGB <= 512L ? 512L : 1024L;
            long usedGB = Math.max(0, displayGB - (statFs.getAvailableBytes() / 1073741824L));
            ((TextView) findViewById(R.id.txtStorageDetail)).setText("Used " + usedGB + "GB / Total " + displayGB + "GB"); ((ProgressBar) findViewById(R.id.storageProgress)).setProgress((int) (100L * usedGB / displayGB)); } catch (Exception e) {}
    }
    private void openStorageManager() { try { Intent i = new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"); if (i.resolveActivity(getPackageManager()) != null) startActivity(i); else startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); } catch (Exception e) {} }
    @Override public void onBackPressed() { new AlertDialog.Builder(this).setTitle("Exit FastShare?").setMessage("Are you sure you want to exit?").setPositiveButton("Yes", (dialog, which) -> finish()).setNegativeButton("No", null).show(); }
    private void openFilePicker() { startActivity(new Intent(MainActivity.this, FilePickerActivity.class)); }
    private void openDeviceDiscovery() { Intent i = new Intent(MainActivity.this, DeviceDiscoveryActivity.class); i.putExtra("isReceiver", true); startActivity(i); }
    private void loadUnityAd() { UnityAds.load(interstitialAdUnitId, new IUnityAdsLoadListener() { @Override public void onUnityAdsAdLoaded(String placementId) {} @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {} }); }
}