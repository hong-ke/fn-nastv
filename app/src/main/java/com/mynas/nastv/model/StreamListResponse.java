package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 流列表响应模型
 */
public class StreamListResponse {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("data")
    private StreamData data;
    
    // Getters and setters
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public StreamData getData() {
        return data;
    }
    
    public void setData(StreamData data) {
        this.data = data;
    }
    
    /**
     * 流数据
     */
    public static class StreamData {
        @SerializedName("video_streams")
        private List<VideoStream> videoStreams;
        
        @SerializedName("audio_streams")
        private List<AudioStream> audioStreams;
        
        @SerializedName("subtitle_streams")
        private List<SubtitleStream> subtitleStreams;
        
        @SerializedName("files")
        private List<FileStream> files;
        
        // Getters and setters
        public List<VideoStream> getVideoStreams() {
            return videoStreams;
        }
        
        public void setVideoStreams(List<VideoStream> videoStreams) {
            this.videoStreams = videoStreams;
        }
        
        public List<AudioStream> getAudioStreams() {
            return audioStreams;
        }
        
        public void setAudioStreams(List<AudioStream> audioStreams) {
            this.audioStreams = audioStreams;
        }
        
        public List<SubtitleStream> getSubtitleStreams() {
            return subtitleStreams;
        }
        
        public void setSubtitleStreams(List<SubtitleStream> subtitleStreams) {
            this.subtitleStreams = subtitleStreams;
        }
        
        public List<FileStream> getFiles() {
            return files;
        }
        
        public void setFiles(List<FileStream> files) {
            this.files = files;
        }
    }
    
    /**
     * 视频流
     */
    public static class VideoStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("media_guid")
        private String mediaGuid;
        
        @SerializedName("codec_name")
        private String codec;
        
        @SerializedName("resolution_type")
        private String resolution;
        
        @SerializedName("color_range_type")
        private String colorRangeType;  // SDR/HDR
        
        @SerializedName("bps")
        private long bitrate;
        
        @SerializedName("width")
        private int width;
        
        @SerializedName("height")
        private int height;
        
        @SerializedName("profile")
        private String profile;
        
        @SerializedName("bit_depth")
        private int bitDepth;
        
        @SerializedName("duration")
        private long duration;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("frame_rate")
        private double frameRate;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getMediaGuid() {
            return mediaGuid;
        }
        
        public void setMediaGuid(String mediaGuid) {
            this.mediaGuid = mediaGuid;
        }
        
        public String getCodec() {
            return codec;
        }
        
        public String getCodecName() {
            return codec;
        }
        
        public void setCodec(String codec) {
            this.codec = codec;
        }
        
        public String getResolution() {
            return resolution;
        }
        
        public void setResolution(String resolution) {
            this.resolution = resolution;
        }
        
        public String getColorRangeType() {
            return colorRangeType;
        }
        
        public void setColorRangeType(String colorRangeType) {
            this.colorRangeType = colorRangeType;
        }
        
        public long getBitrate() {
            return bitrate;
        }
        
        public long getBitRate() {
            return bitrate;
        }
        
        public void setBitrate(long bitrate) {
            this.bitrate = bitrate;
        }
        
        public int getWidth() {
            return width;
        }
        
        public void setWidth(int width) {
            this.width = width;
        }
        
        public int getHeight() {
            return height;
        }
        
        public void setHeight(int height) {
            this.height = height;
        }
        
        public String getProfile() {
            return profile;
        }
        
        public void setProfile(String profile) {
            this.profile = profile;
        }
        
        public int getBitDepth() {
            return bitDepth;
        }
        
        public void setBitDepth(int bitDepth) {
            this.bitDepth = bitDepth;
        }
        
        public long getDuration() {
            return duration;
        }
        
        public void setDuration(long duration) {
            this.duration = duration;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public double getFrameRate() {
            return frameRate;
        }
        
        public void setFrameRate(double frameRate) {
            this.frameRate = frameRate;
        }
    }
    
    /**
     * 音频流
     */
    public static class AudioStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("media_guid")
        private String mediaGuid;
        
        @SerializedName("codec_name")
        private String codec;
        
        @SerializedName("channels")
        private int channels;
        
        @SerializedName("channel_layout")
        private String channelLayout;  // stereo/5.1等
        
        @SerializedName("sample_rate")
        private String sampleRate;
        
