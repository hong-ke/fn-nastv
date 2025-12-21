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
 * 📱 视频列表Activity - 简化版本
 * 功能：显示特定分类的媒体列表，支持网格布局和基础交互
 */
public class VideoListActivity extends AppCompatActivity implements SimpleMediaAdapter.OnItemClickListener {
    private static final String TAG = "VideoListActivity";
    
    // UI组件
    private TextView titleText;
    private RecyclerView videoRecyclerView;
    private TextView emptyStateText;
    
    // 适配器
    private SimpleMediaAdapter mediaAdapter;
    
    // 数据管理器
    private MediaManager mediaManager;
    
    // 数据
    private String categoryType;
    private String categoryName;
    private String categoryGuid;
    private List<MediaItem> mediaList;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);
        
        Log.d(TAG, "🚀 VideoListActivity启动");
        
        // 🔗 初始化数据管理器
        mediaManager = new MediaManager(this);
        
        // 🔗 初始化数据
        initializeData();
        
        // 🔗 初始化视图
        initializeViews();
        
        // 📊 加载媒体数据
        loadMediaData();
    }
    
    /**
     * 📊 初始化数据
     */
    private void initializeData() {
        Intent intent = getIntent();
        categoryType = intent.getStringExtra("category_type");
        categoryName = intent.getStringExtra("category_name");
        categoryGuid = intent.getStringExtra("category_guid");
        
        if (categoryType == null) categoryType = "unknown";
        if (categoryName == null) categoryName = "未知分类";
        if (categoryGuid == null) categoryGuid = "";
        
        Log.d(TAG, "📂 分类信息 - 类型: " + categoryType + ", 名称: " + categoryName + ", GUID: " + categoryGuid);
        
        mediaList = new ArrayList<>();
    }
    
    /**
     * 🔗 初始化视图
     */
    private void initializeViews() {
        Log.d(TAG, "📱 初始化视图组件");
        
        // 绑定UI组件
        titleText = findViewById(R.id.title_text);
        videoRecyclerView = findViewById(R.id.video_recycler_view);
        emptyStateText = findViewById(R.id.empty_state_text);
        
        // 设置标题
        titleText.setText(categoryName);
        
        // 设置RecyclerView网格布局 (Android TV推荐4列)
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4);
        videoRecyclerView.setLayoutManager(gridLayoutManager);
        
        // 空状态处理
        emptyStateText.setText("正在加载" + categoryName + "...");
        
        Log.d(TAG, "✅ 视图组件初始化完成");
    }
    
    /**
     * 📊 加载媒体数据
     */
    private void loadMediaData() {
        Log.d(TAG, "📊 开始加载媒体数据: " + categoryType + ", GUID: " + categoryGuid);
        
        if (categoryGuid != null && !categoryGuid.isEmpty()) {
            // 使用真实API获取数据
            loadRealMediaData();
        } else {
            // 如果没有GUID，先获取媒体库列表
            loadMediaDbList();
        }
    }
    
    /**
     * 📚 加载媒体库列表
     */
    private void loadMediaDbList() {
        Log.d(TAG, "📚 加载媒体库列表...");
        
        mediaManager.getMediaDbList(new MediaManager.MediaCallback<List<MediaManager.MediaDbItem>>() {
            @Override
            public void onSuccess(List<MediaManager.MediaDbItem> dbItems) {
                Log.d(TAG, "✅ 媒体库列表获取成功，共 " + dbItems.size() + " 个");
                
                // 查找匹配的媒体库 - 大小写不敏感匹配
                MediaManager.MediaDbItem targetDb = null;
                for (MediaManager.MediaDbItem item : dbItems) {
                    Log.d(TAG, "🔍 检查媒体库: " + item.getName() + " (Category: " + item.getCategory() + ", GUID: " + item.getGuid() + ")");
                    if (categoryType.equalsIgnoreCase(item.getCategory())) {
                        targetDb = item;
                        Log.d(TAG, "✅ 找到匹配的媒体库: " + item.getName());
                        break;
                    }
                }
                
                // 如果没有找到精确匹配，尝试使用第一个可用的媒体库
                if (targetDb == null && !dbItems.isEmpty()) {
                    targetDb = dbItems.get(0);
                    Log.d(TAG, "🎯 使用第一个可用媒体库: " + targetDb.getName() + " (GUID: " + targetDb.getGuid() + ")");
                }
                
                if (targetDb != null) {
                    categoryGuid = targetDb.getGuid();
                    Log.d(TAG, "📂 找到匹配的媒体库: " + targetDb.getName() + " (GUID: " + categoryGuid + ")");
                    loadRealMediaData();
                } else {
                    Log.w(TAG, "⚠️ 未找到匹配的媒体库，使用模拟数据");
                    generateMockData();
                    updateUI();
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 媒体库列表获取失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(VideoListActivity.this, "媒体库获取失败: " + error, Toast.LENGTH_LONG).show();
                    // 降级到模拟数据
                    generateMockData();
                    updateUI();
                });
            }
        });
    }
    
    /**
     * 📖 加载真实媒体数据
     */
    private void loadRealMediaData() {
        Log.d(TAG, "📖 加载真实媒体数据: " + categoryGuid);
        
        mediaManager.getMediaDbInfos(categoryGuid, new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> items) {
                Log.d(TAG, "✅ 媒体数据获取成功，共 " + items.size() + " 个项目");
                
                runOnUiThread(() -> {
                    mediaList.clear();
                    if (items.isEmpty()) {
                        // 如果API返回空数据，使用模拟数据作为fallback
                        Log.d(TAG, "📊 API返回空数据，使用模拟数据作为fallback");
                        generateMockData();
                    } else {
                        mediaList.addAll(items);
                    }
                    updateUI();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 媒体数据获取失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(VideoListActivity.this, "媒体数据获取失败: " + error, Toast.LENGTH_LONG).show();
                    // 降级到模拟数据
                    generateMockData();
                    updateUI();
                });
            }
        });
    }
    
    /**
     * 🎭 生成模拟数据
     */
    private void generateMockData() {
        Log.d(TAG, "🎭 生成模拟数据");
        
        mediaList.clear();
        
        // 根据分类类型生成不同的模拟数据
        switch (categoryType) {
            case "movie":
                generateMovieData();
                break;
            case "tv":
                generateTVData();
                break;
            case "anime":
                generateAnimeData();
                break;
            default:
                generateDefaultData();
                break;
        }
        
        Log.d(TAG, "✅ 模拟数据生成完成，共 " + mediaList.size() + " 项");
    }
    
    private void generateMovieData() {
        for (int i = 1; i <= 12; i++) {
            MediaItem movie = new MediaItem();
            movie.setId("movie_" + i);
            movie.setTitle("电影 " + i);
            movie.setType("movie");
            movie.setGenre("动作, 科幻");
            movie.setRating(8.0f + (i % 3) * 0.5f);
            movie.setYear(String.valueOf(2020 + (i % 5)));
            movie.setPosterUrl("https://via.placeholder.com/300x450/0066cc/ffffff?text=Movie" + i);
            movie.setSubtitle(movie.getYear() + " · " + movie.getGenre());
            mediaList.add(movie);
        }
    }
    
    private void generateTVData() {
        for (int i = 1; i <= 10; i++) {
            MediaItem tv = new MediaItem();
            tv.setId("tv_" + i);
            tv.setTitle("电视剧 " + i);
            tv.setType("tv");
            tv.setGenre("剧情, 悬疑");
            tv.setRating(8.5f + (i % 2) * 0.3f);
            tv.setYear(String.valueOf(2021 + (i % 3)));
            tv.setPosterUrl("https://via.placeholder.com/300x450/cc6600/ffffff?text=TV" + i);
            tv.setSubtitle(tv.getYear() + " · " + tv.getGenre());
            tv.setTotalEpisodes(20 + (i % 10));
            tv.setWatchedEpisodes(i % 15);
            mediaList.add(tv);
        }
    }
    
    private void generateAnimeData() {
        for (int i = 1; i <= 8; i++) {
            MediaItem anime = new MediaItem();
            anime.setId("anime_" + i);
            anime.setTitle("动漫 " + i);
            anime.setType("anime");
            anime.setGenre("动画, 奇幻");
            anime.setRating(9.0f + (i % 2) * 0.2f);
            anime.setYear(String.valueOf(2022 + (i % 2)));
            anime.setPosterUrl("https://via.placeholder.com/300x450/cc0066/ffffff?text=Anime" + i);
            anime.setSubtitle(anime.getYear() + " · " + anime.getGenre());
            anime.setTotalEpisodes(12 + (i % 12));
            anime.setWatchedEpisodes(i % 8);
            mediaList.add(anime);
        }
    }
    
    private void generateDefaultData() {
        MediaItem defaultItem = new MediaItem();
        defaultItem.setId("default_1");
        defaultItem.setTitle("默认媒体");
        defaultItem.setType("default");
        defaultItem.setGenre("未知");
        defaultItem.setRating(7.0f);
        defaultItem.setYear("2023");
        defaultItem.setPosterUrl("https://via.placeholder.com/300x450/666666/ffffff?text=Default");
        defaultItem.setSubtitle(defaultItem.getYear() + " · " + defaultItem.getGenre());
        mediaList.add(defaultItem);
    }
    
    /**
     * 🔄 更新UI
     */
    private void updateUI() {
        Log.d(TAG, "🔄 更新UI显示");
        
        if (mediaList.isEmpty()) {
            // 显示空状态
            videoRecyclerView.setVisibility(android.view.View.GONE);
            emptyStateText.setVisibility(android.view.View.VISIBLE);
            emptyStateText.setText("暂无" + categoryName + "内容");
        } else {
            // 显示数据列表
            videoRecyclerView.setVisibility(android.view.View.VISIBLE);
            emptyStateText.setVisibility(android.view.View.GONE);
            
            // 🔗 设置适配器
            if (mediaAdapter == null) {
                mediaAdapter = new SimpleMediaAdapter(this, mediaList);
                mediaAdapter.setOnItemClickListener(this);
                videoRecyclerView.setAdapter(mediaAdapter);
            } else {
                mediaAdapter.updateData(mediaList);
            }
            
            Toast.makeText(this, categoryName + " 列表加载完成，共 " + mediaList.size() + " 个项目", Toast.LENGTH_SHORT).show();
        }
        
        Log.d(TAG, "✅ UI更新完成");
    }
    
    /**
     * 📱 媒体项目点击事件
     */
    @Override
    public void onItemClick(MediaItem mediaItem, int position) {
        Log.d(TAG, "📱 用户点击媒体项目: " + mediaItem.getTitle() + " (位置: " + position + ")");
        
        // 导航到视频播放器
        Toast.makeText(this, "正在启动播放器: " + mediaItem.getTitle(), Toast.LENGTH_SHORT).show();
        
        // 启动VideoPlayerActivity
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("media_id", mediaItem.getId());
        intent.putExtra("media_title", mediaItem.getTitle());
        intent.putExtra("media_type", mediaItem.getType());
        intent.putExtra("media_year", mediaItem.getYear());
        intent.putExtra("media_genre", mediaItem.getGenre());
        intent.putExtra("poster_url", mediaItem.getPosterUrl());
        startActivity(intent);
    }
    
    @Override
    public void onBackPressed() {
        Log.d(TAG, "⬅️ 用户按下返回键");
        super.onBackPressed();
        finish();
    }
}
