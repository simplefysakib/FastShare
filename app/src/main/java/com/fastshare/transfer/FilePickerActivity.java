package com.fastshare.transfer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

public class FilePickerActivity extends AppCompatActivity {
    private static final int PICK_FILES_REQUEST = 101;
    private MediaAdapter adapter;
    private List<MediaItem> allMediaItems, mediaItems, cartItems;
    private LinearLayout btnReviewCart; private Button btnSendSelected, tabApps, tabPhotos, tabFiles;
    private ImageView btnSort, cartIcon; private EditText editSearch; private TextView txtSelectedCount;
    private ExecutorService imageExecutor; private boolean isAddingMore; private ListView listView; private FrameLayout rootLayout;
    private ArrayList<Uri> selectedFiles; private ArrayList<String> selectedNames, selectedSizes;
    private int currentSortMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_file_picker);

        allMediaItems = new ArrayList<>(); mediaItems = new ArrayList<>(); cartItems = new ArrayList<>();
        selectedFiles = new ArrayList<>(); selectedNames = new ArrayList<>(); selectedSizes = new ArrayList<>();
        imageExecutor = Executors.newFixedThreadPool(4); isAddingMore = getIntent().getBooleanExtra("isAddingMore", false);

        rootLayout = findViewById(R.id.rootLayout); listView = findViewById(R.id.mediaListView); btnSendSelected = findViewById(R.id.btnSendSelected);
        btnReviewCart = findViewById(R.id.btnReviewCart); txtSelectedCount = findViewById(R.id.txtSelectedCount); cartIcon = findViewById(R.id.cartIcon);
        tabApps = findViewById(R.id.tabApps); tabPhotos = findViewById(R.id.tabPhotos); tabFiles = findViewById(R.id.tabFiles);
        editSearch = findViewById(R.id.editSearch); btnSort = findViewById(R.id.btnSort);

        adapter = new MediaAdapter(); listView.setAdapter(adapter);
        tabApps.setOnClickListener(v -> loadAppsUltraFast()); tabPhotos.setOnClickListener(v -> loadPhotos()); tabFiles.setOnClickListener(v -> openSystemFilePicker());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            MediaItem item = mediaItems.get(position); item.isSelected = !item.isSelected;
            if (item.isSelected) { cartItems.add(item); flyIconToCart(view.findViewById(R.id.imgIcon)); } else cartItems.remove(item);
            adapter.notifyDataSetChanged(); recalculateSelection();
        });

        btnReviewCart.setOnClickListener(v -> showSelectedItemsDialog()); btnSendSelected.setOnClickListener(v -> proceedToTransfer());
        btnSort.setOnClickListener(v -> { currentSortMode = (currentSortMode + 1) % 3; applySort(); adapter.notifyDataSetChanged(); });
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadAppsUltraFast(); setupBannerAd();
    }

    private void setupBannerAd() {
        if (!UnityAds.isInitialized()) UnityAds.initialize(getApplicationContext(), "6073571", false, null);
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

    private void updateTabUI(int activeTab) {
        tabApps.setBackgroundColor(Color.TRANSPARENT); tabPhotos.setBackgroundColor(Color.TRANSPARENT); tabFiles.setBackgroundColor(Color.TRANSPARENT);
        if (activeTab == 0) tabApps.setBackgroundColor(Color.parseColor("#303F9F"));
        else if (activeTab == 1) tabPhotos.setBackgroundColor(Color.parseColor("#303F9F"));
        else if (activeTab == 2) tabFiles.setBackgroundColor(Color.parseColor("#303F9F"));
    }

    private void proceedToTransfer() {
        if (selectedFiles.isEmpty()) { Toast.makeText(this, "Select files first", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = (isAddingMore && TransferManager.connectedEndpointId != null) ? new Intent(this, TransferActivity.class) : new Intent(this, DeviceDiscoveryActivity.class);
        intent.putParcelableArrayListExtra("files", selectedFiles); intent.putStringArrayListExtra("fileNames", selectedNames); intent.putStringArrayListExtra("fileSizes", selectedSizes);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(intent); finish();
    }

    private void filterList(String query) { mediaItems.clear(); if (query.isEmpty()) mediaItems.addAll(allMediaItems); else for (MediaItem item : allMediaItems) if (item.displayName.toLowerCase().contains(query.toLowerCase())) mediaItems.add(item); adapter.notifyDataSetChanged(); }
    private void applySort() { if (currentSortMode == 1) Collections.sort(allMediaItems, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName)); else if (currentSortMode == 2) Collections.sort(allMediaItems, (a, b) -> Long.compare(b.sizeBytes, a.sizeBytes)); filterList(editSearch.getText().toString()); }
    
    private void flyIconToCart(ImageView sourceImg) {
        int[] srcLoc = new int[2]; sourceImg.getLocationInWindow(srcLoc); int[] destLoc = new int[2]; cartIcon.getLocationInWindow(destLoc);
        ImageView flyingImg = new ImageView(this); flyingImg.setImageDrawable(sourceImg.getDrawable());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sourceImg.getWidth(), sourceImg.getHeight()); params.leftMargin = srcLoc[0]; params.topMargin = srcLoc[1]; rootLayout.addView(flyingImg, params);
        flyingImg.animate().translationX(destLoc[0] - srcLoc[0]).translationY(destLoc[1] - srcLoc[1]).scaleX(0.2f).scaleY(0.2f).setDuration(400).setInterpolator(new AccelerateInterpolator()).setListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { rootLayout.removeView(flyingImg); } }).start();
    }

    private void loadAppsUltraFast() {
        updateTabUI(0); txtSelectedCount.setText("Loading Apps..."); allMediaItems.clear(); mediaItems.clear(); adapter.notifyDataSetChanged();
        new Thread(() -> {
            PackageManager pm = getPackageManager(); List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA); List<MediaItem> tempItems = new ArrayList<>();
            for (ApplicationInfo appInfo : packages) {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    MediaItem item = new MediaItem(); item.displayName = pm.getApplicationLabel(appInfo).toString(); item.originalName = appInfo.packageName; item.isApp = true;
                    File apkFile = new File(appInfo.sourceDir); item.sizeBytes = apkFile.length(); item.uri = Uri.fromFile(apkFile);
                    for (MediaItem cartItem : cartItems) if (cartItem.uri.equals(item.uri)) item.isSelected = true; tempItems.add(item);
                }
            }
            runOnUiThread(() -> { allMediaItems.addAll(tempItems); applySort(); txtSelectedCount.setText("Selected (" + cartItems.size() + ")"); });
        }).start();
    }

    private void loadPhotos() {
        updateTabUI(1); txtSelectedCount.setText("Loading Photos..."); allMediaItems.clear(); mediaItems.clear(); adapter.notifyDataSetChanged();
        new Thread(() -> {
            List<MediaItem> tempItems = new ArrayList<>(); String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE};
            try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID), nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME), sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol); String name = cursor.getString(nameCol); long size = cursor.getLong(sizeCol);
                        MediaItem item = new MediaItem(); item.displayName = name != null ? name : "Photo"; item.originalName = item.displayName; item.sizeBytes = size; item.uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)); item.isApp = false;
                        for (MediaItem cartItem : cartItems) if (cartItem.uri.equals(item.uri)) item.isSelected = true; tempItems.add(item);
                    }
                }
            } catch (Exception e) {}
            runOnUiThread(() -> { allMediaItems.addAll(tempItems); applySort(); txtSelectedCount.setText("Selected (" + cartItems.size() + ")"); });
        }).start();
    }

    private void openSystemFilePicker() { updateTabUI(2); Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*"); intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); startActivityForResult(intent, PICK_FILES_REQUEST); }
    private void showSelectedItemsDialog() {
        if (cartItems.isEmpty()) { Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(this); builder.setTitle("Selected Files (" + cartItems.size() + ")");
        String[] names = new String[cartItems.size()]; boolean[] checked = new boolean[cartItems.size()];
        for (int i = 0; i < cartItems.size(); i++) { names[i] = cartItems.get(i).displayName; checked[i] = true; }
        builder.setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> { if (!isChecked) cartItems.get(which).isSelected = false; });
        builder.setPositiveButton("Done", (dialog, which) -> { List<MediaItem> toRemove = new ArrayList<>(); for (MediaItem item : cartItems) if (!item.isSelected) toRemove.add(item); cartItems.removeAll(toRemove); recalculateSelection(); adapter.notifyDataSetChanged(); }).show();
    }
    private void recalculateSelection() {
        selectedFiles.clear(); selectedNames.clear(); selectedSizes.clear();
        for (MediaItem item : cartItems) { selectedFiles.add(item.uri); selectedNames.add(item.isApp ? item.originalName + ".apk" : item.originalName); selectedSizes.add(String.valueOf(item.sizeBytes)); }
        txtSelectedCount.setText("Selected (" + selectedFiles.size() + ")");
    }
    private String getFileName(Uri uri) {
        String result = null; if ("content".equals(uri.getScheme())) { try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) { if (cursor != null && cursor.moveToFirst()) { int index = cursor.getColumnIndex("_display_name"); if (index != -1) result = cursor.getString(index); } } }
        if (result == null) { result = uri.getPath(); int cut = result.lastIndexOf('/'); if (cut != -1) result = result.substring(cut + 1); } return result != null ? result : "Unknown_File";
    }
    private long getFileSize(Uri uri) { try { if ("file".equalsIgnoreCase(uri.getScheme())) return new File(uri.getPath()).length(); try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) { if (pfd != null) return pfd.getStatSize(); } } catch (Exception e) {} return 1024L; }
    
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILES_REQUEST && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri()); else if (data.getData() != null) uris.add(data.getData());
            for (Uri uri : uris) { try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {} selectedFiles.add(uri); selectedNames.add(getFileName(uri)); selectedSizes.add(String.valueOf(getFileSize(uri))); }
            proceedToTransfer();
        }
    }
    @Override protected void onDestroy() { super.onDestroy(); imageExecutor.shutdownNow(); }

    private class MediaAdapter extends ArrayAdapter<MediaItem> {
        public MediaAdapter() { super(FilePickerActivity.this, R.layout.list_item_media, mediaItems); }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_media, parent, false);
            if (position >= mediaItems.size()) return convertView; MediaItem item = mediaItems.get(position);
            ImageView imgIcon = convertView.findViewById(R.id.imgIcon); TextView txtName = convertView.findViewById(R.id.txtName); CheckBox chkSelect = convertView.findViewById(R.id.chkSelect);
            txtName.setText(item.displayName); chkSelect.setChecked(item.isSelected); imgIcon.setTag(item.uri);
            if (item.isApp) { imgIcon.setImageResource(android.R.drawable.sym_def_app_icon); imageExecutor.execute(() -> { try { Drawable icon = getPackageManager().getApplicationIcon(item.originalName); runOnUiThread(() -> { if (imgIcon.getTag().equals(item.uri)) imgIcon.setImageDrawable(icon); }); } catch (Exception e) {} }); }
            else { imgIcon.setImageResource(android.R.drawable.ic_menu_gallery); imageExecutor.execute(() -> { try { runOnUiThread(() -> { if (imgIcon.getTag().equals(item.uri)) imgIcon.setImageURI(item.uri); }); } catch (Exception e) {} }); }
            return convertView;
        }
    }
    private static class MediaItem { String displayName, originalName; long sizeBytes; Uri uri; boolean isApp, isSelected; }
}