package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.PlayStartInfo;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.utils.FormatUtils;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.List;

/**
 * 📺 Episode Detail Activity
 * 显示剧集详情页（第三层详情页）
 * Web端URL格式: /v/tv/episode/{episode_guid}
 */
public class EpisodeDetailActivity extends AppCompatActivity {
    private static final String TAG = "EpisodeDetailActivity";
    
    public static final String EXTRA_EPISODE_GUID = "episode_guid";
    public static final String EXTRA_TV_TITLE = "tv_title";
    public static final String EXTRA_SEASON_NUMBER = "season_number";
    public static final String EXTRA_EPISODE_NUMBER = "episode_number";
    public static final String EXTRA_SEASON_GUID = "season_guid";
    
    // UI
    private ImageView backgroundPoster;
    private ImageView episodeThumbnail;
    private TextView episodeHeader;
    private TextView episodeTitle;
    private TextView playButton;
    private TextView episodeMeta;
    private TextView episodeOverview;
    private TextView filePath;
    private TextView fileSize;
    private TextView fileCreated;
    private TextView fileAdded;
    private TextView videoInfo;
    private TextView audioInfo;
    private TextView subtitleInfo;
    
    // Data
    private String episodeGuid;
    private String tvTitle;
    private String seasonGuid;
    private int seasonNumber;
    private int episodeNumber;
    private long doubanId;
    private MediaManager mediaManager;
    private MediaDetailResponse episodeDetail;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_episode_detail);
        
        Intent intent = getIntent();
        episodeGuid = intent.getStringExtra(EXTRA_EPISODE_GUID);
        tvTitle = intent.getStringExtra(EXTRA_TV_TITLE);
        seasonGuid = intent.getStringExtra(EXTRA_SEASON_GUID);
        seasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, 1);
        episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1);
        doubanId = intent.getLongExtra("douban_id", 0);
        
        if (episodeGuid == null || episodeGuid.isEmpty()) {
            Toast.makeText(this, "Invalid Episode GUID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        mediaManager = new MediaManager(this);
        loadEpisodeDetail();
    }
    
    private void initViews() {
        backgroundPoster = findViewById(R.id.background_poster);
        episodeThumbnail = findViewById(R.id.episode_thumbnail);
        episodeHeader = findViewById(R.id.episode_header);
        episodeTitle = findViewById(R.id.episode_title);
        playButton = findViewById(R.id.play_button);
        episodeMeta = findViewById(R.id.episode_meta);
        episodeOverview = findViewById(R.id.episode_overview);
        filePath = findViewById(R.id.file_path);
        fileSize = findViewById(R.id.file_size);
        fileCreated = findViewById(R.id.file_created);
        fileAdded = findViewById(R.id.file_added);
        videoInfo = findViewById(R.id.video_info);
        audioInfo = findViewById(R.id.audio_info);
        subtitleInfo = findViewById(R.id.subtitle_info);
        
        // 设置初始值
        String header = tvTitle != null ? tvTitle : "Loading...";
        header += " 第" + seasonNumber + "季·第" + episodeNumber + "集";
        episodeHeader.setText(header);
        
        // 播放按钮点击事件
        playButton.setOnClickListener(v -> playEpisode());
        playButton.requestFocus();
    }
    
    private void loadEpisodeDetail() {
        // 加载剧集详情
        mediaManager.getItemDetail(episodeGuid, new MediaManager.MediaCallback<MediaDetailResponse>() {
            @Override
            public void onSuccess(MediaDetailResponse detail) {
                episodeDetail = detail;
                runOnUiThread(() -> updateUI(detail));
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load episode detail: " + error);
                runOnUiThread(() -> Toast.makeText(EpisodeDetailActivity.this, 
                    "加载失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
        
        // 加载流信息
        loadStreamInfo();
    }
    
    private void loadStreamInfo() {
        mediaManager.getStreamList(episodeGuid, new MediaManager.MediaCallback<StreamListResponse>() {
            @Override
            public void onSuccess(StreamListResponse streamResponse) {
                runOnUiThread(() -> updateStreamInfo(streamResponse));
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load stream info: " + error);
            }
        });
    }
    
    private void updateUI(MediaDetailResponse detail) {
        // 剧集标题
        String title = detail.getTitle();
        if (title == null || title.isEmpty()) {
            title = "第" + episodeNumber + "集";
        }
        episodeTitle.setText(title);
        
        // 元信息：年份 · 时长
        StringBuilder meta = new StringBuilder();
        if (detail.getAirDate() != null && detail.getAirDate().length() >= 4) {
            meta.append(detail.getAirDate().substring(0, 4));
        }
        if (detail.getRuntime() > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(FormatUtils.formatDuration(detail.getRuntime() * 60)); // runtime是分钟
        }
        episodeMeta.setText(meta.toString());
        
        // 简介
        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            episodeOverview.setText(overview);
        } else {
            episodeOverview.setText("暂无简介");
        }
        
        // 加载缩略图 - 使用 poster 作为缩略图
        String posterPath = detail.getPoster();
        if (posterPath != null && !posterPath.isEmpty()) {
            String imageUrl = posterPath;
            if (!imageUrl.startsWith("http")) {
                imageUrl = SharedPreferencesManager.getImageServiceUrl() + posterPath + "?w=640";
            }
            Glide.with(this).load(imageUrl).placeholder(R.drawable.bg_card).into(episodeThumbnail);
            
            // 背景图（不使用模糊，直接加载）
            Glide.with(this)
                .load(imageUrl)
                .into(backgroundPoster);
        }
    }
    
    private void updateStreamInfo(StreamListResponse streamResponse) {
        if (streamResponse == null || streamResponse.getData() == null) {
            return;
        }
        
        StreamListResponse.StreamData data = streamResponse.getData();
        
        // 文件信息 - 使用 files 列表
        List<StreamListResponse.FileStream> files = data.getFiles();
        if (files != null && !files.isEmpty()) {
            StreamListResponse.FileStream file = files.get(0);
            filePath.setText(file.getPath() != null ? file.getPath() : "-");
            fileSize.setText(file.getSize() > 0 ? 
                FormatUtils.formatFileSize(file.getSize()) : "-");
            fileCreated.setText(file.getFileBirthTime() > 0 ? 
                FormatUtils.formatDate(file.getFileBirthTime()) : "-");
            fileAdded.setText(file.getCreateTime() > 0 ? 
                FormatUtils.formatDate(file.getCreateTime()) : "-");
        }
        
        // 视频信息
        List<StreamListResponse.VideoStream> videoStreams = data.getVideoStreams();
        if (videoStreams != null && !videoStreams.isEmpty()) {
            StreamListResponse.VideoStream video = videoStreams.get(0);
            videoInfo.setText(FormatUtils.formatVideoInfo(video));
        } else {
            videoInfo.setText("-");
        }
        
        // 音频信息
        List<StreamListResponse.AudioStream> audioStreams = data.getAudioStreams();
        if (audioStreams != null && !audioStreams.isEmpty()) {
            StringBuilder audioText = new StringBuilder();
            for (int i = 0; i < audioStreams.size(); i++) {
                if (i > 0) audioText.append("\n");
                audioText.append(FormatUtils.formatAudioInfo(audioStreams.get(i)));
            }
            audioInfo.setText(audioText.toString());
        } else {
            audioInfo.setText("-");
        }
        
        // 字幕信息
        List<StreamListResponse.SubtitleStream> subtitleStreams = data.getSubtitleStreams();
        if (subtitleStreams != null && !subtitleStreams.isEmpty()) {
            StringBuilder subText = new StringBuilder();
            for (int i = 0; i < subtitleStreams.size(); i++) {
                if (i > 0) subText.append("\n");
                subText.append(FormatUtils.formatSubtitleInfo(subtitleStreams.get(i)));
            }
            subtitleInfo.setText(subText.toString());
        } else {
            subtitleInfo.setText("无字幕");
        }
    }
    
    private void playEpisode() {
        Toast.makeText(this, "正在加载...", Toast.LENGTH_SHORT).show();
        
        mediaManager.startPlayWithInfo(episodeGuid, new MediaManager.MediaCallback<PlayStartInfo>() {
            @Override
            public void onSuccess(PlayStartInfo playInfo) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(EpisodeDetailActivity.this, VideoPlayerActivity.class);
                    intent.putExtra("video_url", playInfo.getPlayUrl());
                    intent.putExtra("video_title", episodeTitle.getText().toString());
                    intent.putExtra("media_title", tvTitle);
                    intent.putExtra("tv_title", tvTitle);
                    intent.putExtra("episode_guid", episodeGuid);
                    intent.putExtra("season_guid", seasonGuid);
                    intent.putExtra("episode_number", episodeNumber);
                    intent.putExtra("season_number", seasonNumber);
                    intent.putExtra("resume_position", playInfo.getResumePositionSeconds());
                    intent.putExtra("video_guid", playInfo.getVideoGuid());
                    intent.putExtra("audio_guid", playInfo.getAudioGuid());
                    intent.putExtra("media_guid", playInfo.getMediaGuid());
                    
                    if (doubanId > 0) {
                        intent.putExtra("douban_id", String.valueOf(doubanId));
                    }
                    
                    startActivity(intent);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(EpisodeDetailActivity.this, "播放失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
