package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.List;

/**
 * 📺 Season Detail Activity
 * 显示季详情和剧集列表（第二层详情页）
 * Web端URL格式: /v/tv/season/{season_guid}
 */
public class SeasonDetailActivity extends AppCompatActivity {
    private static final String TAG = "SeasonDetailActivity";
    
    public static final String EXTRA_SEASON_GUID = "season_guid";
    public static final String EXTRA_TV_TITLE = "tv_title";
    public static final String EXTRA_SEASON_NUMBER = "season_number";
    public static final String EXTRA_TV_GUID = "tv_guid";
    
    // UI
    private ImageView posterImageView;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView summaryTextView;
    private TextView playButtonTextView;
    private LinearLayout episodeContainer;
    
    // Data
    private String seasonGuid;
    private String tvTitle;
    private String tvGuid;
    private int seasonNumber;
    private long doubanId; // 从TV传递过来的豆瓣ID
    private MediaManager mediaManager;
    private MediaDetailResponse seasonDetail;
    private List<EpisodeListResponse.Episode> episodes;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        seasonGuid = intent.getStringExtra(EXTRA_SEASON_GUID);
        tvTitle = intent.getStringExtra(EXTRA_TV_TITLE);
        tvGuid = intent.getStringExtra(EXTRA_TV_GUID);
        seasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, 1);
        doubanId = intent.getLongExtra("douban_id", 0); // 从TV传递过来的豆瓣ID
        
        if (seasonGuid == null || seasonGuid.isEmpty()) {
            Toast.makeText(this, "Invalid Season GUID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 🚀 优化：先显示简单布局，再异步加载数据
        createLayout();
        mediaManager = new MediaManager(this);
        
        // 延迟加载数据，让UI先渲染
        getWindow().getDecorView().post(this::loadSeasonDetail);
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
        
        // Title
        titleTextView = new TextView(this);
        titleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_title));
        titleTextView.setTextColor(getColor(R.color.tv_text_primary));
        titleTextView.setText(tvTitle != null ? tvTitle : "Loading...");
        contentLayout.addView(titleTextView);
        
        // Subtitle (Season info)
        subtitleTextView = new TextView(this);
        subtitleTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        subtitleTextView.setTextColor(getColor(R.color.tv_text_secondary));
        subtitleTextView.setText("第 " + seasonNumber + " 季");
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_small);
        subtitleTextView.setLayoutParams(subtitleParams);
        contentLayout.addView(subtitleTextView);
        
        // Play Button
        playButtonTextView = new TextView(this);
        playButtonTextView.setText("▶ 播放第1集");
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
        
        playButtonTextView.setOnClickListener(v -> {
            if (episodes != null && !episodes.isEmpty()) {
                playEpisode(episodes.get(0));
            }
        });
        contentLayout.addView(playButtonTextView);
        
        // Summary
        summaryTextView = new TextView(this);
        summaryTextView.setTextSize(getResources().getDimension(R.dimen.tv_text_size_medium));
        summaryTextView.setTextColor(getColor(R.color.tv_text_primary));
        summaryTextView.setMaxLines(4);
        summaryTextView.setText("Loading...");
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        summaryTextView.setLayoutParams(summaryParams);
        contentLayout.addView(summaryTextView);
        
        // Episode Container
        episodeContainer = new LinearLayout(this);
        episodeContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams episodeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        episodeParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_large);
        episodeContainer.setLayoutParams(episodeParams);
        contentLayout.addView(episodeContainer);
        
        mainLayout.addView(contentLayout);
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }
    
    private void loadSeasonDetail() {
        // Load season detail
        mediaManager.getItemDetail(seasonGuid, new MediaManager.MediaCallback<MediaDetailResponse>() {
            @Override
            public void onSuccess(MediaDetailResponse detail) {
                seasonDetail = detail;
                runOnUiThread(() -> updateUI(detail));
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load season detail: " + error);
            }
        });
        
        // Load episode list
        loadEpisodeList();
    }
    
    private void loadEpisodeList() {
        mediaManager.getEpisodeList(seasonGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodeList) {
                episodes = episodeList;
                runOnUiThread(() -> {
                    createEpisodeList();
                    if (!episodeList.isEmpty()) {
                        playButtonTextView.setText("▶ 播放第" + episodeList.get(0).getEpisodeNumber() + "集");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SeasonDetailActivity.this, "加载剧集失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateUI(MediaDetailResponse detail) {
        if (detail.getTitle() != null) {
            titleTextView.setText(tvTitle != null ? tvTitle : detail.getTitle());
        }
        
        String subtitle = "第 " + seasonNumber + " 季";
        if (detail.getAirDate() != null && detail.getAirDate().length() >= 4) {
            subtitle += " · " + detail.getAirDate().substring(0, 4);
        }
        subtitleTextView.setText(subtitle);
        
        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            summaryTextView.setText(overview);
        } else {
            summaryTextView.setVisibility(View.GONE);
        }
        
        if (detail.getPoster() != null && !detail.getPoster().isEmpty()) {
            String posterUrl = detail.getPoster();
            if (!posterUrl.startsWith("http")) {
                posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=400";
            }
            Glide.with(this).load(posterUrl).placeholder(R.drawable.bg_card).into(posterImageView);
        }
    }
    
    private void createEpisodeList() {
        episodeContainer.removeAllViews();
        
        if (episodes == null || episodes.isEmpty()) {
            return;
        }
        
        // Title
        TextView episodeTitle = new TextView(this);
        episodeTitle.setText("剧集列表 (" + episodes.size() + "集)");
        episodeTitle.setTextSize(getResources().getDimension(R.dimen.tv_text_size_large));
        episodeTitle.setTextColor(getColor(R.color.tv_text_primary));
        episodeContainer.addView(episodeTitle);
        
        // Episode grid
        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gridParams.topMargin = getResources().getDimensionPixelSize(R.dimen.tv_margin_medium);
        gridContainer.setLayoutParams(gridParams);
        
        int columns = 10;
        int rows = (int) Math.ceil((double) episodes.size() / columns);
        
        for (int row = 0; row < rows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index < episodes.size()) {
                    EpisodeListResponse.Episode ep = episodes.get(index);
                    TextView episodeButton = createEpisodeButton(ep);
                    rowLayout.addView(episodeButton);
                }
            }
            gridContainer.addView(rowLayout);
        }
        
        episodeContainer.addView(gridContainer);
    }
    
    private TextView createEpisodeButton(EpisodeListResponse.Episode episode) {
        TextView button = new TextView(this);
        button.setText(String.valueOf(episode.getEpisodeNumber()));
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
        
        button.setOnClickListener(v -> playEpisode(episode));
        
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
    
    private void playEpisode(EpisodeListResponse.Episode episode) {
        Toast.makeText(this, "正在加载第" + episode.getEpisodeNumber() + "集...", Toast.LENGTH_SHORT).show();
        
        mediaManager.startPlay(episode.getGuid(), new MediaManager.MediaCallback<String>() {
            @Override
            public void onSuccess(String playUrl) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(SeasonDetailActivity.this, VideoPlayerActivity.class);
                    intent.putExtra("video_url", playUrl);
                    intent.putExtra("video_title", episode.getTitle() != null ? episode.getTitle() : "第" + episode.getEpisodeNumber() + "集");
                    intent.putExtra("media_title", tvTitle);
                    intent.putExtra("tv_title", tvTitle); // 电视剧标题用于弹幕搜索
                    intent.putExtra("episode_guid", episode.getGuid());
                    intent.putExtra("season_guid", seasonGuid);
                    intent.putExtra("episode_number", episode.getEpisodeNumber());
                    intent.putExtra("season_number", seasonNumber);
                    
                    // 优先使用从TV传递过来的doubanId，如果没有则尝试从seasonDetail获取
                    long effectiveDoubanId = doubanId;
                    if (effectiveDoubanId <= 0 && seasonDetail != null) {
                        effectiveDoubanId = seasonDetail.getDoubanId();
                    }
                    if (effectiveDoubanId > 0) {
                        intent.putExtra("douban_id", String.valueOf(effectiveDoubanId));
                    }
                    
                    if (seasonDetail != null) {
                        intent.putExtra("parent_guid", seasonDetail.getParentGuid());
                    }
                    
                    startActivity(intent);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SeasonDetailActivity.this, "播放失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
