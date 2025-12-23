package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mynas.nastv.R;
import com.mynas.nastv.adapter.SimpleMediaAdapter;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 📱 Video List Activity
 * Displays media items for a category.
 */
public class VideoListActivity extends AppCompatActivity implements SimpleMediaAdapter.OnItemClickListener {
    private static final String TAG = "VideoListActivity";
    
    // UI
    private TextView titleText;
    private RecyclerView videoRecyclerView;
    private TextView emptyStateText;
    
    // Adapter
    private SimpleMediaAdapter mediaAdapter;
    
    // Manager
    private MediaManager mediaManager;
    
    // Data
    private String categoryType;
    private String categoryName;
    private String categoryGuid;
    private List<MediaItem> mediaList;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);
        
        Log.d(TAG, "🚀 VideoListActivity Start");
        
        mediaManager = new MediaManager(this);
        initializeData();
        initializeViews();
        loadMediaData();
    }
    
    private void initializeData() {
        Intent intent = getIntent();
        categoryType = intent.getStringExtra("category_type");
        categoryName = intent.getStringExtra("category_name");
        categoryGuid = intent.getStringExtra("category_guid");
        
        if (categoryType == null) categoryType = "unknown";
        if (categoryName == null) categoryName = "Unknown Category";
        if (categoryGuid == null) categoryGuid = "";
        
        mediaList = new ArrayList<>();
    }
    
    private void initializeViews() {
        titleText = findViewById(R.id.title_text);
        videoRecyclerView = findViewById(R.id.video_recycler_view);
        emptyStateText = findViewById(R.id.empty_state_text);
        
        titleText.setText(categoryName);
        
        // 4 columns for TV
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4);
        videoRecyclerView.setLayoutManager(gridLayoutManager);
        
        emptyStateText.setText("Loading " + categoryName + "...");
    }
    
    private void loadMediaData() {
        if (categoryGuid != null && !categoryGuid.isEmpty()) {
            loadRealMediaData();
        } else {
            loadMediaDbList();
        }
    }
    
    private void loadMediaDbList() {
        mediaManager.getMediaDbList(new MediaManager.MediaCallback<List<MediaManager.MediaDbItem>>() {
            @Override
            public void onSuccess(List<MediaManager.MediaDbItem> dbItems) {
                MediaManager.MediaDbItem targetDb = null;
                for (MediaManager.MediaDbItem item : dbItems) {
                    if (categoryType.equalsIgnoreCase(item.getCategory())) {
                        targetDb = item;
                        break;
                    }
                }
                
                if (targetDb == null && !dbItems.isEmpty()) {
                    targetDb = dbItems.get(0);
                }
                
                if (targetDb != null) {
                    categoryGuid = targetDb.getGuid();
                    loadRealMediaData();
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(VideoListActivity.this, "No matching library found", Toast.LENGTH_LONG).show();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(VideoListActivity.this, "Error loading library: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void loadRealMediaData() {
        // Updated to use the correct MediaManager method which matches the new ApiService
        mediaManager.getMediaDbInfos(categoryGuid, new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> items) {
                runOnUiThread(() -> {
                    mediaList.clear();
                    if (items != null) mediaList.addAll(items);
                    updateUI();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(VideoListActivity.this, "Error loading items: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void updateUI() {
        if (mediaList.isEmpty()) {
            videoRecyclerView.setVisibility(android.view.View.GONE);
            emptyStateText.setVisibility(android.view.View.VISIBLE);
            emptyStateText.setText("No content in " + categoryName);
        } else {
            videoRecyclerView.setVisibility(android.view.View.VISIBLE);
            emptyStateText.setVisibility(android.view.View.GONE);
            
            if (mediaAdapter == null) {
                mediaAdapter = new SimpleMediaAdapter(this, mediaList);
                mediaAdapter.setOnItemClickListener(this);
                videoRecyclerView.setAdapter(mediaAdapter);
            } else {
                mediaAdapter.updateData(mediaList);
            }
        }
    }
    
    @Override
    public void onItemClick(MediaItem mediaItem, int position) {
        Log.d(TAG, "Navigating to MediaDetailActivity: " + mediaItem.getTitle());
        
        // Navigate to MediaDetailActivity (skipping SeasonDetailActivity for now as requested flow implies Detail -> Play)
        // If it's a series, MediaDetailActivity should handle season/episode selection.
        Intent intent = new Intent(this, MediaDetailActivity.class);
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_GUID, mediaItem.getGuid());
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TITLE, mediaItem.getTitle());
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TYPE, mediaItem.getType());
        startActivity(intent);
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
