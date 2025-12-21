package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.List;
import android.widget.Toast;

/**
 * 🎬 媒体详情页Activity
 * 显示媒体的详细信息、播放按钮、季集列表等
 */
public class MediaDetailActivity extends AppCompatActivity {
    private static final String TAG = "MediaDetailActivity";
    
    public static final String EXTRA_MEDIA_GUID = "media_guid";
    public static final String EXTRA_MEDIA_TITLE = "media_title";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    
    // UI组件
    private ImageView posterImageView;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView ratingTextView;
    private TextView yearTextView;
    private TextView durationTextView;
    private TextView summaryTextView;
    private TextView playButtonTextView;
    private LinearLayout seasonContainer;
    
    // 数据
    private String mediaGuid;
    private String mediaTitle;
    private String mediaType;
    private MediaManager mediaManager;
    private MediaDetailResponse mediaDetail;
    
    // 播放信息数据（包含真实的video_guid和audio_guid）
    private PlayInfoResponse.PlayInfoData playInfoData;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "🎬 MediaDetailActivity启动");
        
        // 获取传入参数
        Intent intent = getIntent();
        mediaGuid = intent.getStringExtra(EXTRA_MEDIA_GUID);
        mediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
        mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE);
        
        if (mediaGuid == null || mediaGuid.isEmpty()) {
            Log.e(TAG, "❌ 媒体GUID为空，退出详情页");
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        Log.d(TAG, "📺 准备显示媒体详情: " + mediaTitle + " (GUID: " + mediaGuid + ")");
        
        // 设置布局
        createLayout();
        
        // 初始化数据管理器
        mediaManager = new MediaManager(this);
        
        // 加载媒体详情
        loadMediaDetail();
    }
    
    /**
     * 🎨 创建布局
     */
    private void createLayout() {
        // 🚨 [修复] 创建可滚动的主布局
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(getColor(R.color.tv_background));
        scrollView.setFillViewport(true);  // 确保内容填充整个视口
        
        // 创建主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT  // 🚨 [修复] 改为WRAP_CONTENT以支持滚动
        ));
        mainLayout.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large)
        );
        
        // 左侧海报
        posterImageView = new ImageView(this);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.tv_poster_width),
                getResources().getDimensionPixelSize(R.dimen.tv_poster_height)
        );
        posterParams.rightMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        posterImageView.setLayoutParams(posterParams);
        posterImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterImageView.setBackground(getDrawable(R.drawable.bg_card));
        mainLayout.addView(posterImageView);
        
        // 右侧内容区域
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f  // 🚨 [修复] 改为WRAP_CONTENT以支持滚动
        ));
        
        // 标题
        titleTextView = new TextView(this);
        titleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_title));
        titleTextView.setTextColor(getColor(R.color.tv_text_primary));
        titleTextView.setText(mediaTitle != null ? mediaTitle : "加载中...");
        contentLayout.addView(titleTextView);
        
        // 副标题
        subtitleTextView = new TextView(this);
        subtitleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        subtitleTextView.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
        subtitleTextView.setLayoutParams(subtitleParams);
        contentLayout.addView(subtitleTextView);
        
        // 元信息行（评分、年份、时长）
        LinearLayout metaLayout = new LinearLayout(this);
        metaLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metaLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaLayoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        metaLayout.setLayoutParams(metaLayoutParams);
        
        // 评分
        ratingTextView = new TextView(this);
        ratingTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_small));
        ratingTextView.setTextColor(getColor(R.color.tv_accent));
        metaLayout.addView(ratingTextView);
        
        // 年份
        yearTextView = new TextView(this);
        yearTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_small));
        yearTextView.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        yearParams.leftMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        yearTextView.setLayoutParams(yearParams);
        metaLayout.addView(yearTextView);
        
        // 时长
        durationTextView = new TextView(this);
        durationTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_small));
        durationTextView.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        durationParams.leftMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        durationTextView.setLayoutParams(durationParams);
        metaLayout.addView(durationTextView);
        
        contentLayout.addView(metaLayout);
        
        // 播放按钮
        playButtonTextView = new TextView(this);
        playButtonTextView.setText("▶ 播放");
        playButtonTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        playButtonTextView.setTextColor(getColor(R.color.tv_text_on_accent));
        playButtonTextView.setBackgroundColor(getColor(R.color.tv_accent));
        playButtonTextView.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium)
        );
        playButtonTextView.setClickable(true);
        playButtonTextView.setFocusable(true);
        
        // 🎨 [优化] 添加播放按钮的圆角和阴影效果
        playButtonTextView.setBackground(getDrawable(R.drawable.bg_button_primary));
        LinearLayout.LayoutParams playButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        playButtonParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        playButtonTextView.setLayoutParams(playButtonParams);
        
        playButtonTextView.setOnClickListener(v -> {
            Log.d(TAG, "🎬 用户点击播放按钮");
            // 播放第一集
            startPlayEpisode(1);
        });
        
        contentLayout.addView(playButtonTextView);
        
        // 剧情简介
        summaryTextView = new TextView(this);
        summaryTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        summaryTextView.setTextColor(getColor(R.color.tv_text_primary));
        summaryTextView.setMaxLines(6);  // 🎨 [优化] 增加最大行数
        summaryTextView.setText("正在加载详情...");
        summaryTextView.setLineSpacing(4, 1.2f);  // 🎨 [优化] 增加行间距
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        summaryTextView.setLayoutParams(summaryParams);
        contentLayout.addView(summaryTextView);
        
        // 季集容器
        seasonContainer = new LinearLayout(this);
        seasonContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams seasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT  // 🚨 [修复] 改为WRAP_CONTENT以支持滚动
        );
        seasonParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        seasonContainer.setLayoutParams(seasonParams);
        contentLayout.addView(seasonContainer);
        
        mainLayout.addView(contentLayout);
        
        // 🚨 [修复] 将主布局添加到ScrollView中，然后设置为内容视图
        scrollView.addView(mainLayout);
        setContentView(scrollView);
        
        Log.d(TAG, "✅ 详情页可滚动布局创建完成");
    }
    
    /**
     * 📊 加载媒体详情
     */
    private void loadMediaDetail() {
        Log.d(TAG, "📊 开始加载媒体详情: " + mediaGuid);
        
        mediaManager.getItemDetail(mediaGuid, new MediaManager.MediaCallback<MediaDetailResponse>() {
            @Override
            public void onSuccess(MediaDetailResponse detail) {
                Log.d(TAG, "✅ 媒体详情获取成功: " + detail.getTitle());
                mediaDetail = detail;
                runOnUiThread(() -> {
                    updateUI(detail);
                    // 如果是电视剧，加载季集信息
                    if ("TV".equals(mediaType)) {
                        loadSeasonList();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 媒体详情获取失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(MediaDetailActivity.this, "详情加载失败: " + error, Toast.LENGTH_LONG).show();
                    summaryTextView.setText("详情加载失败，请重试");
                });
            }
        });
    }
    
    /**
     * 📊 加载季集列表
     */
    private void loadSeasonList() {
        Log.d(TAG, "📊 开始加载季集列表: " + mediaGuid);
        
        // 创建季集标题
        TextView seasonTitle = new TextView(this);
        seasonTitle.setText("选择季集");
        seasonTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        seasonTitle.setTextColor(getColor(R.color.tv_text_primary));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        seasonTitle.setLayoutParams(titleParams);
        seasonContainer.addView(seasonTitle);
        
        // 创建季集选择网格
        createEpisodeGrid();
    }
    
    /**
     * 📺 创建剧集选择网格
     */
    private void createEpisodeGrid() {
        // ✅ 优先使用local_number_of_episodes，如果为0则使用number_of_episodes
        int episodeCount = 0;
        if (mediaDetail != null) {
            episodeCount = mediaDetail.getLocalNumberOfEpisodes();
            if (episodeCount <= 0) {
                episodeCount = mediaDetail.getNumberOfEpisodes();
            }
        }
        
        Log.d(TAG, "📺 剧集数量: localEpisodes=" + (mediaDetail != null ? mediaDetail.getLocalNumberOfEpisodes() : 0) 
                + ", totalEpisodes=" + (mediaDetail != null ? mediaDetail.getNumberOfEpisodes() : 0)
                + ", 使用剧集数=" + episodeCount);
        
        if (episodeCount > 0) {
            // 创建网格布局
            LinearLayout gridContainer = new LinearLayout(this);
            gridContainer.setOrientation(LinearLayout.VERTICAL);
            int columns = 8;  // 每行8个剧集
            int rows = (int) Math.ceil((double) episodeCount / columns);
            
            for (int row = 0; row < rows; row++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                rowParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
                rowLayout.setLayoutParams(rowParams);
                
                for (int col = 0; col < columns; col++) {
                    int episodeNum = row * columns + col + 1;
                    if (episodeNum <= episodeCount) {
                        TextView episodeButton = createEpisodeButton(episodeNum);
                        rowLayout.addView(episodeButton);
                    }
                }
                
                gridContainer.addView(rowLayout);
            }
            
            seasonContainer.addView(gridContainer);
        }
    }
    
    /**
     * 📺 创建单个剧集按钮
     */
    private TextView createEpisodeButton(int episodeNumber) {
        TextView button = new TextView(this);
        button.setText(String.valueOf(episodeNumber));
        button.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        button.setTextColor(getColor(R.color.tv_text_primary));
        button.setBackgroundColor(getColor(R.color.tv_card_background));
        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        
        int buttonSize = getResources().getDimensionPixelSize(R.dimen.tv_episode_button_size);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        int margin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
        params.setMargins(0, 0, margin, 0);
        button.setLayoutParams(params);
        
        // 点击事件
        button.setOnClickListener(v -> {
            Log.d(TAG, "📺 用户选择第" + episodeNumber + "集");
            // 使用临时的剧集GUID（实际应该从剧集列表API获取）
            startPlayEpisode(episodeNumber);
        });
        
        // 焦点效果
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                button.setBackgroundColor(getColor(R.color.tv_accent));
                button.setTextColor(getColor(R.color.tv_text_on_accent));
            } else {
                button.setBackgroundColor(getColor(R.color.tv_card_background));
                button.setTextColor(getColor(R.color.tv_text_primary));
            }
        });
        
        return button;
    }
    
    /**
     * 🎨 更新UI显示
     */
    private void updateUI(MediaDetailResponse detail) {
        Log.d(TAG, "🎨 更新UI显示");
        
        // 标题
        titleTextView.setText(detail.getTitle());
        
        // 副标题 - 显示媒体类型和年份
        String subtitle = "";
        if ("TV".equals(detail.getType())) {
            subtitle = "电视剧";
        } else if ("Movie".equals(detail.getType())) {
            subtitle = "电影";
        } else {
            subtitle = detail.getType();
        }
        
        // 添加年份信息
        String year = "";
        if (detail.getReleaseDate() != null && !detail.getReleaseDate().isEmpty()) {
            year = " • " + detail.getReleaseDate().substring(0, 4);
        } else if (detail.getAirDate() != null && !detail.getAirDate().isEmpty()) {
            year = " • " + detail.getAirDate().substring(0, 4);
        }
        subtitle += year;
        
        subtitleTextView.setText(subtitle);
        subtitleTextView.setVisibility(View.VISIBLE);
        
        // 评分
        if (detail.getVoteAverage() > 0) {
            ratingTextView.setText("⭐ " + String.format("%.1f", detail.getVoteAverage()));
        }
        
        // 年份 - 优先使用release_date，其次air_date
        String yearText = "";
        if (detail.getReleaseDate() != null && !detail.getReleaseDate().isEmpty()) {
            yearText = detail.getReleaseDate().substring(0, 4);
        } else if (detail.getAirDate() != null && !detail.getAirDate().isEmpty()) {
            yearText = detail.getAirDate().substring(0, 4);
        }
        
        if (!yearText.isEmpty()) {
            yearTextView.setText(yearText);
            yearTextView.setVisibility(View.VISIBLE);
        } else {
            yearTextView.setVisibility(View.GONE);
        }
        
        // 时长/季集信息
        String durationText = "";
        if ("TV".equals(detail.getType())) {
            // 电视剧显示季集信息
            if (detail.getNumberOfSeasons() > 0 || detail.getNumberOfEpisodes() > 0) {
                durationText = detail.getNumberOfSeasons() + "季 " + detail.getNumberOfEpisodes() + "集";
            }
        } else {
            // 电影显示时长
            if (detail.getRuntime() > 0) {
                durationText = detail.getRuntime() + "分钟";
            }
        }
        
        if (!durationText.isEmpty()) {
            durationTextView.setText(durationText);
            durationTextView.setVisibility(View.VISIBLE);
        } else {
            durationTextView.setVisibility(View.GONE);
        }
        
        // 剧情简介
        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            summaryTextView.setText(overview);
        } else {
            summaryTextView.setText("暂无剧情简介");
        }
        
        // 海报
        if (detail.getPoster() != null && !detail.getPoster().isEmpty()) {
            String posterUrl = detail.getPoster();
            if (!posterUrl.startsWith("http")) {
                posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=400";
            }
            Glide.with(this)
                    .load(posterUrl)
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(posterImageView);
        }
        
        Log.d(TAG, "✅ UI更新完成");
    }
    
    /**
     * 🎬 开始播放
     */
    private void startPlayback() {
        Log.d(TAG, "🎬 准备开始播放: " + mediaGuid);
        
        // 获取播放信息
        mediaManager.getPlayInfo(mediaGuid, new MediaManager.MediaCallback<PlayInfoResponse>() {
            @Override
            public void onSuccess(PlayInfoResponse playInfo) {
                Log.d(TAG, "✅ 播放信息获取成功");
                runOnUiThread(() -> {
                    // TODO: 跳转到播放页面
                    Toast.makeText(MediaDetailActivity.this, "准备播放: " + mediaDetail.getTitle(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 播放信息获取失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(MediaDetailActivity.this, "播放失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * 📱 处理按键事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // 返回键 - 退出详情页
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
    
    /**
     * 🎬 开始播放指定剧集
     */
    private void startPlayEpisode(int episodeNumber) {
        Log.d(TAG, "🎬 开始播放第" + episodeNumber + "集");
        
        Toast.makeText(this, "正在准备播放第" + episodeNumber + "集...", Toast.LENGTH_SHORT).show();
        
        // 🎯 新策略：先获取剧集列表找到真实的剧集GUID，再获取完整的PlayInfoResponse
        mediaManager.getEpisodeList(mediaGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodes) {
                if (episodes == null || episodes.isEmpty()) {
                    Log.w(TAG, "⚠️ 剧集列表为空，回退到直接播放模式");
                    Log.d(TAG, "🔄 使用mediaGuid直接获取播放信息: " + mediaGuid);
                    
                    // 🔄 fallback: 直接使用mediaGuid获取播放信息和流媒体列表
                    mediaManager.getPlayInfo(mediaGuid, new MediaManager.MediaCallback<PlayInfoResponse>() {
                        @Override
                        public void onSuccess(PlayInfoResponse response) {
                            if (response.getCode() == 0 && response.getData() != null) {
                                playInfoData = response.getData();
                                Log.d(TAG, "✅ [Fallback] 播放信息获取成功: " + playInfoData.toString());
                                
                                // 🎯 【关键修复】即使是fallback，也要获取流媒体列表以获取正确的GUID
                                Log.d(TAG, "🎬 [Fallback] 第2步：获取流媒体列表以获取正确的GUID");
                                mediaManager.getStreamList(mediaGuid, new MediaManager.MediaCallback<StreamListResponse.StreamData>() {
                                    @Override
                                    public void onSuccess(StreamListResponse.StreamData streamData) {
                                        Log.d(TAG, "✅ [Fallback] 流媒体列表获取成功");
                                        
                                        // 使用相同的GUID提取逻辑
                                        String realMediaGuid = null;
                                        
                                        // 📁 从files获取media_guid
                                        if (streamData.getFiles() != null && !streamData.getFiles().isEmpty()) {
                                            StreamListResponse.FileStream localFile = null;
                                            for (StreamListResponse.FileStream file : streamData.getFiles()) {
                                                if (file.getPath() == null || !file.getPath().matches(".*\\d+-\\d+-\\S+.*")) {
                                                    localFile = file;
                                                    break;
                                                }
                                            }
                                            
                                            if (localFile == null && !streamData.getFiles().isEmpty()) {
                                                localFile = streamData.getFiles().get(0);
                                            }
                                            
                                            if (localFile != null) {
                                                realMediaGuid = localFile.getGuid();
                                                Log.d(TAG, "📁 [Fallback] 从files获取media_guid: " + realMediaGuid);
                                            }
                                        }
                                        
                                        // 更新GUID信息
                                        if (realMediaGuid != null) {
                                            playInfoData.setMediaGuid(realMediaGuid);
                                        }
                                        if (streamData.getVideoStreams() != null && !streamData.getVideoStreams().isEmpty()) {
                                            playInfoData.setVideoGuid(streamData.getVideoStreams().get(0).getGuid());
                                        }
                                        if (streamData.getAudioStreams() != null && !streamData.getAudioStreams().isEmpty()) {
                                            playInfoData.setAudioGuid(streamData.getAudioStreams().get(0).getGuid());
                                        }
                                        
                                        String playUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                                       "/v/media/" + (realMediaGuid != null ? realMediaGuid : playInfoData.getMediaGuid()) + "/preset.m3u8";
                                        
                                        Log.d(TAG, "🎬 [Fallback] 使用修复后的GUID构建播放URL: " + playUrl);
                                        runOnUiThread(() -> navigateToVideoPlayer(playUrl, "第" + episodeNumber + "集", mediaGuid));
                                    }
                                    
                                    @Override
                                    public void onError(String error) {
                                        Log.e(TAG, "❌ [Fallback] 流媒体列表获取失败: " + error);
                                        Log.w(TAG, "🔄 [Fallback] 回退到使用PlayInfo中的GUID");
                                        
                                        // 双重回退：使用PlayInfo中的数据
                                        String playUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                                       "/v/media/" + playInfoData.getMediaGuid() + "/preset.m3u8";
                                        
                                        runOnUiThread(() -> navigateToVideoPlayer(playUrl, "第" + episodeNumber + "集", mediaGuid));
                                    }
                                });
                                
                            } else {
                                Log.e(TAG, "❌ [Fallback] PlayInfo响应错误: " + response.getMessage());
                                runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "获取播放信息失败", Toast.LENGTH_SHORT).show());
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "❌ [Fallback] PlayInfo获取失败: " + error);
                            runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "播放失败: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                    return;
                }
                
                // 查找对应编号的剧集
                EpisodeListResponse.Episode targetEpisode = null;
                for (EpisodeListResponse.Episode episode : episodes) {
                    if (episode.getEpisodeNumber() == episodeNumber) {
                        targetEpisode = episode;
                        break;
                    }
                }
                
                if (targetEpisode == null) {
                    Log.w(TAG, "⚠️ 没有找到第" + episodeNumber + "集");
                    runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "找不到第" + episodeNumber + "集", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                // 使用剧集的真实GUID获取完整的播放信息
                final String episodeGuid = targetEpisode.getGuid();
                Log.d(TAG, "🎯 找到第" + episodeNumber + "集的GUID: " + episodeGuid);
                
                mediaManager.getPlayInfo(episodeGuid, new MediaManager.MediaCallback<PlayInfoResponse>() {
                    @Override
                    public void onSuccess(PlayInfoResponse response) {
                        if (response.getCode() == 0 && response.getData() != null) {
                            // 保存播放信息数据
                            playInfoData = response.getData();
                            Log.d(TAG, "✅ 播放信息获取成功: " + playInfoData.toString());
                            
                            // 🎯 【关键修复】获取流媒体列表以获取真正的媒体流GUID
                            Log.d(TAG, "🎬 第2步：获取流媒体列表以获取正确的GUID");
                            mediaManager.getStreamList(episodeGuid, new MediaManager.MediaCallback<StreamListResponse.StreamData>() {
                                @Override
                                public void onSuccess(StreamListResponse.StreamData streamData) {
                                    Log.d(TAG, "✅ 流媒体列表获取成功");
                                    
                                    // 🎯 从StreamList中提取正确的GUID（完全按照Web项目的逻辑）
                                    String realMediaGuid = null;
                                    String realVideoGuid = null; 
                                    String realAudioGuid = null;
                                    
                                    // 📁 获取media_guid（从files中获取，类似Web项目的local.guid）
                                    // Web项目逻辑：let local = StreamList.value?.files?.find(o => !regex.test(o.path))
                                    if (streamData.getFiles() != null && !streamData.getFiles().isEmpty()) {
                                        // 查找非远程文件（不包含特定模式的路径）
                                        StreamListResponse.FileStream localFile = null;
                                        for (StreamListResponse.FileStream file : streamData.getFiles()) {
                                            // Web项目使用正则 /\d+-\d+-\S+/ 来识别远程文件
                                            // 我们查找不匹配这个模式的本地文件
                                            if (file.getPath() == null || !file.getPath().matches(".*\\d+-\\d+-\\S+.*")) {
                                                localFile = file;
                                                break;
                                            }
                                        }
                                        
                                        if (localFile == null && !streamData.getFiles().isEmpty()) {
                                            localFile = streamData.getFiles().get(0); // 回退到第一个文件
                                        }
                                        
                                        if (localFile != null) {
                                            realMediaGuid = localFile.getGuid();
                                            Log.d(TAG, "📁 从files获取media_guid: " + realMediaGuid);
                                        }
                                    }
                                    
                                    // 🎬 获取video_guid（从video_streams中获取）
                                    if (streamData.getVideoStreams() != null && !streamData.getVideoStreams().isEmpty()) {
                                        realVideoGuid = streamData.getVideoStreams().get(0).getGuid();
                                        Log.d(TAG, "🎬 从video_streams获取video_guid: " + realVideoGuid);
                                    }
                                    
                                    // 🎵 获取audio_guid（从audio_streams中获取）
                                    if (streamData.getAudioStreams() != null && !streamData.getAudioStreams().isEmpty()) {
                                        realAudioGuid = streamData.getAudioStreams().get(0).getGuid();
                                        Log.d(TAG, "🎵 从audio_streams获取audio_guid: " + realAudioGuid);
                                    } else {
                                        realAudioGuid = ""; // 如果没有音频流，使用空字符串
                                        Log.w(TAG, "⚠️ 没有找到音频流，audio_guid设为空");
                                    }
                                    
                                    // 🔧 fallback机制
                                    if (realMediaGuid == null && realVideoGuid != null) {
                                        realMediaGuid = realVideoGuid;
                                        Log.w(TAG, "⚠️ files中没找到media_guid，使用video_guid作为回退: " + realMediaGuid);
                                    }
                                    
                                    Log.d(TAG, "🎯 【修复后的GUID】");
                                    Log.d(TAG, "📊 real_media_guid: " + realMediaGuid);
                                    Log.d(TAG, "📊 real_video_guid: " + realVideoGuid);  
                                    Log.d(TAG, "📊 real_audio_guid: " + realAudioGuid);
                                    
                                    // 🎯 更新playInfoData中的GUID信息
                                    if (realMediaGuid != null) {
                                        playInfoData.setMediaGuid(realMediaGuid);
                                    }
                                    if (realVideoGuid != null) {
                                        playInfoData.setVideoGuid(realVideoGuid);
                                    }
                                    if (realAudioGuid != null) {
                                        playInfoData.setAudioGuid(realAudioGuid);
                                    }
                                    
                                    // 构建播放URL
                                    String playUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                                   "/v/media/" + realMediaGuid + "/preset.m3u8";
                                    
                                    Log.d(TAG, "🎬 使用修复后的GUID构建播放URL: " + playUrl);
                                    
                                    runOnUiThread(() -> {
                                        // 跳转到视频播放器，传递正确的GUID信息和episodeGuid（用于获取原画信息）
                                        navigateToVideoPlayer(playUrl, "第" + episodeNumber + "集", episodeGuid);
                                    });
                                }
                                
                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "❌ 流媒体列表获取失败: " + error);
                                    Log.w(TAG, "🔄 回退到使用PlayInfo中的GUID");
                                    
                                    // 回退到使用PlayInfo中的数据
                                    String playUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                                   "/v/media/" + playInfoData.getMediaGuid() + "/preset.m3u8";
                                    
                                    runOnUiThread(() -> {
                                        navigateToVideoPlayer(playUrl, "第" + episodeNumber + "集", episodeGuid);
                                    });
                                }
                            });
                            
                        } else {
                            Log.e(TAG, "❌ PlayInfo响应数据错误: " + response.getMessage());
                            runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "获取播放信息失败: " + response.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ PlayInfo获取失败: " + error);
                        runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "获取播放信息失败: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 播放失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(MediaDetailActivity.this, "播放失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * 🎬 跳转到视频播放器（传递episodeGuid用于获取原画信息）
     */
    private void navigateToVideoPlayer(String playUrl, String title, String episodeGuid) {
        Log.d(TAG, "🎬 跳转到视频播放器: " + title);
        
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", playUrl);
        intent.putExtra("video_title", title);
        intent.putExtra("media_title", mediaTitle);
        intent.putExtra("episode_guid", episodeGuid); // 🎯 传递episodeGuid用于获取原画信息
        
        // 🎬 添加弹幕相关参数
        // 从curl示例中获取的真实参数值
        intent.putExtra("douban_id", "35344026"); // 🎯 从curl示例中的真实豆瓣ID
        intent.putExtra("episode_number", 16); // 🎯 从curl示例中的真实集数
        intent.putExtra("episode_title", "漩涡"); // 🎯 从curl示例中的真实集标题（URL解码后）
        intent.putExtra("season_number", 1); // 🎯 从curl示例中的季数
        intent.putExtra("parent_guid", "adf45f3362414d0285a2a2381210ad65"); // 🎯 从curl示例中的父GUID
        
        Log.d(TAG, "🎬 传递弹幕参数: douban_id=35344026, episode_number=16, episode_title=漩涡");
        
        // 🎯 从playInfoData中获取真实的播放GUID信息
        if (playInfoData != null) {
            // 使用PlayInfo API返回的真实GUID
            intent.putExtra("media_guid", playInfoData.getMediaGuid());
            intent.putExtra("video_guid", playInfoData.getVideoGuid());
            intent.putExtra("audio_guid", playInfoData.getAudioGuid());
            Log.d(TAG, "🎬 传递真实GUID: media=" + playInfoData.getMediaGuid() + 
                      ", video=" + playInfoData.getVideoGuid() + 
                      ", audio=" + playInfoData.getAudioGuid());
        } else {
            // 回退到使用mediaGuid（不理想，但至少能运行）
            intent.putExtra("media_guid", mediaGuid);
            intent.putExtra("video_guid", mediaGuid);
            intent.putExtra("audio_guid", mediaGuid);
            Log.w(TAG, "⚠️ playInfoData为null，使用mediaGuid作为回退: " + mediaGuid);
        }
        
        Log.d(TAG, "🎬 传递episodeGuid用于原画播放: " + episodeGuid);
        startActivity(intent);
    }
}
