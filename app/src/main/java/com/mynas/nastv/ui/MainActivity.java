package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mynas.nastv.R;
import com.mynas.nastv.adapter.ContinueWatchingAdapter;
import com.mynas.nastv.adapter.MediaLibraryAdapter;
import com.mynas.nastv.adapter.MediaLibraryContentAdapter;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaItem;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主页Activity - 增强版本
 * 功能：用户信息显示、基础导航、媒体分类预览
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    // UI组件
    
    // 导航组件
    private TextView navHome;
    
    // 媒体库列表
    private RecyclerView mediaLibraryList;
    private ScrollView mainScrollView;  // [新增] 主内容ScrollView
    private MediaLibraryAdapter mediaLibraryAdapter;
    
    // 继续观看列表
    private TextView continueWatchingTitle;
    private RecyclerView continueWatchingList;
    private ContinueWatchingAdapter continueWatchingAdapter;
    
    // 媒体库内容容器
    private LinearLayout mediaContentContainer;
    
    // 数据管理器
    private MediaManager mediaManager;
    
    // UI状态管理
    private boolean isShowingLibraryContent = false;  // 是否正在显示单个媒体库的完整内容
    private MediaManager.MediaDbItem currentSelectedLibrary = null;  // 当前选中的媒体库
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "MainActivity启动");
        
        // 检查登录状态
        if (!SharedPreferencesManager.isLoggedIn()) {
            Log.d(TAG, "用户未登录，跳转到登录页");
            navigateToLogin();
            return;
        }
        
        // 恢复FnOS服务器URL，确保API服务可用
        String fnOSUrl = SharedPreferencesManager.getFnOSServerUrl();
        if (fnOSUrl != null && !fnOSUrl.isEmpty()) {
            Log.d(TAG, "恢复FnOS服务器地址: " + fnOSUrl);
            ApiClient.setFnOSBaseUrl(fnOSUrl);
        } else {
            Log.w(TAG, "FnOS服务器地址未设置，正在获取...");
            // 主动获取FnOS服务器地址
            fetchFnOSServerUrl();
        }
        
        // 设置简单布局
        setContentView(createSimpleLayout());
        
        // 显示欢迎信息
        initializeViews();
        
        Log.d(TAG, "MainActivity初始化完成");
    }
    
    /**
     * 创建简单的布局
     */
    private int createSimpleLayout() {
        // 暂时返回activity_main，如果不存在会在编译时报错
        // 我们稍后会创建一个简单的布局
        return R.layout.activity_main;
    }
    
    /**
     * 初始化视图
     */
    private void initializeViews() {
        
        // 绑定UI组件
        navHome = findViewById(R.id.nav_home);
        mediaLibraryList = findViewById(R.id.media_library_list);
        continueWatchingTitle = findViewById(R.id.continue_watching_title);
        continueWatchingList = findViewById(R.id.continue_watching_list);
        mediaContentContainer = findViewById(R.id.media_content_container);
        mainScrollView = findViewById(R.id.main_scroll_view);
        
        // 新布局中的收藏容器
        View navFavoriteContainer = findViewById(R.id.nav_favorite_container);
        
        // 初始化数据管理器
        mediaManager = new MediaManager(this);
        
        // 设置媒体库列表
        setupMediaLibraryList();
        
        // 设置继续观看列表
        setupContinueWatchingList();
        
        // 设置事件监听器
        setupEventListeners();
        
        // 初始化导航状态
        setupNavigationState();
        
        // [新增] 配置ScrollView
        setupScrollView();
        
        // 加载媒体库数据
        loadMediaLibraries();
        
    }
    
    /**
     * 配置ScrollView滚动行为
     */
    private void setupScrollView() {
        if (mainScrollView != null) {
            Log.d(TAG, "配置主内容区域ScrollView");
            
            // 设置滚动监听
            mainScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                // Log.v(TAG, "滚动位置: Y=" + scrollY + " (旧Y=" + oldScrollY + ")");
            });
            
            // 确保可以接收焦点和按键事件
            mainScrollView.setFocusable(true);
            mainScrollView.setFocusableInTouchMode(true);
            
            Log.d(TAG, "ScrollView配置完成，支持上下滚动");
        } else {
            Log.e(TAG, "ScrollView未找到，滚动功能不可用");
        }
    }

    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 主页导航点击事件
        navHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "用户点击主页导航");
                if (isShowingLibraryContent) {
                    showHomeContent();
                } else {
                    Log.d(TAG, "已经在主页，无需操作");
                }
            }
        });
        
        // 主页导航焦点变化监听器（用于遥控器导航）
        navHome.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "首页焦点变化: hasFocus=" + hasFocus + ", isShowingLibraryContent=" + isShowingLibraryContent);
            if (hasFocus) {
                // 获得焦点时显示高亮
                navHome.setBackgroundColor(getColor(R.color.tv_accent));
                navHome.setTextColor(getColor(R.color.white));
            } else {
                // 失去焦点时，根据是否在主页决定背景
                if (!isShowingLibraryContent) {
                    // 在主页模式，显示选中状态（浅色背景）
                    navHome.setBackgroundResource(R.drawable.nav_item_selected_bg);
                    navHome.setTextColor(getColor(R.color.tv_accent));
                } else {
                    // 在媒体库模式，不显示选中状态
                    navHome.setBackground(null);
                    navHome.setTextColor(getColor(R.color.tv_text_secondary));
                }
            }
        });
        
        // 收藏导航点击事件
        View navFavoriteContainer = findViewById(R.id.nav_favorite_container);
        if (navFavoriteContainer != null) {
            navFavoriteContainer.setOnClickListener(v -> {
                Log.d(TAG, "用户点击收藏导航");
                Intent intent = new Intent(MainActivity.this, FavoriteActivity.class);
                startActivity(intent);
            });
            
            // 收藏导航焦点变化监听器
            navFavoriteContainer.setOnFocusChangeListener((v, hasFocus) -> {
                Log.d(TAG, "收藏焦点变化: hasFocus=" + hasFocus);
                if (hasFocus) {
                    navFavoriteContainer.setBackgroundColor(getColor(R.color.tv_accent));
                    // 同时更新首页的背景（确保首页不再显示蓝色）
                    if (!isShowingLibraryContent) {
                        navHome.setBackgroundResource(R.drawable.nav_item_selected_bg);
                        navHome.setTextColor(getColor(R.color.tv_accent));
                    } else {
                        navHome.setBackground(null);
                        navHome.setTextColor(getColor(R.color.tv_text_secondary));
                    }
                } else {
                    navFavoriteContainer.setBackground(null);
                }
            });
        }
        
        // 清理缓存按钮点击事件
        View navClearCache = findViewById(R.id.nav_clear_cache);
        if (navClearCache != null) {
            navClearCache.setOnClickListener(v -> {
                Log.d(TAG, "用户点击清理缓存");
                clearAllCache();
            });
            
            // 清理缓存按钮焦点变化监听器
            navClearCache.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    navClearCache.setBackgroundColor(getColor(R.color.tv_accent));
                } else {
                    navClearCache.setBackground(null);
                }
            });
        }
    }
    
    /**
     * 设置媒体库列表
     */
    private void setupMediaLibraryList() {
        mediaLibraryAdapter = new MediaLibraryAdapter(this::onLibraryClick);
        
        // 设置媒体库焦点监听器，当媒体库获得焦点时更新首页背景
        mediaLibraryAdapter.setOnFocusListener(hasFocus -> {
            if (hasFocus) {
                Log.d(TAG, "媒体库获得焦点，更新首页背景");
                // 媒体库获得焦点时，首页应该显示未选中状态（除非在主页模式）
                if (!isShowingLibraryContent) {
                    navHome.setBackgroundResource(R.drawable.nav_item_selected_bg);
                    navHome.setTextColor(getColor(R.color.tv_accent));
                } else {
                    navHome.setBackground(null);
                    navHome.setTextColor(getColor(R.color.tv_text_secondary));
                }
            }
        });
        
        mediaLibraryList.setLayoutManager(new LinearLayoutManager(this));
        mediaLibraryList.setAdapter(mediaLibraryAdapter);
    }
    
    /**
     * 设置继续观看列表
     */
    private void setupContinueWatchingList() {
        continueWatchingAdapter = new ContinueWatchingAdapter(this::onContinueWatchingItemClick);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        continueWatchingList.setLayoutManager(layoutManager);
        continueWatchingList.setAdapter(continueWatchingAdapter);
        
        // 加载示例的继续观看数据
        loadContinueWatchingData();
    }
    
    /**
     * 继续观看项目点击事件
     */
    private void onContinueWatchingItemClick(MediaItem item, int position) {
        Log.d(TAG, "用户点击继续观看项目: " + item.getTitle());
        // 跳转到媒体详情页
        navigateToMediaDetail(item);
    }
    
    /**
     * 加载继续观看数据
     */
    private void loadContinueWatchingData() {
        
        // 从API获取真实的观看记录
        mediaManager.getPlayList(new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> watchedItems) {
                runOnUiThread(() -> {
                    if (watchedItems.isEmpty()) {
                        // 隐藏继续观看标题和列表
                        continueWatchingTitle.setVisibility(View.GONE);
                        continueWatchingList.setVisibility(View.GONE);
                    } else {
                        // 显示继续观看标题和列表
                        continueWatchingTitle.setVisibility(View.VISIBLE);
                        continueWatchingList.setVisibility(View.VISIBLE);
                        continueWatchingAdapter.updateItems(watchedItems);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // 发生错误时也隐藏继续观看区域
                    continueWatchingTitle.setVisibility(View.GONE);
                    continueWatchingList.setVisibility(View.GONE);
                });
            }
        });
    }
    
    /**
     * 加载媒体库数据
     */
    private void loadMediaLibraries() {
        Log.d(TAG, "开始加载媒体库列表");
        
        mediaManager.getMediaDbList(new MediaManager.MediaCallback<List<MediaManager.MediaDbItem>>() {
            @Override
            public void onSuccess(List<MediaManager.MediaDbItem> libraries) {
                Log.d(TAG, "媒体库列表加载成功，共 " + libraries.size() + " 个");
                runOnUiThread(() -> {
                    mediaLibraryAdapter.updateLibraries(libraries);
                    if (!libraries.isEmpty()) {
                        // 先获取统计数据，再创建预览内容，确保数量显示正确
                        loadLibraryItemCountsAndPreview(libraries);
                        
                        // 不设置默认选中项，保持主页为纯粹的预览状态
                        // mediaLibraryAdapter.setSelectedPosition(0);  // 注释掉，避免混淆
                        // onLibraryClick(libraries.get(0), 0);  // 注释掉自动跳转
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "媒体库列表加载失败: " + error);
                runOnUiThread(() -> {
                    ToastUtils.show(MainActivity.this, "媒体库加载失败: " + error);
                });
            }
        });
    }
    
    /**
     * 获取媒体库统计数据并创建预览内容（优化版）
     */
    private void loadLibraryItemCountsAndPreview(List<MediaManager.MediaDbItem> libraries) {
        Log.d(TAG, "开始获取媒体库统计数据并创建预览");
        
        // 使用专门的统计API，一次性获取所有媒体库的项目数量
        mediaManager.getMediaDbSum(new MediaManager.MediaCallback<Map<String, Integer>>() {
            @Override
            public void onSuccess(Map<String, Integer> sumData) {
                Log.d(TAG, "媒体库统计数据获取成功: " + sumData);
                runOnUiThread(() -> {
                    // 更新每个媒体库的数量
                    for (MediaManager.MediaDbItem library : libraries) {
                        String guid = library.getGuid();
                        Integer count = sumData.get(guid);
                        if (count != null) {
                            library.setItemCount(count);
                            Log.d(TAG, "设置媒体库 " + library.getName() + " 数量: " + count);
                        } else {
                            library.setItemCount(0);
                            Log.w(TAG, "媒体库 " + library.getName() + " 在统计数据中未找到");
                        }
                    }
                    // 更新左侧媒体库列表
                    mediaLibraryAdapter.notifyDataSetChanged();
                    
                    // [修复] 统计数据获取完成后，再创建预览内容，确保标题显示正确数量
                    loadMediaLibrariesPreview(libraries);
                    
                    Log.d(TAG, "媒体库数量和预览内容加载完成");
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "获取媒体库统计数据失败: " + error);
                runOnUiThread(() -> {
                    // 发生错误时设置所有数量为0
                    for (MediaManager.MediaDbItem library : libraries) {
                        library.setItemCount(0);
                    }
                    mediaLibraryAdapter.notifyDataSetChanged();
                    
                    // 即使统计失败，也要创建预览内容
                    loadMediaLibrariesPreview(libraries);
                    
                    ToastUtils.show(MainActivity.this, "获取媒体库统计失败: " + error);
                });
            }
        });
    }

    /**
     * 媒体库点击事件
     */
    private void onLibraryClick(MediaManager.MediaDbItem library, int position) {
        Log.d(TAG, "用户点击媒体库: " + library.getName());
        
        // 在当前Activity显示媒体库完整内容，而不是跳转
        
        // 设置选中状态
        mediaLibraryAdapter.setSelectedPosition(position);
        currentSelectedLibrary = library;
        isShowingLibraryContent = true;
        
        // 显示该媒体库的完整内容
        showLibraryFullContent(library);
        
        // 更新导航状态 - 取消主页选中，选中媒体库
        updateNavigationState();
    }

    /**
     * 获取FnOS服务器地址
     */
    private void fetchFnOSServerUrl() {
        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(SharedPreferencesManager.getSystemApiUrl() + "/getFnUrl")
                        .build();
                
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String fnOSUrl = response.body().string().trim();
                    Log.d(TAG, "FnOS服务器地址获取成功: " + fnOSUrl);
                    
                    // 保存并设置FnOS URL
                    SharedPreferencesManager.saveFnOSServerUrl(fnOSUrl);
                    ApiClient.setFnOSBaseUrl(fnOSUrl);
                    
                    runOnUiThread(() -> {
                        Log.d(TAG, "FnOS API服务已初始化");
                    });
                } else {
                    Log.e(TAG, "FnOS服务器地址获取失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "FnOS服务器地址获取异常", e);
            }
        }).start();
    }
    
    /**
     * 跳转到登录页
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * 加载媒体库预览内容（主页用）
     */
    private void loadMediaLibrariesPreview(List<MediaManager.MediaDbItem> libraries) {
        Log.d(TAG, "开始加载媒体库预览内容（主页模式）");
        
        // 清空现有内容
        mediaContentContainer.removeAllViews();
        
        for (MediaManager.MediaDbItem library : libraries) {
            // 为每个媒体库创建预览区域（显示更多项目支持横向滑动）
            createMediaLibraryPreviewSection(library, 20);  // 增加到20个项目支持横向滑动
        }
    }

    /**
     * 为单个媒体库创建预览区域（主页用）
     */
    private void createMediaLibraryPreviewSection(MediaManager.MediaDbItem library, int previewCount) {
        Log.d(TAG, "创建媒体库预览区域: " + library.getName() + " (预览" + previewCount + "个)");
        
        // 创建整个区域的容器
        LinearLayout sectionLayout = new LinearLayout(this);
        sectionLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.tv_margin_xlarge));
        sectionLayout.setLayoutParams(sectionParams);
        
        // 创建可点击的标题（点击查看更多）
        TextView titleView = new TextView(this);
        titleView.setText(library.getName() + " (" + library.getItemCount() + ")");
        titleView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        titleView.setTextColor(getResources().getColor(R.color.tv_text_primary, null));
        titleView.setClickable(true);
        titleView.setFocusable(true);
        
        // 标题点击事件 - 在当前Activity显示媒体库完整内容
        titleView.setOnClickListener(v -> {
            Log.d(TAG, "点击标题，在当前Activity显示媒体库: " + library.getName());
            
            // 找到对应的媒体库在左侧列表中的位置
            List<MediaManager.MediaDbItem> libraries = mediaLibraryAdapter.getLibraries();
            for (int i = 0; i < libraries.size(); i++) {
                if (libraries.get(i).getGuid().equals(library.getGuid())) {
                    // 调用媒体库点击事件，在当前Activity显示内容
                    onLibraryClick(library, i);
                    break;
                }
            }
        });
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.tv_margin_medium));
        titleView.setLayoutParams(titleParams);
        sectionLayout.addView(titleView);
        
        // 创建水平滚动的RecyclerView（优化滚动体验）
        RecyclerView recyclerView = new RecyclerView(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        
        // [修复] 优化RecyclerView横向滚动配置
        recyclerView.setNestedScrollingEnabled(false);  // 避免嵌套滚动冲突
        recyclerView.setHorizontalScrollBarEnabled(false);  // 隐藏滚动条
        
        // 创建适配器
        MediaLibraryContentAdapter adapter = new MediaLibraryContentAdapter((mediaItem, position) -> {
            Log.d(TAG, "预览模式点击媒体项目: " + mediaItem.getTitle());
            // 跳转到媒体详情页
            navigateToMediaDetail(mediaItem);
        });
        recyclerView.setAdapter(adapter);
        
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        recyclerParams.setMargins(getResources().getDimensionPixelSize(R.dimen.tv_margin_small), 0, 
                                 getResources().getDimensionPixelSize(R.dimen.tv_margin_small), 0);
        recyclerView.setLayoutParams(recyclerParams);
        sectionLayout.addView(recyclerView);
        
        // 添加到主容器
        mediaContentContainer.addView(sectionLayout);
        
        // 异步加载该媒体库的预览内容（限制数量）
        mediaManager.getMediaLibraryItems(library.getGuid(), previewCount, new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> items) {
                Log.d(TAG, "媒体库 " + library.getName() + " 预览加载成功，共 " + items.size() + " 个项目");
                runOnUiThread(() -> {
                    adapter.updateItems(items);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "媒体库 " + library.getName() + " 预览加载失败: " + error);
                runOnUiThread(() -> {
                    // 显示空状态或错误信息
                    adapter.updateItems(new ArrayList<>());
                });
            }
        });
    }

    /**
     * 显示媒体库完整内容
     */
    private void showLibraryFullContent(MediaManager.MediaDbItem library) {
        Log.d(TAG, "显示媒体库完整内容: " + library.getName());
        
        // 隐藏继续观看区域
        continueWatchingTitle.setVisibility(View.GONE);
        continueWatchingList.setVisibility(View.GONE);
        
        // 清空现有内容
        mediaContentContainer.removeAllViews();
        
        // 创建媒体库完整内容视图
        createLibraryFullContentView(library);
    }
    
    /**
     * 创建媒体库完整内容视图
     */
    private void createLibraryFullContentView(MediaManager.MediaDbItem library) {
        Log.d(TAG, "创建媒体库完整内容视图: " + library.getName());
        
        // 创建标题栏（包含返回按钮）
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setPadding(getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 
                               getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 
                               getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 
                               getResources().getDimensionPixelSize(R.dimen.tv_margin_medium));
        
        // 返回按钮
        TextView backButton = new TextView(this);
        backButton.setText("← 返回主页");
        backButton.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        backButton.setTextColor(getResources().getColor(R.color.tv_text_secondary, null));
        backButton.setClickable(true);
        backButton.setFocusable(true);
        backButton.setPadding(getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 
                             getResources().getDimensionPixelSize(R.dimen.tv_margin_small), 
                             getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 
                             getResources().getDimensionPixelSize(R.dimen.tv_margin_small));
        
        backButton.setOnClickListener(v -> {
            Log.d(TAG, "用户点击返回主页");
            showHomeContent();
        });
        
        // 标题
        TextView titleView = new TextView(this);
        titleView.setText(library.getName() + " (" + library.getItemCount() + ")");
        titleView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        titleView.setTextColor(getResources().getColor(R.color.tv_text_primary, null));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        titleParams.setMargins(getResources().getDimensionPixelSize(R.dimen.tv_margin_medium), 0, 0, 0);
        titleView.setLayoutParams(titleParams);
        
        headerLayout.addView(backButton);
        headerLayout.addView(titleView);
        
        // 创建网格布局用于显示媒体项目
        RecyclerView gridRecyclerView = new RecyclerView(this);
        androidx.recyclerview.widget.GridLayoutManager gridLayoutManager = 
            new androidx.recyclerview.widget.GridLayoutManager(this, 4); // 4列网格
        gridRecyclerView.setLayoutManager(gridLayoutManager);
        
        // 创建适配器
        MediaLibraryContentAdapter adapter = new MediaLibraryContentAdapter((mediaItem, position) -> {
            Log.d(TAG, "完整内容模式点击媒体项目: " + mediaItem.getTitle());
            // 跳转到媒体详情页
            navigateToMediaDetail(mediaItem);
        });
        gridRecyclerView.setAdapter(adapter);
        
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        gridRecyclerView.setLayoutParams(recyclerParams);
        
        // 添加到容器
        mediaContentContainer.addView(headerLayout);
        mediaContentContainer.addView(gridRecyclerView);
        
        // 加载该媒体库的所有内容
        mediaManager.getMediaLibraryItems(library.getGuid(), 100, new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> items) {
                Log.d(TAG, "媒体库 " + library.getName() + " 完整内容加载成功，共 " + items.size() + " 个项目");
                runOnUiThread(() -> {
                    adapter.updateItems(items);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "媒体库 " + library.getName() + " 完整内容加载失败: " + error);
                runOnUiThread(() -> {
                    ToastUtils.show(MainActivity.this, "加载失败: " + error);
                    adapter.updateItems(new ArrayList<>());
                });
            }
        });
    }
    
    /**
     * 显示主页内容
     */
    private void showHomeContent() {
        Log.d(TAG, "显示主页内容");
        
        isShowingLibraryContent = false;
        currentSelectedLibrary = null;
        
        // 清除媒体库选中状态
        mediaLibraryAdapter.setSelectedPosition(-1);
        
        // 显示继续观看区域
        continueWatchingTitle.setVisibility(View.VISIBLE);
        continueWatchingList.setVisibility(View.VISIBLE);
        
        // 重新加载主页预览内容
        if (!mediaLibraryAdapter.getLibraries().isEmpty()) {
            loadMediaLibrariesPreview(mediaLibraryAdapter.getLibraries());
        }
        
        // [修复] 更新导航状态 - 选中主页，取消媒体库选中
        updateNavigationState();
    }
    
    /**
     * 跳转到详情页（根据类型智能导航）
     */
    private void navigateToMediaDetail(MediaItem mediaItem) {
        String itemType = mediaItem.getType();
        Log.d(TAG, "导航到详情页: " + mediaItem.getTitle() + 
                   " (GUID: " + mediaItem.getGuid() + 
                   ", Type: " + itemType + 
                   ", ParentGuid: " + mediaItem.getParentGuid() + 
                   ", AncestorGuid: " + mediaItem.getAncestorGuid() + ")");
        
        // 根据类型决定导航目标
        if ("Episode".equalsIgnoreCase(itemType)) {
            // Episode类型：来自继续观看，直接播放
            Log.d(TAG, "Episode类型，直接播放");
            playEpisodeDirectly(mediaItem);
        } else if ("Movie".equalsIgnoreCase(itemType)) {
            // Movie类型：跳转到电影详情页
            Log.d(TAG, "Movie类型，跳转到详情页");
            Intent intent = new Intent(this, MediaDetailActivity.class);
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_GUID, mediaItem.getGuid());
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TITLE, mediaItem.getTitle());
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TYPE, mediaItem.getType());
            startActivity(intent);
        } else {
            // TV/其他类型：跳转到电视剧详情页
            Log.d(TAG, "TV/其他类型，跳转到详情页");
            Intent intent = new Intent(this, MediaDetailActivity.class);
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_GUID, mediaItem.getGuid());
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TITLE, mediaItem.getTitle());
            intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TYPE, mediaItem.getType());
            startActivity(intent);
        }
    }
    
    /**
     * 直接播放Episode（用于继续观看）
     * 直接使用 Web 端的 URL 格式，不依赖 stream API
     */
    private void playEpisodeDirectly(MediaItem mediaItem) {
        ToastUtils.show(this, "正在加载 " + mediaItem.getTitle() + "...");
        
        // 如果已有mediaGuid，直接构建播放URL（与Web端一致）
        String mediaGuid = mediaItem.getMediaGuid();
        if (mediaGuid != null && !mediaGuid.isEmpty()) {
            Log.d(TAG, "使用缓存的mediaGuid构建播放URL: " + mediaGuid);
            // 直接使用 Web 端的 URL 格式
            String baseUrl = SharedPreferencesManager.getServerBaseUrl();
            String playUrl = baseUrl + "/v/api/v1/media/range/" + mediaGuid + "?direct_link_quality_index=0";
            Log.d(TAG, "使用媒体URL: " + playUrl);
            navigateToVideoPlayer(playUrl, mediaItem, mediaGuid, mediaItem.getVideoGuid(), mediaItem.getTs());
        } else {
            // 否则调用API获取播放信息
            Log.d(TAG, "调用API获取播放信息");
            mediaManager.startPlayWithInfo(mediaItem.getGuid(), new MediaManager.MediaCallback<com.mynas.nastv.model.PlayStartInfo>() {
                @Override
                public void onSuccess(com.mynas.nastv.model.PlayStartInfo playInfo) {
                    runOnUiThread(() -> navigateToVideoPlayer(playInfo.getPlayUrl(), mediaItem, 
                        playInfo.getMediaGuid(), playInfo.getVideoGuid(), playInfo.getResumePositionSeconds()));
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "播放失败: " + error);
                        ToastUtils.show(MainActivity.this, "播放失败: " + error);
                    });
                }
            });
        }
    }
    
    /**
     * 跳转到视频播放器
     */
    private void navigateToVideoPlayer(String playUrl, MediaItem mediaItem, String mediaGuid, String videoGuid, long resumePosition) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", playUrl);
        intent.putExtra("video_title", mediaItem.getTitle());
        intent.putExtra("episode_guid", mediaItem.getGuid());
        
        // 传递播放进度上报所需的信息
        intent.putExtra("media_guid", mediaGuid);
        intent.putExtra("video_guid", videoGuid);
        intent.putExtra("resume_position", resumePosition);
        
        // 传递关联信息
        if (mediaItem.getParentGuid() != null) {
            intent.putExtra("season_guid", mediaItem.getParentGuid());
        }
        if (mediaItem.getAncestorGuid() != null) {
            intent.putExtra("tv_guid", mediaItem.getAncestorGuid());
        }
        
        // 传递弹幕相关信息
        if (mediaItem.getDoubanId() > 0) {
            intent.putExtra("douban_id", String.valueOf(mediaItem.getDoubanId()));
        }
        intent.putExtra("season_number", mediaItem.getSeasonNumber());
        intent.putExtra("episode_number", mediaItem.getEpisodeNumber());
        
        // 传递电视剧标题（用于弹幕搜索）
        if (mediaItem.getTvTitle() != null && !mediaItem.getTvTitle().isEmpty()) {
            intent.putExtra("tv_title", mediaItem.getTvTitle());
        } else {
            intent.putExtra("tv_title", mediaItem.getTitle());
        }
        
        startActivity(intent);
    }
    
    /**
     * 设置导航状态
     */
    private void setupNavigationState() {
        Log.d(TAG, "初始化导航状态");
        
        // 初始状态：选中主页
        setHomeNavigationSelected(true);
    }
    
    /**
     * 更新导航状态
     */
    private void updateNavigationState() {
        if (isShowingLibraryContent) {
            // 显示媒体库内容：取消主页选中
            setHomeNavigationSelected(false);
            Log.d(TAG, "导航状态更新：媒体库模式");
        } else {
            // 显示主页内容：选中主页
            setHomeNavigationSelected(true);
            Log.d(TAG, "导航状态更新：主页模式");
        }
    }
    
    /**
     * 设置主页导航选中状态
     */
    private void setHomeNavigationSelected(boolean isSelected) {
        if (navHome != null) {
            // 保存选中状态
            navHome.setTag(R.id.nav_home, isSelected);
            
            // 只有在没有焦点时才更新背景（焦点状态由 OnFocusChangeListener 处理）
            if (!navHome.hasFocus()) {
                if (isSelected) {
                    navHome.setBackgroundResource(R.drawable.nav_item_selected_bg);
                    navHome.setTextColor(getColor(R.color.tv_accent));
                    Log.d(TAG, "主页导航：已选中");
                } else {
                    navHome.setBackground(null);
                    navHome.setTextColor(getColor(R.color.tv_text_secondary));
                    Log.d(TAG, "主页导航：取消选中");
                }
            }
        }
    }

    // 双击返回键退出相关
    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000; // 2秒内双击退出

    /**
     * 处理按键事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // 返回键逻辑
                if (isShowingLibraryContent) {
                    // 如果正在显示媒体库内容，返回主页
                    showHomeContent();
                    return true;
                } else {
                    // 如果在主页，双击退出应用
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
                        // 双击，退出应用
                        finish();
                    } else {
                        // 第一次按返回键，提示再按一次退出
                        lastBackPressTime = currentTime;
                        ToastUtils.show(this, "再按一次返回键退出");
                    }
                    return true;
                }
            case KeyEvent.KEYCODE_MENU:
                // 菜单键 - 显示设置菜单
                showSettingsMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 左键 - 只有当焦点在主内容区域的最左边项目时，才移动到侧边栏
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    // 检查当前焦点是否在主内容区域
                    if (isViewInMainContent(currentFocus)) {
                        // 检查是否在RecyclerView的第一个项目
                        RecyclerView parentRecyclerView = findParentRecyclerView(currentFocus);
                        if (parentRecyclerView != null) {
                            int position = parentRecyclerView.getChildAdapterPosition(currentFocus);
                            // 只有在第一个位置时才跳到侧边栏
                            if (position == 0) {
                                if (navHome != null) {
                                    navHome.requestFocus();
                                    return true;
                                }
                            }
                            // 否则让系统处理左移
                        } else {
                            // 不在RecyclerView中，直接跳到侧边栏
                            if (navHome != null) {
                                navHome.requestFocus();
                                return true;
                            }
                        }
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 右键 - 如果当前焦点在侧边栏，移动到主内容区域
                currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    if (isViewInSidebar(currentFocus)) {
                        // 将焦点移动到主内容区域的第一个可聚焦项
                        View firstFocusable = findFirstFocusableInMainContent();
                        if (firstFocusable != null) {
                            firstFocusable.requestFocus();
                            return true;
                        }
                    }
                }
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * 检查视图是否在主内容区域
     */
    private boolean isViewInMainContent(View view) {
        if (mainScrollView == null) return false;
        View parent = view;
        while (parent != null) {
            if (parent == mainScrollView) return true;
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return false;
    }
    
    /**
     * 检查视图是否在侧边栏
     */
    private boolean isViewInSidebar(View view) {
        if (navHome == null) return false;
        View sidebar = (View) navHome.getParent();
        View parent = view;
        while (parent != null) {
            if (parent == sidebar) return true;
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return false;
    }
    
    /**
     * 在主内容区域找到第一个可聚焦的视图
     */
    private View findFirstFocusableInMainContent() {
        if (continueWatchingList != null && continueWatchingList.getVisibility() == View.VISIBLE) {
            if (continueWatchingList.getChildCount() > 0) {
                return continueWatchingList.getChildAt(0);
            }
        }
        if (mediaContentContainer != null && mediaContentContainer.getChildCount() > 0) {
            return findFirstFocusableInViewGroup(mediaContentContainer);
        }
        return null;
    }
    
    /**
     * 查找视图的父RecyclerView
     */
    private RecyclerView findParentRecyclerView(View view) {
        View parent = view;
        while (parent != null) {
            if (parent.getParent() instanceof RecyclerView) {
                return (RecyclerView) parent.getParent();
            }
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return null;
    }
    
    /**
     * 递归查找第一个可聚焦的视图
     */
    private View findFirstFocusableInViewGroup(android.view.ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                return child;
            }
            if (child instanceof android.view.ViewGroup) {
                View found = findFirstFocusableInViewGroup((android.view.ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * 显示设置菜单
     */
    private void showSettingsMenu() {
        String[] options = {"清空缓存", "退出登录"};
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        // 清空缓存
                        clearVideoCache();
                        break;
                    case 1:
                        // 退出登录
                        logout();
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 清空视频缓存
     */
    private void clearVideoCache() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("清空缓存")
            .setMessage("确定要清空所有视频缓存吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                try {
                    // 清空视频缓存目录
                    java.io.File videoCacheDir = new java.io.File(getCacheDir(), "video_cache");
                    if (videoCacheDir.exists()) {
                        deleteDirectory(videoCacheDir);
                    }
                    
                    ToastUtils.show(this, "缓存已清空");
                    Log.d(TAG, "视频缓存已清空");
                } catch (Exception e) {
                    Log.e(TAG, "清空缓存失败", e);
                    ToastUtils.show(this, "清空缓存失败: " + e.getMessage());
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 清理所有缓存（左侧菜单按钮）
     */
    private void clearAllCache() {
        // 计算缓存大小
        long totalSize = 0;
        
        java.io.File gsyCacheDir = new java.io.File(getCacheDir(), "gsy_video_cache");
        if (gsyCacheDir.exists()) {
            totalSize += getDirectorySize(gsyCacheDir);
        }
        
        java.io.File okhttpCacheDir = new java.io.File(getCacheDir(), "okhttp_video_cache");
        if (okhttpCacheDir.exists()) {
            totalSize += getDirectorySize(okhttpCacheDir);
        }
        
        String sizeStr = formatFileSize(totalSize);
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("清理缓存")
            .setMessage("当前缓存大小: " + sizeStr + "\n确定要清理所有缓存吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                try {
                    long freedSize = 0;
                    
                    // 清空 GSY 的缓存目录
                    if (gsyCacheDir.exists()) {
                        freedSize += getDirectorySize(gsyCacheDir);
                        deleteDirectory(gsyCacheDir);
                    }
                    
                    // 清空 okhttp_video_cache 目录
                    if (okhttpCacheDir.exists()) {
                        freedSize += getDirectorySize(okhttpCacheDir);
                        deleteDirectory(okhttpCacheDir);
                    }
                    
                    ToastUtils.show(this, "已清理 " + formatFileSize(freedSize));
                    Log.d(TAG, "缓存已清理: " + formatFileSize(freedSize));
                } catch (Exception e) {
                    Log.e(TAG, "清理缓存失败", e);
                    ToastUtils.show(this, "清理缓存失败");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 获取目录大小
     */
    private long getDirectorySize(java.io.File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else {
                        size += getDirectorySize(file);
                    }
                }
            }
        }
        return size;
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 递归删除目录
     */
    private boolean deleteDirectory(java.io.File dir) {
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return dir.delete();
    }
    
    /**
     * 退出登录
     */
    private void logout() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                SharedPreferencesManager.clearAuthInfo();
                navigateToLogin();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}