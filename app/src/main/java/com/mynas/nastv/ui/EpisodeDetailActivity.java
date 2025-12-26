package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
 * Episode Detail Activity
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
    
    // 新增简要信息UI
    private TextView subtitleBrief;
    private TextView audioBrief;
    private LinearLayout tagsContainer;
    
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
            ToastUtils.show(this, "Invalid Episode GUID");
            finish();
            return;
        }
        
        initViews();
        mediaManager = new MediaManager(this);
        loadEpisodeDetail();
    }
    
    private void initViews() {
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
        
        // 新增简要信息
        subtitleBrief = findViewById(R.id.subtitle_brief);
        audioBrief = findViewById(R.id.audio_brief);
        tagsContainer = findViewById(R.id.tags_container);
        
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
                runOnUiThread(() -> ToastUtils.show(EpisodeDetailActivity.this, 
                    "加载失败: " + error));
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
        
        // 加载缩略图 - 使用poster，需要拼接服务地址
        String posterPath = detail.getPoster();
        Log.d(TAG, "剧集详情 poster字段值: " + posterPath);
        Log.d(TAG, "剧集详情 still字段值: " + detail.getStill());
        if (posterPath != null && !posterPath.isEmpty()) {
            String imageUrl = posterPath;
            if (!imageUrl.startsWith("http")) {
                imageUrl = SharedPreferencesManager.getImageServiceUrl() + posterPath + "?w=640";
            }
            Log.d(TAG, "加载剧集缩略图: " + imageUrl);
            // 使用asBitmap()强制作为图片解码，避免webp被误识别为视频
            Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .placeholder(R.drawable.bg_card)
                .into(episodeThumbnail);
        } else {
            Log.d(TAG, "poster为空，尝试使用still");
            String stillPath = detail.getStill();
            if (stillPath != null && !stillPath.isEmpty()) {
                String imageUrl = stillPath;
                if (!imageUrl.startsWith("http")) {
                    imageUrl = SharedPreferencesManager.getImageServiceUrl() + stillPath + "?w=640";
                }
                Log.d(TAG, "加载剧集剧照: " + imageUrl);
                Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .placeholder(R.drawable.bg_card)
                    .into(episodeThumbnail);
            }
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
            
            // 更新分辨率标签
            updateVideoTags(video, data.getAudioStreams());
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
            
            // 更新音轨简要信息
            audioBrief.setText(audioStreams.size() > 1 ? audioStreams.size() + "条音轨" : "1条音轨");
        } else {
            audioInfo.setText("-");
            audioBrief.setText("未知音轨");
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
            
            // 更新字幕简要信息
            subtitleBrief.setText("字幕 " + subtitleStreams.size() + "条");
        } else {
            subtitleInfo.setText("无字幕");
            subtitleBrief.setText("无字幕");
        }
    }
    
    /**
     * 更新视频标签（分辨率、HDR、音频格式）
     */
    private void updateVideoTags(StreamListResponse.VideoStream video, List<StreamListResponse.AudioStream> audioStreams) {
        tagsContainer.removeAllViews();
        
        // 分辨率标签
        int height = video.getHeight();
        String resolutionTag = null;
        if (height >= 2160) {
            resolutionTag = "4K";
        } else if (height >= 1080) {
            resolutionTag = "1080p";
        } else if (height >= 720) {
            resolutionTag = "720";
        } else if (height > 0) {
            resolutionTag = height + "p";
        }
        if (resolutionTag != null) {
            addTag(resolutionTag);
        }
        
        // HDR标签
        String colorRangeType = video.getColorRangeType();
        if (colorRangeType != null && colorRangeType.toUpperCase().contains("HDR")) {
            addTag("HDR");
        } else {
            addTag("SDR");
        }
        
        // 音频格式标签
        if (audioStreams != null && !audioStreams.isEmpty()) {
            StreamListResponse.AudioStream audio = audioStreams.get(0);
            int channels = audio.getChannels();
            if (channels >= 6) {
                addTag("5.1");
            } else if (channels == 2) {
                addTag("立体声");
            } else if (channels == 1) {
                addTag("单声道");
            }
        }
    }
    
    /**
     * 添加标签到容器
     */
    private void addTag(String text) {
        TextView tag = new TextView(this);
        tag.setText(text);
        tag.setTextSize(11);
        tag.setTextColor(getColor(R.color.tv_text_secondary));
        tag.setBackgroundResource(R.drawable.tag_background);
        tag.setPadding(12, 4, 12, 4);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 8, 0);
        tag.setLayoutParams(params);
        
        tagsContainer.addView(tag);
    }
    
    private void playEpisode() {
        ToastUtils.show(this, "正在加载...");
        
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
                    ToastUtils.show(EpisodeDetailActivity.this, "播放失败: " + error);
                });
            }
        });
    }
}
