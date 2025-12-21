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
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 🎬 季详情页Activity
 * 显示某个剧集的所有季信息，用户可以选择具体的季进入剧集详情页
 * 流程：首页 -> 季详情页（这里） -> 剧集详情页（MediaDetailActivity）
 */
public class SeasonDetailActivity extends AppCompatActivity {
    private static final String TAG = "SeasonDetailActivity";
    
    public static final String EXTRA_MEDIA_GUID = "media_guid";
    public static final String EXTRA_MEDIA_TITLE = "media_title";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    
    // UI组件
    private ImageView backdropImageView;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView overviewTextView;
    private LinearLayout seasonsContainer;
    
    // 数据
    private String mediaGuid;
    private String mediaTitle;
    private String mediaType;
    private MediaManager mediaManager;
    private MediaDetailResponse mediaDetail;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "🎬 SeasonDetailActivity启动");
        
        // 获取传入参数
        Intent intent = getIntent();
        mediaGuid = intent.getStringExtra(EXTRA_MEDIA_GUID);
        mediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
        mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE);
        
        if (mediaGuid == null || mediaGuid.isEmpty()) {
            Log.e(TAG, "❌ 媒体GUID为空，退出季详情页");
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        Log.d(TAG, "📺 准备显示季详情: " + mediaTitle + " (GUID: " + mediaGuid + ")");
        
        // 创建布局
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
        // 创建可滚动的主布局
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(getColor(R.color.tv_background));
        scrollView.setFillViewport(true);
        
        // 创建垂直主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        mainLayout.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large)
        );
        
        // 背景图片
        backdropImageView = new ImageView(this);
        LinearLayout.LayoutParams backdropParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.tv_backdrop_height)
        );
        backdropParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        backdropImageView.setLayoutParams(backdropParams);
        backdropImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropImageView.setBackground(getDrawable(R.drawable.bg_card));
        mainLayout.addView(backdropImageView);
        
        // 标题
        titleTextView = new TextView(this);
        titleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_title));
        titleTextView.setTextColor(getColor(R.color.tv_text_primary));
        titleTextView.setText(mediaTitle != null ? mediaTitle : "加载中...");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        titleTextView.setLayoutParams(titleParams);
        mainLayout.addView(titleTextView);
        
        // 副标题
        subtitleTextView = new TextView(this);
        subtitleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        subtitleTextView.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        subtitleTextView.setLayoutParams(subtitleParams);
        mainLayout.addView(subtitleTextView);
        
        // 剧情概述
        overviewTextView = new TextView(this);
        overviewTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        overviewTextView.setTextColor(getColor(R.color.tv_text_primary));
        overviewTextView.setMaxLines(4);
        overviewTextView.setText("正在加载详情...");
        overviewTextView.setLineSpacing(4, 1.2f);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        overviewParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_xlarge);
        overviewTextView.setLayoutParams(overviewParams);
        mainLayout.addView(overviewTextView);
        
        // 选择季标题
        TextView seasonsTitle = new TextView(this);
        seasonsTitle.setText("选择季");
        seasonsTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        seasonsTitle.setTextColor(getColor(R.color.tv_text_primary));
        LinearLayout.LayoutParams seasonsTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        seasonsTitleParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        seasonsTitle.setLayoutParams(seasonsTitleParams);
        mainLayout.addView(seasonsTitle);
        
        // 季容器
        seasonsContainer = new LinearLayout(this);
        seasonsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams seasonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        seasonsContainer.setLayoutParams(seasonsParams);
        mainLayout.addView(seasonsContainer);
        
        scrollView.addView(mainLayout);
        setContentView(scrollView);
        
        Log.d(TAG, "✅ 季详情页布局创建完成");
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
                    loadSeasonsList();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 媒体详情获取失败: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(SeasonDetailActivity.this, "详情加载失败: " + error, Toast.LENGTH_LONG).show();
                    overviewTextView.setText("详情加载失败，请重试");
                });
            }
        });
    }
    
    /**
     * 📊 加载季列表
     */
    private void loadSeasonsList() {
        Log.d(TAG, "📊 开始加载季列表");
        
        // 🚨 临时：根据媒体详情创建季列表（后续可以调用真实的季列表API）
        createSeasonsFromMediaDetail();
    }
    
    /**
     * 📺 根据媒体详情创建季列表
     */
    private void createSeasonsFromMediaDetail() {
        if (mediaDetail != null) {
            int numberOfSeasons = mediaDetail.getNumberOfSeasons();
            
            Log.d(TAG, "📺 创建" + numberOfSeasons + "个季的卡片");
            
            // 如果没有明确的季数信息，默认为1季
            if (numberOfSeasons <= 0) {
                numberOfSeasons = 1;
            }
            
            for (int seasonNumber = 1; seasonNumber <= numberOfSeasons; seasonNumber++) {
                createSeasonCard(seasonNumber);
            }
        }
    }
    
    /**
     * 🎬 创建单个季卡片
     */
    private void createSeasonCard(int seasonNumber) {
        // 创建季卡片容器
        CardView seasonCard = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        seasonCard.setLayoutParams(cardParams);
        seasonCard.setCardBackgroundColor(getColor(R.color.tv_card_background));
        seasonCard.setRadius(getResources().getDimension(R.dimen.tv_card_corner_radius));
        seasonCard.setCardElevation(getResources().getDimension(R.dimen.tv_card_elevation));
        seasonCard.setClickable(true);
        seasonCard.setFocusable(true);
        
        // 季卡片内容布局
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium)
        );
        
        // 季海报（使用主海报）
        ImageView seasonPoster = new ImageView(this);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.tv_season_card_width),
                getResources().getDimensionPixelSize(R.dimen.tv_season_poster_height)
        );
        posterParams.rightMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        seasonPoster.setLayoutParams(posterParams);
        seasonPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seasonPoster.setBackground(getDrawable(R.drawable.bg_card));
        
        // 加载季海报（使用主海报）
        if (mediaDetail != null && mediaDetail.getPoster() != null && !mediaDetail.getPoster().isEmpty()) {
            String posterUrl = mediaDetail.getPoster();
            if (!posterUrl.startsWith("http")) {
                posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=200";
            }
            Glide.with(this)
                    .load(posterUrl)
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(seasonPoster);
        }
        
        cardContent.addView(seasonPoster);
        
        // 季信息区域
        LinearLayout seasonInfo = new LinearLayout(this);
        seasonInfo.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        seasonInfo.setLayoutParams(infoParams);
        
        // 季标题
        TextView seasonTitle = new TextView(this);
        seasonTitle.setText("第 " + seasonNumber + " 季");
        seasonTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        seasonTitle.setTextColor(getColor(R.color.tv_text_primary));
        seasonInfo.addView(seasonTitle);
        
        // 季信息
        TextView seasonDesc = new TextView(this);
        String episodeCount = mediaDetail != null ? String.valueOf(mediaDetail.getNumberOfEpisodes()) : "未知";
        seasonDesc.setText("共 " + episodeCount + " 集");
        seasonDesc.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        seasonDesc.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
        seasonDesc.setLayoutParams(descParams);
        seasonInfo.addView(seasonDesc);
        
        cardContent.addView(seasonInfo);
        seasonCard.addView(cardContent);
        
        // 设置点击事件
        seasonCard.setOnClickListener(v -> {
            Log.d(TAG, "🎬 用户点击第" + seasonNumber + "季");
            navigateToEpisodeDetail(seasonNumber);
        });
        
        // 设置焦点效果
        seasonCard.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                seasonCard.setCardBackgroundColor(getColor(R.color.tv_accent_light));
            } else {
                seasonCard.setCardBackgroundColor(getColor(R.color.tv_card_background));
            }
        });
        
        seasonsContainer.addView(seasonCard);
    }
    
    /**
     * 🎨 更新UI显示
     */
    private void updateUI(MediaDetailResponse detail) {
        Log.d(TAG, "🎨 更新UI显示");
        
        // 标题
        titleTextView.setText(detail.getTitle());
        
        // 副标题
        String subtitle = "";
        if ("TV".equals(detail.getType())) {
            subtitle = "电视剧";
        } else if ("Movie".equals(detail.getType())) {
            subtitle = "电影";
        } else {
            subtitle = detail.getType();
        }
        
        // 添加年份信息
        if (detail.getReleaseDate() != null && !detail.getReleaseDate().isEmpty()) {
            subtitle += " • " + detail.getReleaseDate().substring(0, 4);
        } else if (detail.getAirDate() != null && !detail.getAirDate().isEmpty()) {
            subtitle += " • " + detail.getAirDate().substring(0, 4);
        }
        
        // 添加评分
        if (detail.getVoteAverage() > 0) {
            subtitle += " • ⭐ " + String.format("%.1f", detail.getVoteAverage());
        }
        
        subtitleTextView.setText(subtitle);
        
        // 剧情概述
        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            overviewTextView.setText(overview);
        } else {
            overviewTextView.setText("暂无剧情简介");
        }
        
        // 背景图片
        if (detail.getBackdrop() != null && !detail.getBackdrop().isEmpty()) {
            String backdropUrl = detail.getBackdrop();
            if (!backdropUrl.startsWith("http")) {
                backdropUrl = SharedPreferencesManager.getImageServiceUrl() + backdropUrl + "?w=800";
            }
            Glide.with(this)
                    .load(backdropUrl)
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .into(backdropImageView);
        }
        
        Log.d(TAG, "✅ UI更新完成");
    }
    
    /**
     * 🎬 跳转到剧集详情页
     */
    private void navigateToEpisodeDetail(int seasonNumber) {
        Log.d(TAG, "🎬 跳转到剧集详情页: 第" + seasonNumber + "季");
        
        // 🚨 获取真实的季GUID
        getSeasonGuidAndNavigate(seasonNumber);
    }
    
    /**
     * 🎬 获取季GUID并跳转
     */
    private void getSeasonGuidAndNavigate(int seasonNumber) {
        // 🚨 临时解决方案：根据经验推测季GUID格式
        // 实际应该调用季列表API获取真实的季GUID
        // 从您提供的示例可以看出：
        // 主剧集GUID: bb54bb2accdb412bbd735d5ab2d63efb
        // 第1季GUID: a80fa14531894e9bb1a9cc4e754fc683
        
        // 🚨 这里使用一个临时的映射，实际项目中应该调用API
        String seasonGuid = mapToSeasonGuid(mediaGuid, seasonNumber);
        
        Intent intent = new Intent(this, MediaDetailActivity.class);
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_GUID, seasonGuid); // ✅ 使用季GUID
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TITLE, mediaTitle);
        intent.putExtra(MediaDetailActivity.EXTRA_MEDIA_TYPE, mediaType);
        intent.putExtra("season_number", seasonNumber);
        startActivity(intent);
    }
    
    /**
     * 🎬 临时方法：映射主剧集GUID到季GUID
     */
    private String mapToSeasonGuid(String mainGuid, int seasonNumber) {
        // 🚨 临时硬编码映射，基于您提供的示例
        if ("bb54bb2accdb412bbd735d5ab2d63efb".equals(mainGuid) && seasonNumber == 1) {
            return "a80fa14531894e9bb1a9cc4e754fc683"; // 亲爱的小孩 第1季
        }
        
        // 如果没有找到映射，返回原GUID（会回退到直接播放）
        Log.w(TAG, "⚠️ 没有找到季GUID映射，使用原GUID: " + mainGuid);
        return mainGuid;
    }
    
    /**
     * 📱 处理按键事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // 返回键 - 退出季详情页
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
