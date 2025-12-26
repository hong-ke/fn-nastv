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
        Log.d(TAG, "[调试] 开始初始化视图组件");
        
        // 绑定UI组件
        Log.d(TAG, "[调试] 绑定UI组件");
        navHome = findViewById(R.id.nav_home);
        mediaLibraryList = findViewById(R.id.media_library_list);
        continueWatchingTitle = findViewById(R.id.continue_watching_title);
        continueWatchingList = findViewById(R.id.continue_watching_list);
        mediaContentContainer = findViewById(R.id.media_content_container);
        mainScrollView = findViewById(R.id.main_scroll_view);
        
        // 新布局中的收藏容器
        View navFavoriteContainer = findViewById(R.id.nav_favorite_container);
        
        // 初始化数据管理器
        Log.d(TAG, "[调试] 初始化数据管理器");
        mediaManager = new MediaManager(this);
        
        // 设置媒体库列表
        Log.d(TAG, "[调试] 设置媒体库列表");
        setupMediaLibraryList();
        
        // 设置继续观看列表
        Log.d(TAG, "[调试] 设置继续观看列表");
        setupContinueWatchingList();
        
        // 设置事件监听器
        Log.d(TAG, "[调试] 设置事件监听器");
        setupEventListeners();
        
        // 初始化导航状态
        Log.d(TAG, "[调试] 初始化导航状态");
        setupNavigationState();
        
        // [新增] 配置ScrollView
        setupScrollView();
        
        // 加载媒体库数据
        Log.d(TAG, "[调试] 加载媒体库数据");
        loadMediaLibraries();
        
        Log.d(TAG, "[调试] 视图组件初始化完成");
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
     * 设置用户信息显示 - 新布局中已移除
     */
    private void setupUserInfo() {
        // 新布局中没有用户信息显示区域
        Log.d(TAG, "新布局中已移除用户信息显示");
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
        
        // 收藏导航点击事件
        View navFavoriteContainer = findViewById(R.id.nav_favorite_container);
        if (navFavoriteContainer != null) {
            navFavoriteContainer.setOnClickListener(v -> {
                Log.d(TAG, "用户点击收藏导航");
                Intent intent = new Intent(MainActivity.this, FavoriteActivity.class);
                startActivity(intent);
            });
        }
    }
    
    /**
     * 设置媒体库列表
     */
    private void setupMediaLibraryList() {
        mediaLibraryAdapter = new MediaLibraryAdapter(this::onLibraryClick);
        mediaLibraryList.setLayoutManager(new LinearLayoutManager(this));
        mediaLibraryList.setAdapter(mediaLibraryAdapter);
    }
    
    /**
     * 设置继续观看列表
     */
    private void setupContinueWatchingList() {
        Log.d(TAG, "[调试] 开始设置继续观看列表");
        continueWatchingAdapter = new ContinueWatchingAdapter(this::onContinueWatchingItemClick);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        continueWatchingList.setLayoutManager(layoutManager);
        continueWatchingList.setAdapter(continueWatchingAdapter);
        
        Log.d(TAG, "[调试] 继续观看列表设置完成，开始加载数据");
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
        Log.d(TAG, "[调试] 开始加载继续观看数据");
        
        // 从API获取真实的观看记录
        Log.d(TAG, "[调试] 调用MediaManager.getPlayList()");
        mediaManager.getPlayList(new MediaManager.MediaCallback<List<MediaItem>>() {
            @Override
            public void onSuccess(List<MediaItem> watchedItems) {
                Log.d(TAG, "[调试] 继续观看API调用成功，返回 " + watchedItems.size() + " 项");
                runOnUiThread(() -> {
                    if (watchedItems.isEmpty()) {
                        Log.d(TAG, "[调试] 暂无继续观看记录，隐藏继续观看区域");
                        // 隐藏继续观看标题和列表
                        continueWatchingTitle.setVisibility(View.GONE);
                        continueWatchingList.setVisibility(View.GONE);
                    } else {
                        Log.d(TAG, "[调试] 继续观看数据加载完成，共 " + watchedItems.size() + " 项，显示UI");
                        // 显示继续观看标题和列表
                        continueWatchingTitle.setVisibility(View.VISIBLE);
                        continueWatchingList.setVisibility(View.VISIBLE);
                        continueWatchingAdapter.updateItems(watchedItems);
                        Log.d(TAG, "[调试] 继续观看UI更新完成");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[调试] 继续观看数据加载失败: " + error);
                runOnUiThread(() -> {
                    // 发生错误时也隐藏继续观看区域
                    Log.d(TAG, "[调试] 隐藏继续观看区域（错误处理）");
                    continueWatchingTitle.setVisibility(View.GONE);
                    continueWatchingList.setVisibility(View.GONE);
                });
            }
        });
        Log.d(TAG, "[调试] getPlayList()调用已发起");
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
                        // [修复] 先获取统计数据，再创建预览内容，确保数量显示正确
                        Log.d(TAG, "[修复] 先获取统计数据，再创建预览内容");
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
     * 获取媒体库统计数据（旧版本，保留用于兼容）
     */
    private void loadLibraryItemCounts(List<MediaManager.MediaDbItem> libraries) {
        Log.d(TAG, "[兼容] 调用旧版本统计方法，建议使用loadLibraryItemCountsAndPreview");
        loadLibraryItemCountsAndPreview(libraries);
    }
    
    /**
     * 媒体库点击事件
     */
    private void onLibraryClick(MediaManager.MediaDbItem library, int position) {
        Log.d(TAG, "用户点击媒体库: " + library.getName());
        
        // [修复] 在当前Activity显示媒体库完整内容，而不是跳转
        Log.d(TAG, "[修复] 在当前Activity显示媒体库内容: " + library.getName());
        
        // 设置选中状态
        mediaLibraryAdapter.setSelectedPosition(position);
        currentSelectedLibrary = library;
        isShowingLibraryContent = true;
        
        // 显示该媒体库的完整内容
        showLibraryFullContent(library);
        
        // [修复] 更新导航状态 - 取消主页选中，选中媒体库
        updateNavigationState();
    }
    
    /**
     * 设置分类卡片监听器 - 已废弃
     */
    private void setupCategoryCardListeners() {
        // 新UI设计中移除了固定的分类卡片，改用动态媒体库列表
        Log.d(TAG, "旧版分类卡片代码已废弃");
        /* 
        // 旧代码已注释，新布局中不再使用固定的分类卡片
        movieCategoryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "用户点击电影分类");
                navigateToCategory("movie", "电影");
            }
        });
        
        tvCategoryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "用户点击电视剧分类");
                navigateToCategory("tv", "电视剧");
            }
        });
        
        animeCategoryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "用户点击动漫分类");
                navigateToCategory("anime", "动漫");
            }
        });
        
        setupCategoryFocusListeners();
        */
    }
    
    /**
     * 设置分类卡片焦点监听器 - 已废弃
     */
    private void setupCategoryFocusListeners() {
        // 新UI设计中移除了固定的分类卡片，改用动态媒体库列表
        Log.d(TAG, "旧版焦点监听器代码已废弃");
        /*
        View.OnFocusChangeListener focusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 获得焦点时轻微放大
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                } else {
                    // 失去焦点时恢复大小
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            }
        };
        
        movieCategoryCard.setOnFocusChangeListener(focusListener);
        tvCategoryCard.setOnFocusChangeListener(focusListener);
        animeCategoryCard.setOnFocusChangeListener(focusListener);
        */
    }
    
    /**
     * 导航到分类页面（旧版本，兼容性）
     */
    private void navigateToCategory(String categoryType, String categoryName) {
        navigateToCategory(categoryType, categoryName, null);
    }
    
    /**
     * 导航到分类页面（新版本，包含GUID）
     */
    private void navigateToCategory(String categoryType, String categoryName, String categoryGuid) {
        Log.d(TAG, "导航到分类: " + categoryName + " (" + categoryType + "), GUID: " + categoryGuid);
        
        try {
            // 实际导航到VideoListActivity
            Intent intent = new Intent(this, VideoListActivity.class);
            intent.putExtra("category_type", categoryType);
            intent.putExtra("category_name", categoryName);
            if (categoryGuid != null && !categoryGuid.isEmpty()) {
                intent.putExtra("category_guid", categoryGuid);
            }
            startActivity(intent);
            
            Log.d(TAG, "成功导航到 " + categoryName + " 分类");
        } catch (Exception e) {
            Log.e(TAG, "导航失败: " + e.getMessage());
            ToastUtils.show(this, "打开 " + categoryName + " 分类失败，请重试");
        }
    }
    
    /**
     * 更新状态信息 - 新UI中已移除状态文本
     */
    private void updateStatus(String status) {
        // 新UI设计中没有statusText组件
        Log.d(TAG, "状态更新: " + status);
        // ToastUtils.show(this, status); // 可选：用Toast显示状态
    }
    
    /**
     * 执行登出操作
     */
    private void performLogout() {
        Log.d(TAG, "开始执行登出...");
        
        // 清除认证信息
        SharedPreferencesManager.clearAuthInfo();
        
        // 显示退出消息
        ToastUtils.show(this, "已退出登录");
        
        // 跳转到登录页
        navigateToLogin();
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
     * 加载所有媒体库的内容展示（已废弃，改用预览模式）
     */
    private void loadAllMediaLibrariesContent(List<MediaManager.MediaDbItem> libraries) {
        Log.d(TAG, "[废弃] loadAllMediaLibrariesContent已改用loadMediaLibrariesPreview");
        loadMediaLibrariesPreview(libraries);
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
     * 为单个媒体库创建内容展示区域（已废弃，改用预览模式）
     */
    private void createMediaLibraryContentSection(MediaManager.MediaDbItem library) {
        Log.d(TAG, "[废弃] createMediaLibraryContentSection已改用createMediaLibraryPreviewSection");
        createMediaLibraryPreviewSection(library, 6);
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
            if (isSelected) {
                navHome.setBackgroundColor(getColor(R.color.tv_accent));
                navHome.setTextColor(getColor(R.color.tv_text_on_accent));
                Log.d(TAG, "主页导航：已选中");
            } else {
                navHome.setBackground(null);
                navHome.setTextColor(getColor(R.color.tv_text_primary));
                Log.d(TAG, "主页导航：取消选中");
            }
        }
    }
    
    /**
     * 更新预览区域标题
     */
    private void updatePreviewSectionTitles(List<MediaManager.MediaDbItem> libraries) {
        Log.d(TAG, "开始更新预览区域标题");
        
        // 遍历媒体内容容器的子视图，找到标题TextView并更新
        for (int i = 0; i < mediaContentContainer.getChildCount(); i++) {
            View sectionView = mediaContentContainer.getChildAt(i);
            if (sectionView instanceof LinearLayout) {
                LinearLayout sectionLayout = (LinearLayout) sectionView;
                
                // 查找第一个子视图（应该是标题）
                if (sectionLayout.getChildCount() > 0) {
                    View firstChild = sectionLayout.getChildAt(0);
                    if (firstChild instanceof TextView) {
                        TextView titleView = (TextView) firstChild;
                        String currentTitle = titleView.getText().toString();
                        
                        // 从当前标题中提取媒体库名称（去掉数量部分）
                        String libraryName = currentTitle.replaceAll("\\s*\\(\\d+\\)\\s*$", "");
                        
                        // 找到对应的媒体库并更新标题
                        for (MediaManager.MediaDbItem library : libraries) {
                            if (library.getName().equals(libraryName)) {
                                String newTitle = library.getName() + " (" + library.getItemCount() + ")";
                                titleView.setText(newTitle);
                                Log.d(TAG, "更新标题: " + currentTitle + " -> " + newTitle);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "预览区域标题更新完成");
    }
    
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
                    // 如果在主页，退出应用
                    finish();
                    return true;
                }
            case KeyEvent.KEYCODE_MENU:
                // 菜单键 - 显示设置或选项
                ToastUtils.show(this, "菜单功能待实现");
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}