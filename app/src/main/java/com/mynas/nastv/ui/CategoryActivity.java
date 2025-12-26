package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;

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
import com.mynas.nastv.adapter.MediaLibraryContentAdapter;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaItem;

import java.util.List;

/**
 * Category Activity
 * 显示分类列表页面
 * Web端URL格式: /v/list/{type}
 */
public class CategoryActivity extends AppCompatActivity {
    private static final String TAG = "CategoryActivity";
    private static final int PAGE_SIZE = 20;
    
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_LIBRARY_GUID = "library_guid";
    public static final String EXTRA_TITLE = "title";
    
    // UI
    private TextView pageTitle;
    private TextView totalCount;
    private TextView tabAll;
    private TextView tabTv;
    private TextView tabMovie;
    private TextView tabOther;
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private RecyclerView recyclerView;
    
    // Data
    private MediaManager mediaManager;
    private MediaLibraryContentAdapter adapter;
    private String currentType = "all"; // all, tv, movie, other
    private String libraryGuid; // 可选，用于媒体库筛选
    private int currentPage = 1;
    private int totalItems = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        
        Intent intent = getIntent();
        currentType = intent.getStringExtra(EXTRA_TYPE);
        if (currentType == null) currentType = "all";
        libraryGuid = intent.getStringExtra(EXTRA_LIBRARY_GUID);
        String title = intent.getStringExtra(EXTRA_TITLE);
        
        initViews();
        
        if (title != null && !title.isEmpty()) {
            pageTitle.setText(title);
        }
        
        mediaManager = new MediaManager(this);
        updateTabStyles();
        loadItems(true);
    }
    
    private void initViews() {
        pageTitle = findViewById(R.id.page_title);
        totalCount = findViewById(R.id.total_count);
        tabAll = findViewById(R.id.tab_all);
        tabTv = findViewById(R.id.tab_tv);
        tabMovie = findViewById(R.id.tab_movie);
        tabOther = findViewById(R.id.tab_other);
        loadingProgress = findViewById(R.id.loading_progress);
        emptyText = findViewById(R.id.empty_text);
        recyclerView = findViewById(R.id.category_recycler_view);
        
        // 设置 RecyclerView
        adapter = new MediaLibraryContentAdapter(this::onItemClick);
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
                            loadMoreItems();
                        }
                    }
                }
            }
        });
        
        // 标签点击事件
        tabAll.setOnClickListener(v -> switchTab("all"));
        tabTv.setOnClickListener(v -> switchTab("tv"));
        tabMovie.setOnClickListener(v -> switchTab("movie"));
        tabOther.setOnClickListener(v -> switchTab("other"));
        
        // 初始焦点
        tabAll.requestFocus();
    }
    
    private void switchTab(String type) {
        if (type.equals(currentType)) return;
        
        currentType = type;
        updateTabStyles();
        loadItems(true);
    }
    
    private void updateTabStyles() {
        // 重置所有标签样式
        tabAll.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabAll.setBackgroundResource(0);
        tabTv.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabTv.setBackgroundResource(0);
        tabMovie.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabMovie.setBackgroundResource(0);
        tabOther.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tabOther.setBackgroundResource(0);
        
        // 高亮当前标签
        TextView activeTab;
        switch (currentType) {
            case "tv":
                activeTab = tabTv;
                break;
            case "movie":
                activeTab = tabMovie;
                break;
            case "other":
                activeTab = tabOther;
                break;
            default:
                activeTab = tabAll;
                break;
        }
        activeTab.setTextColor(getResources().getColor(android.R.color.white));
        activeTab.setBackgroundResource(R.drawable.rating_badge_background);
    }
    
    private void loadItems(boolean refresh) {
        if (isLoading) return;
        
        if (refresh) {
            currentPage = 1;
            hasMore = true;
            adapter.updateItems(null);
            showLoading();
        }
        
        isLoading = true;
        
        // 根据类型获取媒体列表
        String typeFilter = "all".equals(currentType) ? null : currentType;
        
        mediaManager.getMediaList(libraryGuid, typeFilter, currentPage, PAGE_SIZE, 
            new MediaManager.MediaCallback<List<MediaItem>>() {
                @Override
                public void onSuccess(List<MediaItem> items) {
                    isLoading = false;
                    runOnUiThread(() -> {
                        hideLoading();
                        
                        if (items != null && !items.isEmpty()) {
                            if (refresh) {
                                adapter.updateItems(items);
                            } else {
                                adapter.appendItems(items);
                            }
                            hasMore = items.size() >= PAGE_SIZE;
                            showContent();
                            
                            // 更新总数（如果是第一页）
                            if (refresh) {
                                totalCount.setText("共 " + adapter.getItemCount() + "+ 项");
                            }
                        } else if (refresh) {
                            showEmpty();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    isLoading = false;
                    Log.e(TAG, "Failed to load items: " + error);
                    runOnUiThread(() -> {
                        hideLoading();
                        if (currentPage == 1) {
                            showEmpty();
                        }
                        ToastUtils.show(CategoryActivity.this, "加载失败: " + error);
                    });
                }
            });
    }
    
    private void loadMoreItems() {
        currentPage++;
        loadItems(false);
    }
    
    private void onItemClick(MediaItem item, int position) {
        Intent intent = new Intent(this, MediaDetailActivity.class);
        intent.putExtra("media_guid", item.getGuid());
        intent.putExtra("media_type", item.getType());
        intent.putExtra("media_title", item.getTitle());
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