        @SerializedName("language")
        private String language;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("bps")
        private long bitrate;
        
        @SerializedName("is_default")
        private int isDefault;
        
        @SerializedName("index")
        private int index;
        
        @SerializedName("audio_type")
        private String audioType;  // Stereo/DolbyAtmos/DTS等
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getMediaGuid() {
            return mediaGuid;
        }
        
        public void setMediaGuid(String mediaGuid) {
            this.mediaGuid = mediaGuid;
        }
        
        public String getCodec() {
            return codec;
        }
        
        public String getCodecName() {
            return codec;
        }
        
        public void setCodec(String codec) {
            this.codec = codec;
        }
        
        public int getChannels() {
            return channels;
        }
        
        public void setChannels(int channels) {
            this.channels = channels;
        }
        
        public String getChannelLayout() {
            return channelLayout;
        }
        
        public void setChannelLayout(String channelLayout) {
            this.channelLayout = channelLayout;
        }
        
        public String getSampleRate() {
            return sampleRate;
        }
        
        public void setSampleRate(String sampleRate) {
            this.sampleRate = sampleRate;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public long getBitrate() {
            return bitrate;
        }
        
        public void setBitrate(long bitrate) {
            this.bitrate = bitrate;
        }
        
        public boolean isDefault() {
            return isDefault == 1;
        }
        
        public void setIsDefault(int isDefault) {
            this.isDefault = isDefault;
        }
        
        public int getIndex() {
            return index;
        }
        
        public void setIndex(int index) {
            this.index = index;
        }
        
        public String getAudioType() {
            return audioType;
        }
        
        public void setAudioType(String audioType) {
            this.audioType = audioType;
        }
    }
    
    /**
     * 字幕流（添加 index 字段）
     */
    public static class SubtitleStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("media_guid")
        private String mediaGuid;
        
        @SerializedName("language")
        private String language;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("format")
        private String format;
        
        @SerializedName("is_external")
        private int isExternal;
        
        @SerializedName("codec_name")
        private String codecName;
        
        @SerializedName("index")
        private int index;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getMediaGuid() {
            return mediaGuid;
        }
        
        public void setMediaGuid(String mediaGuid) {
            this.mediaGuid = mediaGuid;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getFormat() {
            return format;
        }
        
        public void setFormat(String format) {
            this.format = format;
        }
        
        public boolean isExternal() {
            return isExternal == 1;
        }
        
        public void setIsExternal(int isExternal) {
            this.isExternal = isExternal;
        }
        
        public String getCodecName() {
            return codecName;
        }
        
        public void setCodecName(String codecName) {
            this.codecName = codecName;
        }
        
        public int getIndex() {
            return index;
        }
        
        public void setIndex(int index) {
            this.index = index;
        }
    }
    
    /**
     * 文件流
     */
    public static class FileStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("path")
        private String path;
        
        @SerializedName("file_name")
        private String fileName;
        
        @SerializedName("size")
        private long size;
        
        @SerializedName("file_birth_time")
        private long fileBirthTime;  // 文件创建时间
        
        @SerializedName("create_time")
        private long createTime;  // 添加时间
        
        @SerializedName("update_time")
        private long updateTime;
        
        @SerializedName("can_play")
        private int canPlay;
        
        @SerializedName("play_error")
        private String playError;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
        
        public long getFileBirthTime() {
            return fileBirthTime;
        }
        
        public void setFileBirthTime(long fileBirthTime) {
            this.fileBirthTime = fileBirthTime;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }
        
        public long getUpdateTime() {
            return updateTime;
        }
        
        public void setUpdateTime(long updateTime) {
            this.updateTime = updateTime;
        }
        
        public boolean canPlay() {
            return canPlay == 1;
        }
        
        public void setCanPlay(int canPlay) {
            this.canPlay = canPlay;
        }
        
        public String getPlayError() {
            return playError;
        }
        
        public void setPlayError(String playError) {
            this.playError = playError;
        }
        
        /**
         * 获取显示名称（从文件名提取）
         */
        public String getDisplayName() {
            if (fileName != null && !fileName.isEmpty()) {
                return fileName;
            }
            if (path != null && !path.isEmpty()) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    return path.substring(lastSlash + 1);
                }
                return path;
            }
            return "未知文件";
        }
    }
}