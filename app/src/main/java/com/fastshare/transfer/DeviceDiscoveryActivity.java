package com.fastshare.transfer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DeviceDiscoveryActivity extends AppCompatActivity {
    private static final String SERVICE_ID = "com.fastshare.transfer.SERVICE";
    private int[] avatarIcons; private Button btnScanQr; private Map<String, String> discoveredEndpoints; private Map<String, View> radarViews;
    private ArrayList<Uri> filesToSend; private boolean isConnecting, isReceiver; private LinearLayout layoutReceiverQR; private RelativeLayout layoutSenderRadar;
    private String myEndpointName; private ImageView myRadarAvatar, receiverMyAvatar, qrCodeImage; private TextView receiverMyName;
    private ArrayList<String> providedFileNames, providedFileSizes; private AnimatorSet radarAnimatorSet; private View ripple1, ripple2; private String targetEndpointName;

    public DeviceDiscoveryActivity() {
        targetEndpointName = null; isConnecting = false; discoveredEndpoints = new HashMap<>(); radarViews = new HashMap<>();
        avatarIcons = new int[]{R.drawable.avatar_boy, R.drawable.avatar_girl, R.drawable.avatar_robot, R.drawable.avatar_cyber, R.drawable.avatar_space};
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) { discoveredEndpoints.put(endpointId, info.getEndpointName()); runOnUiThread(() -> addDeviceToRadar(endpointId, info.getEndpointName())); if (targetEndpointName != null && info.getEndpointName().equals(targetEndpointName) && !isConnecting) connectToDevice(endpointId, info.getEndpointName()); }
        @Override public void onEndpointLost(String endpointId) { discoveredEndpoints.remove(endpointId); runOnUiThread(() -> removeDeviceFromRadar(endpointId)); }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
            if (isReceiver) {
                new AlertDialog.Builder(DeviceDiscoveryActivity.this).setTitle("Incoming Connection").setMessage(getDisplayName(connectionInfo.getEndpointName()) + " wants to connect and send files. Accept?").setPositiveButton("Accept", (dialog, which) -> { Nearby.getConnectionsClient(DeviceDiscoveryActivity.this).acceptConnection(endpointId, TransferManager.payloadCallback); }).setNegativeButton("Decline", (dialog, which) -> { Nearby.getConnectionsClient(DeviceDiscoveryActivity.this).rejectConnection(endpointId); }).setCancelable(false).show();
            } else { ((TextView) findViewById(R.id.txtSearching)).setText("Waiting for Receiver to Accept..."); Nearby.getConnectionsClient(DeviceDiscoveryActivity.this).acceptConnection(endpointId, TransferManager.payloadCallback); }
        }
        @Override public void onConnectionResult(String endpointId, ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                Nearby.getConnectionsClient(DeviceDiscoveryActivity.this).stopDiscovery(); Nearby.getConnectionsClient(DeviceDiscoveryActivity.this).stopAdvertising(); stopRadarAnimation(); TransferManager.connectedEndpointId = endpointId;
                Intent intent = new Intent(DeviceDiscoveryActivity.this, TransferActivity.class); intent.putExtra("isServer", isReceiver);
                if (filesToSend != null) intent.putParcelableArrayListExtra("files", filesToSend); if (providedFileNames != null) intent.putStringArrayListExtra("fileNames", providedFileNames); if (providedFileSizes != null) intent.putStringArrayListExtra("fileSizes", providedFileSizes);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(intent); finish();
            } else { isConnecting = false; if (!isReceiver) ((TextView) findViewById(R.id.txtSearching)).setText("Connection Declined by Receiver."); else ((TextView) findViewById(R.id.statusText)).setText("Connection Cancelled."); }
        }
        @Override public void onDisconnected(String endpointId) { isConnecting = false; Toast.makeText(DeviceDiscoveryActivity.this, "Device Disconnected", Toast.LENGTH_SHORT).show(); if (!isReceiver) ((TextView) findViewById(R.id.txtSearching)).setText("Searching for receivers..."); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_discovery);

        layoutReceiverQR = findViewById(R.id.layoutReceiverQR); layoutSenderRadar = findViewById(R.id.layoutSenderRadar); btnScanQr = findViewById(R.id.btnScanQr);
        myRadarAvatar = findViewById(R.id.myRadarAvatar); receiverMyAvatar = findViewById(R.id.receiverMyAvatar); receiverMyName = findViewById(R.id.receiverMyName);
        qrCodeImage = findViewById(R.id.qrCodeImage); ripple1 = findViewById(R.id.ripple1); ripple2 = findViewById(R.id.ripple2);

        isReceiver = getIntent().getBooleanExtra("isReceiver", false);
        if (getIntent().hasExtra("files")) { filesToSend = getIntent().getParcelableArrayListExtra("files"); providedFileNames = getIntent().getStringArrayListExtra("fileNames"); providedFileSizes = getIntent().getStringArrayListExtra("fileSizes"); }

        SharedPreferences prefs = getSharedPreferences("FastShareProfile", MODE_PRIVATE);
        String name = prefs.getString("user_name", Build.MODEL); int avatarIdx = prefs.getInt("user_avatar_index", 0); if (avatarIdx < 0 || avatarIdx >= avatarIcons.length) avatarIdx = 0;

        myEndpointName = name + "|" + avatarIdx + "|" + (int)(Math.random() * 1000); myRadarAvatar.setImageResource(avatarIcons[avatarIdx]);
        if (receiverMyAvatar != null) receiverMyAvatar.setImageResource(avatarIcons[avatarIdx]); if (receiverMyName != null) receiverMyName.setText(name);

        if (isReceiver) { layoutSenderRadar.setVisibility(View.GONE); layoutReceiverQR.setVisibility(View.VISIBLE); startAdvertising(); generateQRCode(); } 
        else { layoutReceiverQR.setVisibility(View.GONE); layoutSenderRadar.setVisibility(View.VISIBLE); startDiscovery(); }

        btnScanQr.setOnClickListener(v -> { IntentIntegrator integrator = new IntentIntegrator(this); integrator.setCaptureActivity(PortraitCaptureActivity.class); integrator.setOrientationLocked(true); integrator.initiateScan(); });

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

    private void addDeviceToRadar(String endpointId, String endpointName) {
        if (radarViews.containsKey(endpointId)) return;
        String[] parts = endpointName.split("\\|"); String name = parts.length > 0 ? parts[0] : "Unknown";
        int avatarIdx = 0; if (parts.length > 1) { try { avatarIdx = Integer.parseInt(parts[1]); } catch (Exception e) {} }
        if (avatarIdx < 0 || avatarIdx >= avatarIcons.length) avatarIdx = 0;

        LinearLayout deviceLayout = new LinearLayout(this); deviceLayout.setOrientation(LinearLayout.VERTICAL); deviceLayout.setGravity(Gravity.CENTER);
        ImageView avatar = new ImageView(this); avatar.setImageResource(avatarIcons[avatarIdx]); avatar.setBackgroundResource(R.drawable.circle_bg); avatar.setPadding(15, 15, 15, 15);
        int size = (int) (70 * getResources().getDisplayMetrics().density); LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(size, size); avatar.setLayoutParams(imgParams); avatar.setElevation(8f);
        TextView nameView = new TextView(this); nameView.setText(name); nameView.setTextColor(Color.parseColor("#3F51B5")); nameView.setTextSize(12f); nameView.setSingleLine(true); nameView.setGravity(Gravity.CENTER); nameView.setPadding(0, 5, 0, 0);
        deviceLayout.addView(avatar); deviceLayout.addView(nameView);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT); deviceLayout.setLayoutParams(params);
        int radius = (int) (110 * getResources().getDisplayMetrics().density); double angle = Math.random() * Math.PI * 2;
        deviceLayout.setTranslationX((float) (radius * Math.cos(angle))); deviceLayout.setTranslationY((float) (radius * Math.sin(angle)));
        deviceLayout.setOnClickListener(v -> { if (!isConnecting) { targetEndpointName = endpointName; connectToDevice(endpointId, endpointName); } });
        RelativeLayout radarContainer = findViewById(R.id.radarContainer); radarContainer.addView(deviceLayout); radarViews.put(endpointId, deviceLayout);
        deviceLayout.setScaleX(0f); deviceLayout.setScaleY(0f); deviceLayout.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(new OvershootInterpolator()).start();
    }
    private void removeDeviceFromRadar(String endpointId) { View view = radarViews.remove(endpointId); if (view != null) { view.animate().scaleX(0f).scaleY(0f).setDuration(200).withEndAction(() -> { RelativeLayout radarContainer = findViewById(R.id.radarContainer); radarContainer.removeView(view); }).start(); } }
    private void generateQRCode() { try { BarcodeEncoder barcodeEncoder = new BarcodeEncoder(); Bitmap bitmap = barcodeEncoder.encodeBitmap("FASTSHARE|" + myEndpointName, BarcodeFormat.QR_CODE, 800, 800); qrCodeImage.setImageBitmap(bitmap); } catch (Exception e) {} }
    private void startAdvertising() { AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(); Nearby.getConnectionsClient(this).startAdvertising(myEndpointName, SERVICE_ID, connectionLifecycleCallback, options); }
    private void startDiscovery() { startRadarAnimation(); DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(); Nearby.getConnectionsClient(this).startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options); }
    private void connectToDevice(String endpointId, String endpointName) { isConnecting = true; ((TextView) findViewById(R.id.txtSearching)).setText("Connecting to " + getDisplayName(endpointName) + "..."); Nearby.getConnectionsClient(this).requestConnection(myEndpointName, endpointId, connectionLifecycleCallback).addOnFailureListener(e -> { isConnecting = false; ((TextView) findViewById(R.id.txtSearching)).setText("Connection Failed"); }); }
    private String getDisplayName(String full) { if (full == null) return "Unknown"; String[] parts = full.split("\\|"); return parts.length >= 1 ? parts[0] : full; }
    private void startRadarAnimation() { if (radarAnimatorSet != null && radarAnimatorSet.isRunning()) return; ObjectAnimator sX1 = ObjectAnimator.ofFloat(ripple1, "scaleX", 1f, 8f); ObjectAnimator sY1 = ObjectAnimator.ofFloat(ripple1, "scaleY", 1f, 8f); ObjectAnimator a1 = ObjectAnimator.ofFloat(ripple1, "alpha", 1f, 0f); sX1.setRepeatCount(ObjectAnimator.INFINITE); sY1.setRepeatCount(ObjectAnimator.INFINITE); a1.setRepeatCount(ObjectAnimator.INFINITE); ObjectAnimator sX2 = ObjectAnimator.ofFloat(ripple2, "scaleX", 1f, 8f); ObjectAnimator sY2 = ObjectAnimator.ofFloat(ripple2, "scaleY", 1f, 8f); ObjectAnimator a2 = ObjectAnimator.ofFloat(ripple2, "alpha", 1f, 0f); sX2.setRepeatCount(ObjectAnimator.INFINITE); sY2.setRepeatCount(ObjectAnimator.INFINITE); a2.setRepeatCount(ObjectAnimator.INFINITE); sX2.setStartDelay(1000); sY2.setStartDelay(1000); a2.setStartDelay(1000); radarAnimatorSet = new AnimatorSet(); radarAnimatorSet.playTogether(sX1, sY1, a1, sX2, sY2, a2); radarAnimatorSet.setDuration(2000); radarAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator()); radarAnimatorSet.start(); }
    private void stopRadarAnimation() { if (radarAnimatorSet != null) radarAnimatorSet.cancel(); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) { IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data); if (result != null && result.getContents() != null) { String[] split = result.getContents().split("\\|", 2); if (split.length == 2 && split[0].equals("FASTSHARE")) { targetEndpointName = split[1]; for (Map.Entry<String, String> entry : discoveredEndpoints.entrySet()) { if (entry.getValue().equals(targetEndpointName)) { connectToDevice(entry.getKey(), entry.getValue()); return; } } Toast.makeText(this, "Device not found nearby yet. Keep waiting...", Toast.LENGTH_SHORT).show(); } } else { super.onActivityResult(requestCode, resultCode, data); } }
    @Override protected void onDestroy() { super.onDestroy(); stopRadarAnimation(); Nearby.getConnectionsClient(this).stopDiscovery(); Nearby.getConnectionsClient(this).stopAdvertising(); }
}