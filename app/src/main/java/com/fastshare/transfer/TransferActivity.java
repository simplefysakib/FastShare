package com.fastshare.transfer;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

public class TransferActivity extends AppCompatActivity {
    private TransferAdapter adapter; private Button btnSendMore; private long currentTrackingPayloadId = -1L; private int filesCompletedCount = 0;
    private HashMap<Long, Payload> incomingPayloads = new HashMap<>(); private boolean isServer, isSoundPlayed = false; private long lastBytesTransferred = 0L, lastUiUpdateTime = 0L, overallBytesCompleted = 0L, overallTotalBytes = 0L;
    private HashMap<Long, TransferItem> payloadMap = new HashMap<>(); private List<TransferItem> transferList = new ArrayList<>(); private ListView transferListView;
    private TextView txtSpeed, txtStatus, txtTotalProgress; private WaveProgressView waveProgressView;

    private String unityGameID = "6073571";
    private Boolean testMode = false; // Real ads
    private String interstitialAdUnitId = "Interstitial_Android";
    private boolean isTransferFinishedAdShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_transfer);

        waveProgressView = findViewById(R.id.waveProgressView); txtTotalProgress = findViewById(R.id.txtTotalProgress); txtSpeed = findViewById(R.id.txtSpeed); txtStatus = findViewById(R.id.txtStatus); transferListView = findViewById(R.id.transferListView); btnSendMore = findViewById(R.id.btnSendMore);
        TransferManager.activeActivity = this; adapter = new TransferAdapter(); transferListView.setAdapter(adapter);

        if (!UnityAds.isInitialized()) {
            UnityAds.initialize(getApplicationContext(), unityGameID, testMode, new IUnityAdsInitializationListener() {
                @Override public void onInitializationComplete() { UnityAds.load(interstitialAdUnitId, new IUnityAdsLoadListener() { @Override public void onUnityAdsAdLoaded(String placementId) {} @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String msg) {} }); }
                @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {}
            });
        }

        transferListView.setOnItemClickListener((parent, view, position, id) -> { TransferItem item = transferList.get(position); if (item.status.equals("Completed")) openFile(item); });
        btnSendMore.setOnClickListener(v -> { Intent intent = new Intent(this, FilePickerActivity.class); intent.putExtra("isAddingMore", true); startActivity(intent); });
        
        handleIncomingIntent(getIntent());
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
            UnityAds.initialize(getApplicationContext(), unityGameID, testMode, new IUnityAdsInitializationListener() {
                @Override public void onInitializationComplete() { runOnUiThread(() -> bottomBanner.load()); }
                @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String msg) {}
            });
        }
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIncomingIntent(intent); }

    private void handleIncomingIntent(Intent intent) {
        isServer = intent.getBooleanExtra("isServer", isServer);
        if (intent.hasExtra("files")) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra("files"); ArrayList<String> names = intent.getStringArrayListExtra("fileNames"); ArrayList<String> sizes = intent.getStringArrayListExtra("fileSizes");
            int size = uris.size(); long addedBytes = 0L; int startIndex = transferList.size();
            for (int i = 0; i < size; i++) { String name = names != null ? names.get(i) : "File"; long fileSize = sizes != null ? Long.parseLong(sizes.get(i)) : 1024L; transferList.add(new TransferItem(name, fileSize, uris.get(i))); addedBytes += fileSize; }
            overallTotalBytes += addedBytes; adapter.notifyDataSetChanged(); sendFilesBatch(uris, names, sizes, startIndex);
        }
        if (isServer && transferList.isEmpty()) txtStatus.setText("Connected! Waiting for files...");
    }

    // ULTRA-FAST SENDING ENGINE
    private void sendFilesBatch(ArrayList<Uri> uris, ArrayList<String> names, ArrayList<String> sizes, int startIndex) {
        txtStatus.setText("Preparing Connection...");
        new Thread(() -> {
            try { 
                // Initial hardware stabilization delay (Only once per batch)
                Thread.sleep(1000); 
            } catch (Exception e) {}
            
            runOnUiThread(() -> txtStatus.setText("Sending Files..."));

            for (int i = 0; i < uris.size(); i++) {
                TransferItem item = transferList.get(startIndex + i);
                try {
                    Uri uri = uris.get(i); 
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r"); 
                    if (pfd == null) throw new Exception("File read error");

                    Payload filePayload = Payload.fromFile(pfd); 
                    long payloadId = filePayload.getId();
                    String meta = payloadId + "::" + names.get(i) + "::" + sizes.get(i) + "::" + overallTotalBytes;
                    
                    Nearby.getConnectionsClient(this).sendPayload(TransferManager.connectedEndpointId, Payload.fromBytes(meta.getBytes())); 
                    
                    // MICRO-DELAY: 50ms is enough for OS to order payloads. Massively speeds up multi-file transfers (e.g. 500 photos)
                    Thread.sleep(50); 
                    
                    Nearby.getConnectionsClient(this).sendPayload(TransferManager.connectedEndpointId, filePayload);
                    item.payloadId = payloadId; 
                    payloadMap.put(payloadId, item);

                    runOnUiThread(() -> {
                        item.status = "Sending...";
                        adapter.notifyDataSetChanged();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        item.status = "Read Error";
                        adapter.notifyDataSetChanged();
                        Toast.makeText(TransferActivity.this, "Could not read file: " + item.name, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    public void handlePayloadReceived(Payload payload) {
        if (payload.getType() == Payload.Type.BYTES) {
            String data = new String(payload.asBytes());
            if (data.startsWith("SYNC::")) { String[] parts = data.split("::"); if (parts.length >= 6) { runOnUiThread(() -> { txtSpeed.setText(parts[1] + " MB/s"); txtTotalProgress.setText(parts[3]); waveProgressView.setProgress(Float.parseFloat(parts[5])); }); } }
            else if (data.startsWith("ACK::")) { long payloadId = Long.parseLong(data.split("::")[1]); TransferItem item = payloadMap.get(payloadId); if (item != null) { item.status = "Completed"; item.progress = 100; overallBytesCompleted += item.size; filesCompletedCount++; HistoryActivity.saveHistory(this, item.name, "Sent"); runOnUiThread(() -> adapter.notifyDataSetChanged()); } checkAllComplete(); }
            else { String[] parts = data.split("::"); if (parts.length >= 4) { long payloadId = Long.parseLong(parts[0]); overallTotalBytes = Long.parseLong(parts[3]); TransferItem item = new TransferItem(parts[1], Long.parseLong(parts[2]), null); item.payloadId = payloadId; transferList.add(item); payloadMap.put(payloadId, item); runOnUiThread(() -> adapter.notifyDataSetChanged()); } }
        } else if (payload.getType() == Payload.Type.FILE) { incomingPayloads.put(payload.getId(), payload); TransferItem item = payloadMap.get(payload.getId()); if (item != null) { item.status = "Receiving..."; runOnUiThread(() -> adapter.notifyDataSetChanged()); } }
    }

    public void handlePayloadUpdate(PayloadTransferUpdate update) {
        long payloadId = update.getPayloadId(); TransferItem item = payloadMap.get(payloadId); if (item == null) return;
        long bytesTransferred = update.getBytesTransferred(); if (item.size > 0) { item.progress = (int) (100L * bytesTransferred / item.size); item.transferredBytes = bytesTransferred; } long now = System.currentTimeMillis();

        if (isServer) {
            if (currentTrackingPayloadId != payloadId) { currentTrackingPayloadId = payloadId; lastBytesTransferred = 0L; lastUiUpdateTime = now; }
            if (now - lastUiUpdateTime >= 300L || bytesTransferred == item.size) {
                long timeDiff = now - lastUiUpdateTime; long bytesDiff = bytesTransferred - lastBytesTransferred; double speed = timeDiff > 0 ? (1000L * bytesDiff / timeDiff / 1048576.0) : 0.0;
                long totalCompletedBytes = overallBytesCompleted + bytesTransferred; final float overallPercent = Math.min((totalCompletedBytes * 100.0f) / overallTotalBytes, 100f);
                String speedStr = String.format("%.1f", speed); String progressStr = String.format("%.1f/%.1f MB", totalCompletedBytes / 1048576.0, overallTotalBytes / 1048576.0);
                runOnUiThread(() -> { txtSpeed.setText(speedStr + " MB/s"); txtTotalProgress.setText(progressStr); waveProgressView.setProgress(overallPercent); adapter.notifyDataSetChanged(); });
                Nearby.getConnectionsClient(this).sendPayload(TransferManager.connectedEndpointId, Payload.fromBytes(("SYNC::" + speedStr + "::00:00::" + progressStr + "::0::" + overallPercent).getBytes())); lastUiUpdateTime = now; lastBytesTransferred = bytesTransferred;
            }
        } else { if (now - lastUiUpdateTime >= 300L || bytesTransferred == item.size) { runOnUiThread(() -> adapter.notifyDataSetChanged()); lastUiUpdateTime = now; } }

        if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
            if (isServer) { 
                item.status = "Saving..."; 
                runOnUiThread(() -> adapter.notifyDataSetChanged()); 
                savePayloadToFile(payloadId, item); 
            }
        } else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE) { item.status = "Failed"; runOnUiThread(() -> adapter.notifyDataSetChanged()); }
    }

    // NIO ZERO-COPY SAVE ENGINE
    private void savePayloadToFile(long payloadId, TransferItem item) {
        Payload payload = incomingPayloads.get(payloadId);
        if (payload != null && payload.asFile() != null) {
            new Thread(() -> {
                try {
                    Payload.File payloadFile = payload.asFile(); 
                    File fastShareDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FastShare"); 
                    if (!fastShareDir.exists()) fastShareDir.mkdirs();
                    
                    int counter = 1; String nameWithoutExt = item.name; String ext = ""; int dotIndex = item.name.lastIndexOf("."); 
                    if (dotIndex > 0) { nameWithoutExt = item.name.substring(0, dotIndex); ext = item.name.substring(dotIndex); }
                    File destFile = new File(fastShareDir, item.name); 
                    while (destFile.exists()) { destFile = new File(fastShareDir, nameWithoutExt + "_" + counter + ext); counter++; } 
                    item.name = destFile.getName();

                    boolean movedSuccessfully = false;
                    
                    // Priority 1: Instant Rename (0 seconds overhead)
                    File sourceFile = payloadFile.asJavaFile();
                    if (sourceFile != null && sourceFile.exists()) {
                        movedSuccessfully = sourceFile.renameTo(destFile);
                    }

                    // Priority 2: NIO Channel Transfer (Ultra-fast DMA copy for large files)
                    if (!movedSuccessfully && sourceFile != null && sourceFile.exists()) {
                        try (FileInputStream fis = new FileInputStream(sourceFile);
                             FileOutputStream fos = new FileOutputStream(destFile);
                             FileChannel inChannel = fis.getChannel();
                             FileChannel outChannel = fos.getChannel()) {
                            
                            inChannel.transferTo(0, inChannel.size(), outChannel);
                            fos.getFD().sync(); // Force write to physical storage
                            movedSuccessfully = true;
                        }
                        if (movedSuccessfully) sourceFile.delete();
                    }

                    // Priority 3: Fallback stream for Content URIs with 1MB Buffer
                    if (!movedSuccessfully && payloadFile.asUri() != null) {
                        try (InputStream in = getContentResolver().openInputStream(payloadFile.asUri());
                             FileOutputStream fos = new FileOutputStream(destFile)) { 
                            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                            int read; 
                            while ((read = in.read(buffer)) != -1) { fos.write(buffer, 0, read); }
                            fos.flush();
                            fos.getFD().sync(); 
                        }
                    }
                    
                    MediaScannerConnection.scanFile(TransferActivity.this, new String[]{destFile.getAbsolutePath()}, null, null); 
                    item.savedFile = destFile;
                    HistoryActivity.saveHistory(TransferActivity.this, item.name, "Received"); 

                    item.status = "Completed";
                    overallBytesCompleted += item.size;
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                    
                    Nearby.getConnectionsClient(TransferActivity.this).sendPayload(TransferManager.connectedEndpointId, Payload.fromBytes(("ACK::" + payloadId).getBytes())); 
                    checkAllComplete();

                } catch (Exception e) {
                    e.printStackTrace();
                    item.status = "Save Error";
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            }).start();
        }
    }

    private void checkAllComplete() {
        boolean allDone = true; for (TransferItem item : transferList) if (!item.status.equals("Completed")) allDone = false;
        if (allDone && !transferList.isEmpty()) {
            playSuccessSound();
            runOnUiThread(() -> {
                txtStatus.setText("All files transferred successfully!"); txtSpeed.setText("0.0 MB/s"); waveProgressView.setProgress(100.0f);
                if (!isTransferFinishedAdShown && UnityAds.isInitialized()) {
                    isTransferFinishedAdShown = true;
                    UnityAds.show(TransferActivity.this, interstitialAdUnitId, new IUnityAdsShowListener() {
                        @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {}
                        @Override public void onUnityAdsShowStart(String placementId) {}
                        @Override public void onUnityAdsShowClick(String placementId) {}
                        @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) { UnityAds.load(interstitialAdUnitId, null); }
                    });
                }
            });
        }
    }

    private void playSuccessSound() { if (isSoundPlayed) return; isSoundPlayed = true; try { Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); if (v != null && Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)); RingtoneManager.getRingtone(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play(); } catch (Exception e) {} }
    private void openFile(TransferItem item) {
        try { if (item.savedFile == null || !item.savedFile.exists()) return; Uri uriToOpen = FileProvider.getUriForFile(this, getPackageName() + ".provider", item.savedFile); Intent intent = new Intent(Intent.ACTION_VIEW); String ext = MimeTypeMap.getFileExtensionFromUrl(item.savedFile.getAbsolutePath()); String mime = "*/*"; if (ext != null) mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase()); if (item.name.toLowerCase().endsWith(".apk")) mime = "application/vnd.android.package-archive"; intent.setDataAndType(uriToOpen, mime); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); } catch (Exception e) {}
    }
    @Override public void onBackPressed() { new AlertDialog.Builder(this).setTitle("Stop Transfer?").setMessage("Going back will disconnect and stop current transfers.").setPositiveButton("Yes", (dialog, which) -> { if (TransferManager.connectedEndpointId != null) Nearby.getConnectionsClient(this).disconnectFromEndpoint(TransferManager.connectedEndpointId); startActivity(new Intent(this, MainActivity.class)); finish(); }).setNegativeButton("No", null).show(); }

    class TransferAdapter extends ArrayAdapter<TransferItem> {
        public TransferAdapter() { super(TransferActivity.this, R.layout.list_item_transfer, transferList); }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_transfer, parent, false); TransferItem item = getItem(position);
            TextView tvName = convertView.findViewById(R.id.itemName); TextView tvDetails = convertView.findViewById(R.id.itemSizeProgress); ProgressBar pb = convertView.findViewById(R.id.itemProgressBar); ImageView statusIcon = convertView.findViewById(R.id.itemStatusIcon);
            tvName.setText(item.name); double sizeMB = item.size / 1048576.0; double transMB = item.transferredBytes / 1048576.0;
            if (item.status.equals("Completed")) { tvDetails.setText(String.format("%.1f MB • Completed", sizeMB)); pb.setVisibility(View.GONE); statusIcon.setImageResource(R.drawable.ic_check_circle); statusIcon.clearColorFilter(); }
            else if (item.status.contains("ing...")) { tvDetails.setText(String.format("%.1f/%.1f MB", transMB, sizeMB)); pb.setVisibility(View.VISIBLE); pb.setProgress(item.progress); statusIcon.setImageResource(android.R.drawable.ic_popup_sync); statusIcon.setColorFilter(0xFF2196F3); }
            else if (item.status.equals("Saving...")) { tvDetails.setText("Saving to Phone..."); pb.setVisibility(View.VISIBLE); pb.setIndeterminate(true); statusIcon.setImageResource(android.R.drawable.ic_popup_sync); statusIcon.setColorFilter(0xFFFFA500); } 
            else if (item.status.contains("Error")) { tvDetails.setText("Failed to read/save file"); pb.setVisibility(View.GONE); statusIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); statusIcon.setColorFilter(0xFFF44336); }
            else { tvDetails.setText(String.format("%.1f MB • Waiting", sizeMB)); pb.setVisibility(View.GONE); statusIcon.setImageResource(android.R.drawable.ic_menu_upload); statusIcon.setColorFilter(0xFF888888); }
            return convertView;
        }
    }
    class TransferItem { String name, status; long payloadId, size, transferredBytes; int progress; File savedFile; Uri savedUri, uri; public TransferItem(String name, long size, Uri uri) { this.name = name; this.size = size; this.uri = uri; this.status = "Waiting..."; this.progress = 0; this.transferredBytes = 0L; } }
}