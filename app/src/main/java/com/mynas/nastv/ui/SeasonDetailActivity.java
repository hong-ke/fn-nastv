package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.adapter.EpisodeCardAdapter;
import com.mynas.nastv.adapter.PersonAdapter;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PersonInfo;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
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
    private LinearLayout personContainer;
    private HorizontalScrollView episodeGroupScroll;
    private LinearLayout episodeGroupTabs;
    private RecyclerView episodeRecyclerView;
    private RecyclerView directorRecyclerView;
    private RecyclerView actorRecyclerView;
    private RecyclerView writerRecyclerView;
    
    // Adapters
    private EpisodeCardAdapter episodeAdapter;
    private PersonAdapter directorAdapter;
    private PersonAdapter actorAdapter;
    private PersonAdapter writerAdapter;
    
    // Data
    private String seasonGuid;
    private String tvTitle;
    private String tvGuid;
    private int seasonNumber;
    private int currentEpisodeNumber = -1; // 当前播放的剧集号
    private long doubanId; // 从TV传递过来的豆瓣ID
    private MediaManager mediaManager;
    private MediaDetailResponse seasonDetail;
    private List<EpisodeListResponse.Episode> episodes;
    
    // 分组相关
    private static final int GROUP_SIZE = 30; // 每组30集
    private int currentGroupIndex = 0;
    private List<TextView> groupTabViews = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        seasonGuid = intent.getStringExtra(EXTRA_SEASON_GUID);
        tvTitle = intent.getStringExtra(EXTRA_TV_TITLE);
        tvGuid = intent.getStringExtra(EXTRA_TV_GUID);
        seasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, 1);
        doubanId = intent.getLongExtra("douban_id", 0); // 从TV传递过来的豆瓣ID
        currentEpisodeNumber = intent.getIntExtra("current_episode", -1); // 当前播放的剧集号
        
        if (seasonGuid == null || seasonGuid.isEmpty()) {
            ToastUtils.show(this, "Invalid Season GUID");
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
        setContentView(R.layout.activity_season_detail);
        
        // 绑定视图
        posterImageView = findViewById(R.id.season_poster);
        titleTextView = findViewById(R.id.season_title);
        subtitleTextView = findViewById(R.id.season_subtitle);
        summaryTextView = findViewById(R.id.season_summary);
        playButtonTextView = findViewById(R.id.season_play_button);
        episodeContainer = findViewById(R.id.episode_container);
        personContainer = findViewById(R.id.person_container);
        episodeGroupScroll = findViewById(R.id.episode_group_scroll);
        episodeGroupTabs = findViewById(R.id.episode_group_tabs);
        episodeRecyclerView = findViewById(R.id.episode_recycler_view);
        directorRecyclerView = findViewById(R.id.director_recycler_view);
        actorRecyclerView = findViewById(R.id.actor_recycler_view);
        writerRecyclerView = findViewById(R.id.writer_recycler_view);
        
        // 设置初始值
        titleTextView.setText(tvTitle != null ? tvTitle : "Loading...");
        subtitleTextView.setText("第 " + seasonNumber + " 季");
        
        // 设置播放按钮点击事件
        playButtonTextView.setOnClickListener(v -> {
            if (episodes != null && !episodes.isEmpty()) {
                playEpisode(episodes.get(0));
            }
        });
        
        // 初始化剧集列表适配器 - 使用横向卡片适配器
        episodeAdapter = new EpisodeCardAdapter(this::onEpisodeClick);
        episodeRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        episodeRecyclerView.setAdapter(episodeAdapter);
        episodeRecyclerView.setNestedScrollingEnabled(false);
        
        // 初始化演职人员适配器
        directorAdapter = new PersonAdapter();
        directorRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        directorRecyclerView.setAdapter(directorAdapter);
        
        actorAdapter = new PersonAdapter();
        actorRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        actorRecyclerView.setAdapter(actorAdapter);
        
        writerAdapter = new PersonAdapter();
        writerRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        writerRecyclerView.setAdapter(writerAdapter);
    }
    
    private void onEpisodeClick(EpisodeListResponse.Episode episode, int position) {
        // 跳转到剧集详情页
        navigateToEpisodeDetail(episode);
    }
    
    private void navigateToEpisodeDetail(EpisodeListResponse.Episode episode) {
        Intent intent = new Intent(this, EpisodeDetailActivity.class);
        intent.putExtra(EpisodeDetailActivity.EXTRA_EPISODE_GUID, episode.getGuid());
        intent.putExtra(EpisodeDetailActivity.EXTRA_TV_TITLE, tvTitle);
        intent.putExtra(EpisodeDetailActivity.EXTRA_SEASON_NUMBER, seasonNumber);
        intent.putExtra(EpisodeDetailActivity.EXTRA_EPISODE_NUMBER, episode.getEpisodeNumber());
        intent.putExtra(EpisodeDetailActivity.EXTRA_SEASON_GUID, seasonGuid);
        
        // 传递豆瓣ID
        long effectiveDoubanId = doubanId;
        if (effectiveDoubanId <= 0 && seasonDetail != null) {
            effectiveDoubanId = seasonDetail.getDoubanId();
        }
        if (effectiveDoubanId > 0) {
            intent.putExtra("douban_id", effectiveDoubanId);
        }
        
        startActivity(intent);
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
        
        // Load person list (演职人员)
        loadPersonList();
    }
    
    private void loadPersonList() {
        mediaManager.getPersonList(seasonGuid, new MediaManager.MediaCallback<List<PersonInfo>>() {
            @Override
            public void onSuccess(List<PersonInfo> personList) {
                runOnUiThread(() -> {
                    if (personList != null && !personList.isEmpty()) {
                        // 按类型分组
                        List<PersonInfo> directors = new ArrayList<>();
                        List<PersonInfo> actors = new ArrayList<>();
                        List<PersonInfo> writers = new ArrayList<>();
                        
                        for (PersonInfo person : personList) {
                            if (person.isDirector()) {
                                directors.add(person);
                            } else if (person.isActor()) {
                                actors.add(person);
                            } else if (person.isWriter()) {
                                writers.add(person);
                            }
                        }
                        
                        // 更新UI
                        if (!directors.isEmpty()) {
                            directorAdapter.updatePersons(directors);
                            findViewById(R.id.director_section).setVisibility(View.VISIBLE);
                        }
                        if (!actors.isEmpty()) {
                            actorAdapter.updatePersons(actors);
                            findViewById(R.id.actor_section).setVisibility(View.VISIBLE);
                        }
                        if (!writers.isEmpty()) {
                            writerAdapter.updatePersons(writers);
                            findViewById(R.id.writer_section).setVisibility(View.VISIBLE);
                        }
                        
                        personContainer.setVisibility(View.VISIBLE);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load person list: " + error);
            }
        });
    }
    
    private void loadEpisodeList() {
        mediaManager.getEpisodeList(seasonGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodeList) {
                episodes = episodeList;
                runOnUiThread(() -> {
                    // 创建分组标签
                    setupEpisodeGroupTabs(episodeList.size());
                    
                    // 显示第一组剧集
                    showEpisodeGroup(0);
                    
                    if (currentEpisodeNumber > 0) {
                        episodeAdapter.setCurrentEpisode(currentEpisodeNumber);
                        // 自动切换到当前剧集所在的分组
                        int groupIndex = (currentEpisodeNumber - 1) / GROUP_SIZE;
                        if (groupIndex != currentGroupIndex) {
                            selectGroupTab(groupIndex);
                        }
                    }
                    
                    if (!episodeList.isEmpty()) {
                        playButtonTextView.setText("▶ 播放第" + episodeList.get(0).getEpisodeNumber() + "集");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    ToastUtils.show(SeasonDetailActivity.this, "加载剧集失败: " + error);
                });
            }
        });
    }
    
    /**
     * 设置剧集分组标签 (1-30 / 31-60 / 61-73)
     */
    private void setupEpisodeGroupTabs(int totalEpisodes) {
        episodeGroupTabs.removeAllViews();
        groupTabViews.clear();
        
        if (totalEpisodes <= GROUP_SIZE) {
            // 只有一组，不显示标签
            episodeGroupScroll.setVisibility(View.GONE);
            return;
        }
        
        episodeGroupScroll.setVisibility(View.VISIBLE);
        
        int groupCount = (totalEpisodes + GROUP_SIZE - 1) / GROUP_SIZE;
        
        for (int i = 0; i < groupCount; i++) {
            int start = i * GROUP_SIZE + 1;
            int end = Math.min((i + 1) * GROUP_SIZE, totalEpisodes);
            
            TextView tabView = createGroupTab(start + "-" + end, i);
            episodeGroupTabs.addView(tabView);
            groupTabViews.add(tabView);
            
            // 添加分隔符
            if (i < groupCount - 1) {
                TextView separator = new TextView(this);
                separator.setText(" / ");
                separator.setTextColor(Color.parseColor("#666666"));
                separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                separator.setGravity(Gravity.CENTER);
                episodeGroupTabs.addView(separator);
            }
        }
        
        // 默认选中第一个
        updateGroupTabSelection(0);
    }
    
    /**
     * 创建分组标签按钮 - 美化版：圆角 + 透明度
     */
    private TextView createGroupTab(String text, int groupIndex) {
        TextView tabView = new TextView(this);
        tabView.setText(text);
        tabView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tabView.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        tabView.setFocusable(true);
        tabView.setClickable(true);
        tabView.setGravity(android.view.Gravity.CENTER);
        
        tabView.setOnClickListener(v -> selectGroupTab(groupIndex));
        tabView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
                // 聚焦时添加边框效果
                updateTabFocusState((TextView) v, true, v == groupTabViews.get(currentGroupIndex));
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                updateTabFocusState((TextView) v, false, v == groupTabViews.get(currentGroupIndex));
            }
        });
        
        return tabView;
    }
    
    /**
     * 更新标签聚焦状态
     */
    private void updateTabFocusState(TextView tabView, boolean hasFocus, boolean isSelected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(16));
        
        if (isSelected) {
            bg.setColor(Color.parseColor("#CC2196F3")); // 选中：蓝色 80%透明
            if (hasFocus) {
                bg.setStroke(dpToPx(2), Color.parseColor("#FFFFFF"));
            }
        } else if (hasFocus) {
            bg.setColor(Color.parseColor("#662196F3")); // 聚焦：蓝色 40%透明
            bg.setStroke(dpToPx(2), Color.parseColor("#2196F3"));
        } else {
            bg.setColor(Color.parseColor("#40FFFFFF")); // 默认：白色 25%透明
        }
        
        tabView.setBackground(bg);
    }
    
    /**
     * 选择分组标签
     */
    private void selectGroupTab(int groupIndex) {
        if (groupIndex == currentGroupIndex) return;
        
        currentGroupIndex = groupIndex;
        updateGroupTabSelection(groupIndex);
        showEpisodeGroup(groupIndex);
    }
    
    /**
     * 更新分组标签选中状态 - 美化版：圆角 + 透明度
     */
    private void updateGroupTabSelection(int selectedIndex) {
        for (int i = 0; i < groupTabViews.size(); i++) {
            TextView tabView = groupTabViews.get(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dpToPx(16));
            
            if (i == selectedIndex) {
                // 选中状态：蓝色半透明背景
                tabView.setTextColor(Color.WHITE);
                bg.setColor(Color.parseColor("#CC2196F3")); // 蓝色 80%透明
            } else {
                // 未选中状态：深色半透明背景
                tabView.setTextColor(Color.parseColor("#CCFFFFFF")); // 白色 80%透明
                bg.setColor(Color.parseColor("#40FFFFFF")); // 白色 25%透明
            }
            tabView.setBackground(bg);
        }
    }
    
    /**
     * 显示指定分组的剧集
     */
    private void showEpisodeGroup(int groupIndex) {
        if (episodes == null || episodes.isEmpty()) return;
        
        int start = groupIndex * GROUP_SIZE;
        int end = Math.min(start + GROUP_SIZE, episodes.size());
        
        List<EpisodeListResponse.Episode> groupEpisodes = episodes.subList(start, end);
        episodeAdapter.updateEpisodes(new ArrayList<>(groupEpisodes));
        
        // 滚动到开头
        episodeRecyclerView.scrollToPosition(0);
    }
    
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
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
        
        // 更新剧集列表标题
        TextView episodeListTitle = findViewById(R.id.episode_list_title);
        if (episodeListTitle != null && episodes != null) {
            episodeListTitle.setText("剧集列表 (" + episodes.size() + "集)");
        }
    }
    
    private void playEpisode(EpisodeListResponse.Episode episode) {
        ToastUtils.show(this, "正在加载第" + episode.getEpisodeNumber() + "集...");
        
        mediaManager.startPlayWithInfo(episode.getGuid(), new MediaManager.MediaCallback<com.mynas.nastv.model.PlayStartInfo>() {
            @Override
            public void onSuccess(com.mynas.nastv.model.PlayStartInfo playInfo) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(SeasonDetailActivity.this, VideoPlayerActivity.class);
                    intent.putExtra("video_url", playInfo.getPlayUrl());
                    intent.putExtra("video_title", episode.getTitle() != null ? episode.getTitle() : "第" + episode.getEpisodeNumber() + "集");
                    intent.putExtra("media_title", tvTitle);
                    intent.putExtra("tv_title", tvTitle); // 电视剧标题用于弹幕搜索
                    intent.putExtra("episode_guid", episode.getGuid());
                    intent.putExtra("season_guid", seasonGuid);
                    intent.putExtra("episode_number", episode.getEpisodeNumber());
                    intent.putExtra("season_number", seasonNumber);
                    
                    // 🎬 传递恢复播放位置
                    intent.putExtra("resume_position", playInfo.getResumePositionSeconds());
                    intent.putExtra("video_guid", playInfo.getVideoGuid());
                    intent.putExtra("audio_guid", playInfo.getAudioGuid());
                    intent.putExtra("media_guid", playInfo.getMediaGuid());
                    
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
                    ToastUtils.show(SeasonDetailActivity.this, "播放失败: " + error);
                });
            }
        });
    }
}
