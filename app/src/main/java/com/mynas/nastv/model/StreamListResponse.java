package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🎬 流列表响应模型
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
     * 📺 流数据
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
     * 📺 视频流
     */
    public static class VideoStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("codec_name")  // 🔧 修复：API中是 codec_name
        private String codec;
        
        @SerializedName("resolution_type")  // 🔧 修复：API中是 resolution_type (如 "4k")
        private String resolution;
        
        @SerializedName("bps")  // 🔧 修复：API中是 bps
        private long bitrate;
        
        @SerializedName("width")
        private int width;
        
        @SerializedName("height")
        private int height;
        
        @SerializedName("profile")  // 🔧 新增：编码器Profile (如 "Main", "Main 10")
        private String profile;
        
        @SerializedName("bit_depth")  // 🔧 新增：色深 (如 8, 10)
        private int bitDepth;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getCodec() {
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
        
        public long getBitrate() {
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
    }
    
    /**
     * 🎵 音频流
     */
    public static class AudioStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("codec_name")  // 🔧 修复：API中是 codec_name
        private String codec;
        
        @SerializedName("channels")
        private int channels;
        
        @SerializedName("sample_rate")  // 🔧 注意：API返回字符串 "44100"
        private String sampleRate;
        
        @SerializedName("language")
        private String language;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getCodec() {
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
        
        public String getSampleRate() {  // 🔧 修复：API返回字符串类型
            return sampleRate;
        }
        
        public void setSampleRate(String sampleRate) {  // 🔧 修复：参数类型改为String
            this.sampleRate = sampleRate;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
    }
    
    /**
     * 📝 字幕流
     */
    public static class SubtitleStream {
        @SerializedName("guid")
        private String guid;
        
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
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
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
    }
    
    /**
     * 📁 文件流
     */
    public static class FileStream {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("path")
        private String path;
        
        @SerializedName("size")
        private long size;
        
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
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
    }
}