package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;
import com.mynas.nastv.utils.FormatUtils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.PersonInfo;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Media Detail Activity
 * 显示媒体详情（电影/电视剧第一层）
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
    private TextView durationTextView;
    private TextView playButtonTextView;
    private TextView summaryTextView;
    private LinearLayout personContainer;
    private LinearLayout seasonContainer;
    
    // 新增元信息UI
    private TextView yearText;
    private TextView genreText;
    private TextView regionText;
    private TextView runtimeText;
    private TextView mediaTypeTag;
    private TextView subtitleBrief;
    private TextView audioBrief;
    private LinearLayout tagsContainer;
    
    // 文件信息UI
    private View dividerFile;
    private LinearLayout fileInfoSection;
    private TextView filePath;
    private TextView fileSize;
    private TextView fileCreated;
    private TextView fileAdded;
    
    // 视频信息UI
    private View dividerVideo;
    private LinearLayout videoInfoSection;
    private TextView videoCodec;
    private TextView videoResolution;
    private TextView videoFramerate;
    private TextView videoBitrate;
    private TextView videoHdr;
    
    // 音频信息UI
    private View dividerAudio;
    private LinearLayout audioInfoSection;
    private TextView audioCodec;
    private TextView audioChannels;
    private TextView audioSampleRate;
    
    // 字幕信息UI
    private View dividerSubtitle;
    private LinearLayout subtitleInfoSection;
    private TextView subtitleList;
    
    // 流信息缓存
    private StreamListResponse cachedStreamResponse;
    
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
        setContentView(R.layout.activity_media_detail);
        
        Intent intent = getIntent();
        mediaGuid = intent.getStringExtra(EXTRA_MEDIA_GUID);
        mediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
        mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE);
        
        if (mediaGuid == null || mediaGuid.isEmpty()) {
            ToastUtils.show(this, "Invalid Media GUID");
            finish();
            return;
        }
        
        initViews();
        mediaManager = new MediaManager(this);
        loadMediaDetail();
    }
    
    private void initViews() {
        posterImageView = findViewById(R.id.poster_image);
        titleTextView = findViewById(R.id.title_text);
        subtitleTextView = findViewById(R.id.subtitle_text);
        ratingTextView = findViewById(R.id.rating_text);
        durationTextView = findViewById(R.id.duration_text);
        playButtonTextView = findViewById(R.id.play_button);
        summaryTextView = findViewById(R.id.summary_text);
        personContainer = findViewById(R.id.person_container);
        seasonContainer = findViewById(R.id.season_container);
        
        // 新增元信息
        yearText = findViewById(R.id.year_text);
        genreText = findViewById(R.id.genre_text);
        regionText = findViewById(R.id.region_text);
        runtimeText = findViewById(R.id.runtime_text);
        mediaTypeTag = findViewById(R.id.media_type_tag);
        subtitleBrief = findViewById(R.id.subtitle_brief);
        audioBrief = findViewById(R.id.audio_brief);
        tagsContainer = findViewById(R.id.tags_container);
        
        // 文件信息
        dividerFile = findViewById(R.id.divider_file);
        fileInfoSection = findViewById(R.id.file_info_section);
        filePath = findViewById(R.id.file_path);
        fileSize = findViewById(R.id.file_size);
        fileCreated = findViewById(R.id.file_created);
        fileAdded = findViewById(R.id.file_added);
        
        // 视频信息
        dividerVideo = findViewById(R.id.divider_video);
        videoInfoSection = findViewById(R.id.video_info_section);
        videoCodec = findViewById(R.id.video_codec);
        videoResolution = findViewById(R.id.video_resolution);
        videoFramerate = findViewById(R.id.video_framerate);
        videoBitrate = findViewById(R.id.video_bitrate);
        videoHdr = findViewById(R.id.video_hdr);
        
        // 音频信息
        dividerAudio = findViewById(R.id.divider_audio);
        audioInfoSection = findViewById(R.id.audio_info_section);
        audioCodec = findViewById(R.id.audio_codec);
        audioChannels = findViewById(R.id.audio_channels);
        audioSampleRate = findViewById(R.id.audio_sample_rate);
        
        // 字幕信息
        dividerSubtitle = findViewById(R.id.divider_subtitle);
        subtitleInfoSection = findViewById(R.id.subtitle_info_section);
        subtitleList = findViewById(R.id.subtitle_list);
        
        // 设置初始标题
        titleTextView.setText(mediaTitle != null ? mediaTitle : "Loading...");
        
        // 播放按钮点击
        playButtonTextView.setOnClickListener(v -> onPlayButtonClick());
        playButtonTextView.requestFocus();
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
                    } else {
                        // 电影：加载演职人员和流信息
                        loadPersonListForMovie();
                        loadStreamInfo();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    ToastUtils.show(MediaDetailActivity.this, "加载详情失败: " + error);
                    summaryTextView.setText("加载详情失败");
                });
            }
        });
    }
    
    private void updateUI(MediaDetailResponse detail) {
        titleTextView.setText(detail.getTitle());
        mediaTitle = detail.getTitle();
        
        // 时长（顶部小字）
        if (detail.getRuntime() > 0) {
            durationTextView.setText("时长 " + FormatUtils.formatDuration(detail.getRuntime() * 60));
            durationTextView.setVisibility(View.VISIBLE);
        } else {
            durationTextView.setVisibility(View.GONE);
        }
        
        // 评分（蓝色高亮）
        if (detail.getVoteAverage() > 0) {
            ratingTextView.setText(String.format("%.1f分", detail.getVoteAverage()));
            ratingTextView.setVisibility(View.VISIBLE);
        } else {
            ratingTextView.setVisibility(View.GONE);
        }
        
        // 年份
        String year = "";
        if (detail.getReleaseDate() != null && detail.getReleaseDate().length() >= 4) {
            year = detail.getReleaseDate().substring(0, 4);
        } else if (detail.getAirDate() != null && detail.getAirDate().length() >= 4) {
            year = detail.getAirDate().substring(0, 4);
        } else if (detail.getFirstAirDate() != null && detail.getFirstAirDate().length() >= 4) {
            year = detail.getFirstAirDate().substring(0, 4);
        }
        if (!year.isEmpty()) {
            yearText.setText(year);
            yearText.setVisibility(View.VISIBLE);
        } else {
            yearText.setVisibility(View.GONE);
        }
        
        // 类型
        String genres = detail.getGenres();
        if (genres != null && !genres.isEmpty()) {
            genreText.setText(genres);
            genreText.setVisibility(View.VISIBLE);
        } else {
            genreText.setVisibility(View.GONE);
        }
        
        // 地区
        String originCountry = detail.getOriginCountry();
        if (originCountry != null && !originCountry.isEmpty()) {
            regionText.setText(originCountry);
            regionText.setVisibility(View.VISIBLE);
        } else {
            regionText.setVisibility(View.GONE);
        }
        
        // 时长（元信息行）
        if (detail.getRuntime() > 0) {
            runtimeText.setText(FormatUtils.formatDuration(detail.getRuntime() * 60));
            runtimeText.setVisibility(View.VISIBLE);
        } else {
            runtimeText.setVisibility(View.GONE);
        }
        
        // 媒体类型标签
        String type = detail.getType();
        if (type != null && !type.isEmpty()) {
            mediaTypeTag.setText(type.toLowerCase());
            mediaTypeTag.setVisibility(View.VISIBLE);
        } else {
            mediaTypeTag.setVisibility(View.GONE);
        }
        
        // 副标题（原标题）- 隐藏，已在元信息行显示
        subtitleTextView.setVisibility(View.GONE);

        // 简介
        String overview = detail.getOverview();
        if (overview != null && !overview.trim().isEmpty()) {
            summaryTextView.setText(overview);
        } else {
            summaryTextView.setText("暂无简介");
        }
        
        // 海报
        if (detail.getPoster() != null && !detail.getPoster().isEmpty()) {
            String posterUrl = detail.getPoster();
            if (!posterUrl.startsWith("http")) {
                posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=400";
            }
            Glide.with(this).load(posterUrl).placeholder(R.drawable.bg_card).into(posterImageView);
        }
    }
    
    /**
     * 加载流信息（电影专用）
     */
    private void loadStreamInfo() {
        mediaManager.getStreamList(mediaGuid, new MediaManager.MediaCallback<StreamListResponse>() {
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
    
    private void updateStreamInfo(StreamListResponse streamResponse) {
        if (streamResponse == null || streamResponse.getData() == null) {
            return;
        }
        
        cachedStreamResponse = streamResponse;
        
        // 显示文件信息区域
        dividerFile.setVisibility(View.VISIBLE);
        fileInfoSection.setVisibility(View.VISIBLE);
        
        StreamListResponse.StreamData data = streamResponse.getData();
        
        // 文件信息
        List<StreamListResponse.FileStream> files = data.getFiles();
        if (files != null && !files.isEmpty()) {
            StreamListResponse.FileStream file = files.get(0);
            filePath.setText(file.getPath() != null ? file.getPath() : "-");
            fileSize.setText(file.getSize() > 0 ? FormatUtils.formatFileSize(file.getSize()) : "-");
            fileCreated.setText(file.getFileBirthTime() > 0 ? FormatUtils.formatDate(file.getFileBirthTime()) : "-");
            fileAdded.setText(file.getCreateTime() > 0 ? FormatUtils.formatDate(file.getCreateTime()) : "-");
        }
        
        // 视频信息
        List<StreamListResponse.VideoStream> videoStreams = data.getVideoStreams();
        if (videoStreams != null && !videoStreams.isEmpty()) {
            dividerVideo.setVisibility(View.VISIBLE);
            videoInfoSection.setVisibility(View.VISIBLE);
            
            StreamListResponse.VideoStream video = videoStreams.get(0);
            videoCodec.setText(video.getCodecName() != null ? video.getCodecName().toUpperCase() : "-");
            videoResolution.setText(video.getWidth() > 0 && video.getHeight() > 0 ? 
                    video.getWidth() + " x " + video.getHeight() : "-");
            videoFramerate.setText(video.getFrameRate() > 0 ? 
                    String.format("%.2f fps", video.getFrameRate()) : "-");
            videoBitrate.setText(video.getBitRate() > 0 ? 
                    FormatUtils.formatBitrate(video.getBitRate()) : "-");
            String colorRange = video.getColorRangeType();
            videoHdr.setText(colorRange != null && colorRange.toUpperCase().contains("HDR") ? "HDR" : "SDR");
            
            // 更新分辨率标签
            updateVideoTags(video, data.getAudioStreams());
        }
        
        // 音频信息
        List<StreamListResponse.AudioStream> audioStreams = data.getAudioStreams();
        if (audioStreams != null && !audioStreams.isEmpty()) {
            dividerAudio.setVisibility(View.VISIBLE);
            audioInfoSection.setVisibility(View.VISIBLE);
            
            StreamListResponse.AudioStream audio = audioStreams.get(0);
            audioCodec.setText(audio.getCodecName() != null ? audio.getCodecName().toUpperCase() : "-");
            audioChannels.setText(FormatUtils.formatChannels(audio.getChannels()));
            String sampleRate = audio.getSampleRate();
            audioSampleRate.setText(sampleRate != null && !sampleRate.isEmpty() ? 
                    sampleRate + " Hz" : "-");
            
            // 更新音轨简要信息
            audioBrief.setText(audioStreams.size() > 1 ? audioStreams.size() + "条音轨" : "1条音轨");
            audioBrief.setVisibility(View.VISIBLE);
        } else {
            audioBrief.setText("未知音轨");
            audioBrief.setVisibility(View.VISIBLE);
        }
        
        // 字幕信息
        List<StreamListResponse.SubtitleStream> subtitleStreams = data.getSubtitleStreams();
        if (subtitleStreams != null && !subtitleStreams.isEmpty()) {
            dividerSubtitle.setVisibility(View.VISIBLE);
            subtitleInfoSection.setVisibility(View.VISIBLE);
            
            StringBuilder subText = new StringBuilder();
            for (int i = 0; i < subtitleStreams.size(); i++) {
                if (i > 0) subText.append("\n");
                subText.append(FormatUtils.formatSubtitleInfo(subtitleStreams.get(i)));
            }
            subtitleList.setText(subText.toString());
            
            // 更新字幕简要信息
            subtitleBrief.setText("字幕 " + subtitleStreams.size() + "条");
            subtitleBrief.setVisibility(View.VISIBLE);
        } else {
            subtitleBrief.setText("无字幕");
            subtitleBrief.setVisibility(View.VISIBLE);
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
    
    /**
     * 为电影加载演职人员列表
     */
    private void loadPersonListForMovie() {
        mediaManager.getPersonList(mediaGuid, new MediaManager.MediaCallback<List<PersonInfo>>() {
            @Override
            public void onSuccess(List<PersonInfo> personList) {
                runOnUiThread(() -> {
                    if (personList != null && !personList.isEmpty()) {
                        createPersonSection(personList);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load person list: " + error);
            }
        });
    }
    
    /**
     * 创建演职人员区域
     */
    private void createPersonSection(List<PersonInfo> personList) {
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
        
        personContainer.setVisibility(View.VISIBLE);
        personContainer.removeAllViews();
        
        if (!directors.isEmpty()) {
            addPersonRow(personContainer, "导演", directors);
        }
        if (!actors.isEmpty()) {
            addPersonRow(personContainer, "演员", actors);
        }
        if (!writers.isEmpty()) {
            addPersonRow(personContainer, "编剧", writers);
        }
    }
    
    private void addPersonRow(LinearLayout container, String title, List<PersonInfo> persons) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(getColor(R.color.tv_text_secondary));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = 16;
        titleView.setLayoutParams(titleParams);
        container.addView(titleView);
        
        android.widget.HorizontalScrollView scrollView = new android.widget.HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 8, 0, 0);
        
        for (PersonInfo person : persons) {
            LinearLayout personItem = createPersonItem(person);
            rowLayout.addView(personItem);
        }
        
        scrollView.addView(rowLayout);
        container.addView(scrollView);
    }
    
    private LinearLayout createPersonItem(PersonInfo person) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(android.view.Gravity.CENTER);
        
        int itemWidth = 80;
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                (int)(itemWidth * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        itemParams.rightMargin = 8;
        itemLayout.setLayoutParams(itemParams);
        
        ImageView avatarView = new ImageView(this);
        int avatarSize = (int)(60 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarView.setLayoutParams(avatarParams);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setBackground(getDrawable(R.drawable.person_avatar_background));
        
        String profilePath = person.getProfilePath();
        if (profilePath != null && !profilePath.isEmpty()) {
            String imageUrl = profilePath;
            if (!imageUrl.startsWith("http")) {
                imageUrl = SharedPreferencesManager.getImageServiceUrl() + profilePath + "?w=100";
            }
            Glide.with(this).load(imageUrl).placeholder(R.drawable.person_avatar_background).into(avatarView);
        }
        itemLayout.addView(avatarView);
        
        TextView nameView = new TextView(this);
        nameView.setText(person.getName());
        nameView.setTextSize(12);
        nameView.setTextColor(getColor(R.color.tv_text_primary));
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = 4;
        nameView.setLayoutParams(nameParams);
        itemLayout.addView(nameView);
        
        String role = person.getRole();
        if (role != null && !role.isEmpty()) {
            TextView roleView = new TextView(this);
            roleView.setText(role);
            roleView.setTextSize(10);
            roleView.setTextColor(getColor(R.color.tv_text_secondary));
            roleView.setGravity(android.view.Gravity.CENTER);
            roleView.setMaxLines(1);
            roleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemLayout.addView(roleView);
        }
        
        return itemLayout;
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
                        playButtonTextView.setText("播放第1季");
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
        if (seasons == null || seasons.isEmpty()) {
            return;
        }
        
        seasonContainer.setVisibility(View.VISIBLE);
        seasonContainer.removeAllViews();
        
        TextView seasonTitle = new TextView(this);
        seasonTitle.setText("季列表");
        seasonTitle.setTextSize(18);
        seasonTitle.setTextColor(getColor(R.color.tv_text_primary));
        seasonContainer.addView(seasonTitle);
        
        LinearLayout seasonRow = new LinearLayout(this);
        seasonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = 16;
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
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.tv_text_primary));
        button.setBackgroundColor(getColor(R.color.tv_card_background));
        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(24, 16, 24, 16);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 8, 0);
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
        seasonContainer.setVisibility(View.VISIBLE);
        seasonContainer.removeAllViews();
        
        TextView episodeTitle = new TextView(this);
        episodeTitle.setText("剧集");
        episodeTitle.setTextSize(18);
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
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.tv_text_primary));
        button.setBackgroundColor(getColor(R.color.tv_card_background));
        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        
        int buttonSize = 48;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int)(buttonSize * getResources().getDisplayMetrics().density),
                (int)(buttonSize * getResources().getDisplayMetrics().density)
        );
        params.setMargins(0, 0, 8, 8);
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
        ToastUtils.show(this, "正在加载...");
        startPlayItem(mediaGuid, mediaTitle, 0);
    }
    
    private void startPlayEpisode(int episodeNumber) {
        ToastUtils.show(this, "正在加载第" + episodeNumber + "集...");
        
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
                    runOnUiThread(() -> ToastUtils.show(MediaDetailActivity.this, "未找到该集"));
                }
            }

            @Override
            public void onError(String error) {
                 runOnUiThread(() -> ToastUtils.show(MediaDetailActivity.this, "加载剧集失败"));
            }
        });
    }
    
    private void startPlayItem(String itemGuid, String title, int episodeNumber) {
        mediaManager.startPlayWithInfo(itemGuid, new MediaManager.MediaCallback<com.mynas.nastv.model.PlayStartInfo>() {
            @Override
            public void onSuccess(com.mynas.nastv.model.PlayStartInfo playInfo) {
                runOnUiThread(() -> navigateToVideoPlayer(playInfo, title, itemGuid, episodeNumber));
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> ToastUtils.show(MediaDetailActivity.this, "播放失败: " + error));
            }
        });
    }

    private void navigateToVideoPlayer(com.mynas.nastv.model.PlayStartInfo playInfo, String title, String itemGuid, int episodeNumber) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", playInfo.getPlayUrl());
        intent.putExtra("video_title", title);
        intent.putExtra("media_title", mediaTitle);
        intent.putExtra("episode_guid", itemGuid);
        intent.putExtra("resume_position", playInfo.getResumePositionSeconds());
        intent.putExtra("video_guid", playInfo.getVideoGuid());
        intent.putExtra("audio_guid", playInfo.getAudioGuid());
        intent.putExtra("media_guid", playInfo.getMediaGuid());
        
        if (mediaDetail != null) {
             intent.putExtra("douban_id", String.valueOf(mediaDetail.getDoubanId()));
             intent.putExtra("parent_guid", mediaDetail.getParentGuid());
             intent.putExtra("season_number", mediaDetail.getSeasonNumber());
        }
        intent.putExtra("episode_number", episodeNumber);
        
        startActivity(intent);
    }
}
