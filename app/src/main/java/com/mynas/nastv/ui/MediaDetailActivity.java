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
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.List;

/**
 * 🎬 Media Detail Activity
 * 显示媒体详情（电影/电视剧第一层）
 * 电视剧会显示季列表，点击季进入 SeasonDetailActivity
 * Web端URL格式: /v/tv/{tv_guid} 或 /v/movie/{movie_guid}
 */
public class MediaDetailActivity extends AppCompatActivity {
    private static final String TAG = "MediaDetailActivity";
    
    public static final String EXTRA_MEDIA_GUID = "media_guid";
    public static final String EXTRA_MEDIA_TITLE = "media_title";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    
    // UI
    private ImageView posterImageView;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView ratingTextView;
    private TextView yearTextView;
    private TextView durationTextView;
    private TextView summaryTextView;
    private TextView playButtonTextView;
    private LinearLayout seasonContainer;
    
    // Data
    private String mediaGuid;
    private String mediaTitle;
    private String mediaType;
    private MediaManager mediaManager;
    private MediaDetailResponse mediaDetail;
    private List<SeasonListResponse.Season> seasons;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        mediaGuid = intent.getStringExtra(EXTRA_MEDIA_GUID);
        mediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
        mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE);
        
        if (mediaGuid == null || mediaGuid.isEmpty()) {
            Toast.makeText(this, "Invalid Media GUID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 🚀 优化：先显示简单布局，再异步加载数据
        createLayout();
        mediaManager = new MediaManager(this);
        
        // 延迟加载数据，让UI先渲染
        getWindow().getDecorView().post(this::loadMediaDetail);
    }
    
    private void createLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(getColor(R.color.tv_background));
        scrollView.setFillViewport(true);
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
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
        
        // Poster
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
        
        // Content
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        
        titleTextView = new TextView(this);
        titleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_title));
        titleTextView.setTextColor(getColor(R.color.tv_text_primary));
        titleTextView.setText(mediaTitle != null ? mediaTitle : "Loading...");
        contentLayout.addView(titleTextView);
        
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
        
        // Meta row
        LinearLayout metaLayout = new LinearLayout(this);
        metaLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metaLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaLayoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        metaLayout.setLayoutParams(metaLayoutParams);
        
        ratingTextView = new TextView(this);
        ratingTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_small));
        ratingTextView.setTextColor(getColor(R.color.tv_accent));
        metaLayout.addView(ratingTextView);
        
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
        
        // Play Button
        playButtonTextView = new TextView(this);
        playButtonTextView.setText("▶ 播放");
        playButtonTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        playButtonTextView.setTextColor(getColor(R.color.tv_text_on_accent));
        playButtonTextView.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_large),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium)
        );
        playButtonTextView.setClickable(true);
        playButtonTextView.setFocusable(true);
        playButtonTextView.setBackground(getDrawable(R.drawable.bg_button_primary));
        
        LinearLayout.LayoutParams playButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        playButtonParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        playButtonTextView.setLayoutParams(playButtonParams);
        
        playButtonTextView.setOnClickListener(v -> onPlayButtonClick());
        
        contentLayout.addView(playButtonTextView);
        
        summaryTextView = new TextView(this);
        summaryTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        summaryTextView.setTextColor(getColor(R.color.tv_text_primary));
        summaryTextView.setMaxLines(6);
        summaryTextView.setText("Loading details...");
        summaryTextView.setLineSpacing(4, 1.2f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        summaryTextView.setLayoutParams(summaryParams);
        contentLayout.addView(summaryTextView);
        
        seasonContainer = new LinearLayout(this);
        seasonContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams seasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        seasonParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        seasonContainer.setLayoutParams(seasonParams);
        contentLayout.addView(seasonContainer);
        
        mainLayout.addView(contentLayout);
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }
    
    private void loadMediaDetail() {
        mediaManager.getItemDetail(mediaGuid, new MediaManager.MediaCallback<MediaDetailResponse>() {
            @Override
            public void onSuccess(MediaDetailResponse detail) {
                mediaDetail = detail;
                runOnUiThread(() -> {
                    updateUI(detail);
                    if (isTVShow()) {
                        loadSeasonList();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MediaDetailActivity.this, "加载详情失败: " + error, Toast.LENGTH_LONG).show();
                    summaryTextView.setText("加载详情失败");
                });
            }
        });
    }
    
    private boolean isTVShow() {
        if ("TV".equalsIgnoreCase(mediaType)) return true;
        if (mediaDetail != null && "TV".equalsIgnoreCase(mediaDetail.getType())) return true;
        return false;
    }
    
    private void loadSeasonList() {
        mediaManager.getSeasonList(mediaGuid, new MediaManager.MediaCallback<List<SeasonListResponse.Season>>() {
            @Override
            public void onSuccess(List<SeasonListResponse.Season> seasonList) {
                seasons = seasonList;
                runOnUiThread(() -> {
                    createSeasonList();
                    if (seasonList != null && !seasonList.isEmpty()) {
                        playButtonTextView.setText("▶ 播放第1季");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load seasons: " + error);
                runOnUiThread(() -> loadEpisodeListFallback());
            }
        });
    }
    
    private void createSeasonList() {
        seasonContainer.removeAllViews();
        
        if (seasons == null || seasons.isEmpty()) {
            return;
        }
        
        TextView seasonTitle = new TextView(this);
        seasonTitle.setText("季列表");
        seasonTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        seasonTitle.setTextColor(getColor(R.color.tv_text_primary));
        seasonContainer.addView(seasonTitle);
        
        LinearLayout seasonRow = new LinearLayout(this);
        seasonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        seasonRow.setLayoutParams(rowParams);
        
        for (SeasonListResponse.Season season : seasons) {
            TextView seasonButton = createSeasonButton(season);
            seasonRow.addView(seasonButton);
        }
        
        seasonContainer.addView(seasonRow);
    }
    
    private TextView createSeasonButton(SeasonListResponse.Season season) {
        TextView button = new TextView(this);
        String text = "第 " + season.getSeasonNumber() + " 季";
        if (season.getEpisodeCount() > 0) {
            text += "\n" + season.getEpisodeCount() + "集";
        }
        button.setText(text);
        button.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        button.setTextColor(getColor(R.color.tv_text_primary));
        button.setBackgroundColor(getColor(R.color.tv_card_background));
        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium),
                getResources().getDimensionPixelSize(R.dimen.tv_margin_medium)
        );
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
        params.setMargins(0, 0, margin, 0);
        button.setLayoutParams(params);
        
        button.setOnClickListener(v -> navigateToSeasonDetail(season));
        
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
    
    private void navigateToSeasonDetail(SeasonListResponse.Season season) {
        Intent intent = new Intent(this, SeasonDetailActivity.class);
        intent.putExtra(SeasonDetailActivity.EXTRA_SEASON_GUID, season.getGuid());
        intent.putExtra(SeasonDetailActivity.EXTRA_TV_TITLE, mediaTitle);
        intent.putExtra(SeasonDetailActivity.EXTRA_TV_GUID, mediaGuid);
        intent.putExtra(SeasonDetailActivity.EXTRA_SEASON_NUMBER, season.getSeasonNumber());
        // 传递豆瓣ID用于弹幕
        if (mediaDetail != null && mediaDetail.getDoubanId() > 0) {
            intent.putExtra("douban_id", mediaDetail.getDoubanId());
        }
        startActivity(intent);
    }
    
    private void loadEpisodeListFallback() {
        int episodeCount = 0;
        if (mediaDetail != null) {
            episodeCount = mediaDetail.getLocalNumberOfEpisodes() > 0 ? 
                    mediaDetail.getLocalNumberOfEpisodes() : mediaDetail.getNumberOfEpisodes();
        }
        
        if (episodeCount > 0) {
            createEpisodeGrid(episodeCount);
        }
    }
    
    private void createEpisodeGrid(int episodeCount) {
        seasonContainer.removeAllViews();
        
        TextView episodeTitle = new TextView(this);
        episodeTitle.setText("剧集");
        episodeTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        episodeTitle.setTextColor(getColor(R.color.tv_text_primary));
        seasonContainer.addView(episodeTitle);
        
        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        int columns = 10;
        int rows = (int) Math.ceil((double) episodeCount / columns);
        
        for (int row = 0; row < rows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            
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
        params.setMargins(0, 0, margin, margin);
        button.setLayoutParams(params);
        
        button.setOnClickListener(v -> startPlayEpisode(episodeNumber));
        
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
    
    private void updateUI(MediaDetailResponse detail) {
        titleTextView.setText(detail.getTitle());
        mediaTitle = detail.getTitle();
        
        String subtitle = detail.getType();
        String year = "";
        if (detail.getReleaseDate() != null && detail.getReleaseDate().length() >= 4) {
            year = " • " + detail.getReleaseDate().substring(0, 4);
        } else if (detail.getAirDate() != null && detail.getAirDate().length() >= 4) {
             year = " • " + detail.getAirDate().substring(0, 4);
        }
        subtitleTextView.setText(subtitle + year);
        
        if (detail.getVoteAverage() > 0) {
            ratingTextView.setText("⭐ " + String.format("%.1f", detail.getVoteAverage()));
        }
        
        if (detail.getRuntime() > 0) {
            durationTextView.setText(detail.getRuntime() + " min");
            durationTextView.setVisibility(View.VISIBLE);
        } else {
             durationTextView.setVisibility(View.GONE);
        }

        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            summaryTextView.setText(overview);
        } else {
            summaryTextView.setText("暂无简介");
        }
        
        if (detail.getPoster() != null && !detail.getPoster().isEmpty()) {
             String posterUrl = detail.getPoster();
             if (!posterUrl.startsWith("http")) {
                 posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=400";
             }
             Glide.with(this).load(posterUrl).placeholder(R.drawable.bg_card).into(posterImageView);
        }
    }
    
    private void onPlayButtonClick() {
        if (isTVShow()) {
            if (seasons != null && !seasons.isEmpty()) {
                navigateToSeasonDetail(seasons.get(0));
            } else {
                startPlayEpisode(1);
            }
        } else {
            startPlayMovie();
        }
    }
    
    private void startPlayMovie() {
        Toast.makeText(this, "正在加载...", Toast.LENGTH_SHORT).show();
        startPlayItem(mediaGuid, mediaTitle, 0);
    }
    
    private void startPlayEpisode(int episodeNumber) {
        Toast.makeText(this, "正在加载第" + episodeNumber + "集...", Toast.LENGTH_SHORT).show();
        
        mediaManager.getEpisodeList(mediaGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodes) {
                EpisodeListResponse.Episode target = null;
                if (episodes != null) {
                    for (EpisodeListResponse.Episode ep : episodes) {
                        if (ep.getEpisodeNumber() == episodeNumber) {
                            target = ep;
                            break;
                        }
                    }
                }
                
                if (target != null) {
                    String epTitle = target.getTitle() != null ? target.getTitle() : ("第" + episodeNumber + "集");
                    startPlayItem(target.getGuid(), epTitle, episodeNumber);
                } else {
                    runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "未找到该集", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(String error) {
                 runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "加载剧集失败", Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private void startPlayItem(String itemGuid, String title, int episodeNumber) {
        mediaManager.startPlay(itemGuid, new MediaManager.MediaCallback<String>() {
            @Override
            public void onSuccess(String playUrl) {
                runOnUiThread(() -> navigateToVideoPlayer(playUrl, title, itemGuid, episodeNumber));
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MediaDetailActivity.this, "播放失败: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void navigateToVideoPlayer(String playUrl, String title, String itemGuid, int episodeNumber) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", playUrl);
        intent.putExtra("video_title", title);
        intent.putExtra("media_title", mediaTitle);
        intent.putExtra("episode_guid", itemGuid);
        
        if (mediaDetail != null) {
             intent.putExtra("douban_id", String.valueOf(mediaDetail.getDoubanId()));
             intent.putExtra("parent_guid", mediaDetail.getParentGuid());
             intent.putExtra("season_number", mediaDetail.getSeasonNumber());
        }
        intent.putExtra("episode_number", episodeNumber);
        
        startActivity(intent);
    }
}
