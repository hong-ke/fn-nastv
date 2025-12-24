package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mynas.nastv.R;
import com.mynas.nastv.adapter.FavoriteAdapter;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.FavoriteListResponse;

/**
 * ⭐ Favorite Activity
 * 显示收藏列表页面
 * Web端URL格式: /v/favorite
 */
public class FavoriteActivity extends AppCompatActivity {
    private static final String TAG = "FavoriteActivity";
    private static final int PAGE_SIZE = 20;
    
    // UI
    private TextView totalCount;
    private TextView tabAll;
    private TextView tabMovie;
    private TextView tabTv;
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private RecyclerView recyclerView;
    
    // Data
    private MediaManager mediaManager;
    private FavoriteAdapter adapter;
    private String currentType = ""; // 空字符串表示全部
    private int currentPage = 1;
    private int totalItems = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);
        
        initViews();
        mediaManager = new MediaManager(this);
        loadFavorites(true);
    }
    
    private void initViews() {
        totalCount = findViewById(R.id.total_count);
        tabAll = findViewById(R.id.tab_all);
        tabMovie = findViewById(R.id.tab_movie);
        tabTv = findViewById(R.id.tab_tv);
        loadingProgress = findViewById(R.id.loading_progress);
        emptyText = findViewById(R.id.empty_text);
        recyclerView = findViewById(R.id.favorite_recycler_view);
        
        // 设置 RecyclerView
        adapter = new FavoriteAdapter(this::onItemClick);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        recyclerView.setAdapter(adapter);
        
        // 滚动加载更多
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isLoading && hasMore) {
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int lastVisible = layoutManager.findLastVisibleItemPosition();
                        int total = layoutManager.getItemCount();
                        if (lastVisible >= total - 5) {
                            loadMoreFavorites();
                        }
                    }
                }
            }
        });
        
        // 标签点击事件
        tabAll.setOnClickListener(v -> switchTab(""));
        tabMovie.setOnClickListener(v -> switchTab("movie"));
        tabTv.setOnClickListener(v -> switchTab("tv"));
        
        // 初始焦点
        tabAll.requestFocus();
    }
    
    private void switchTab(String type) {
        if (type.equals(currentType)) return;
        
        currentType = type;
        updateTabStyles();
        loadFavorites(true);
    }
    
    private void updateTabStyles() {
        // 重置所有标签样式
        tabAll.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabAll.setBackgroundResource(0);
        tabMovie.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabMovie.setBackgroundResource(0);
        tabTv.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabTv.setBackgroundResource(0);
        
        // 高亮当前标签
        TextView activeTab;
        if ("movie".equals(currentType)) {
            activeTab = tabMovie;
        } else if ("tv".equals(currentType)) {
            activeTab = tabTv;
        } else {
            activeTab = tabAll;
        }
        activeTab.setTextColor(getResources().getColor(android.R.color.white));
        activeTab.setBackgroundResource(R.drawable.rating_badge_background);
    }
    
    private void loadFavorites(boolean refresh) {
        if (isLoading) return;
        
        if (refresh) {
            currentPage = 1;
            hasMore = true;
            adapter.updateItems(null);
            showLoading();
        }
        
        isLoading = true;
        
        mediaManager.getFavoriteList(currentType, currentPage, PAGE_SIZE, new MediaManager.MediaCallback<FavoriteListResponse>() {
            @Override
            public void onSuccess(FavoriteListResponse response) {
                isLoading = false;
                runOnUiThread(() -> {
                    hideLoading();
                    
                    if (response != null) {
                        totalItems = response.getTotal();
                        totalCount.setText("共 " + totalItems + " 项");
                        
                        if (response.getList() != null && !response.getList().isEmpty()) {
                            if (refresh) {
                                adapter.updateItems(response.getList());
                            } else {
                                adapter.appendItems(response.getList());
                            }
                            hasMore = response.getList().size() >= PAGE_SIZE;
                            showContent();
                        } else if (refresh) {
                            showEmpty();
                        }
                    } else if (refresh) {
                        showEmpty();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                isLoading = false;
                Log.e(TAG, "Failed to load favorites: " + error);
                runOnUiThread(() -> {
                    hideLoading();
                    if (currentPage == 1) {
                        showEmpty();
                    }
                    Toast.makeText(FavoriteActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void loadMoreFavorites() {
        currentPage++;
        loadFavorites(false);
    }
    
    private void onItemClick(FavoriteListResponse.FavoriteItem item, int position) {
        String type = item.getType();
        String guid = item.getGuid();
        
        Intent intent;
        if ("movie".equalsIgnoreCase(type)) {
            intent = new Intent(this, MediaDetailActivity.class);
            intent.putExtra("media_guid", guid);
            intent.putExtra("media_type", "movie");
        } else if ("tv".equalsIgnoreCase(type)) {
            intent = new Intent(this, MediaDetailActivity.class);
            intent.putExtra("media_guid", guid);
            intent.putExtra("media_type", "tv");
        } else if ("episode".equalsIgnoreCase(type)) {
            intent = new Intent(this, EpisodeDetailActivity.class);
            intent.putExtra(EpisodeDetailActivity.EXTRA_EPISODE_GUID, guid);
            intent.putExtra(EpisodeDetailActivity.EXTRA_TV_TITLE, item.getTvTitle());
        } else {
            // 默认跳转到媒体详情
            intent = new Intent(this, MediaDetailActivity.class);
            intent.putExtra("media_guid", guid);
        }
        
        startActivity(intent);
    }
    
    private void showLoading() {
        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        loadingProgress.setVisibility(View.GONE);
    }
    
    private void showContent() {
        recyclerView.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
    }
    
    private void showEmpty() {
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }
}
