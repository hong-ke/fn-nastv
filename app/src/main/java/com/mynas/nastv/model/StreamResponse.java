package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🎬 流信息响应
 * 对应 POST /v/api/v1/stream 接口
 */
public class StreamResponse {
    private int code;
    @SerializedName("msg")
    private String message;
    private StreamData data;

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public StreamData getData() { return data; }

    public static class StreamData {
        @SerializedName("file_stream")
        private FileStream fileStream;
        
        @SerializedName("video_stream")
        private VideoStream videoStream;
        
        @SerializedName("audio_streams")
        private List<AudioStream> audioStreams;
        
        @SerializedName("qualities")
        private List<Quality> qualities;
        
        @SerializedName("direct_link_qualities")
        private List<DirectLinkQuality> directLinkQualities;
        
        @SerializedName("direct_link_audio_streams")
        private List<DirectLinkAudioStream> directLinkAudioStreams;

        public FileStream getFileStream() { return fileStream; }
        public VideoStream getVideoStream() { return videoStream; }
        public List<AudioStream> getAudioStreams() { return audioStreams; }
        public List<Quality> getQualities() { return qualities; }
        public List<DirectLinkQuality> getDirectLinkQualities() { return directLinkQualities; }
        public List<DirectLinkAudioStream> getDirectLinkAudioStreams() { return directLinkAudioStreams; }
        
        /**
         * 获取原画直连URL（优先）
         */
        public String getOriginalQualityUrl() {
            if (directLinkQualities == null || directLinkQualities.isEmpty()) {
                return null;
            }
            // 优先查找"原画"
            for (DirectLinkQuality q : directLinkQualities) {
                if ("原画".equals(q.getResolution())) {
                    return q.getUrl();
                }
            }
            // 如果没有原画，返回第一个
            return directLinkQualities.get(0).getUrl();
        }
    }

    public static class FileStream {
        private String guid;
        private String path;
        private long size;
        
        public String getGuid() { return guid; }
        public String getPath() { return path; }
        public long getSize() { return size; }
    }

    public static class VideoStream {
        private String guid;
        @SerializedName("codec_name")
        private String codecName;
        private int width;
        private int height;
        private long duration;
        
        public String getGuid() { return guid; }
        public String getCodecName() { return codecName; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public long getDuration() { return duration; }
    }

    public static class AudioStream {
        private String guid;
        @SerializedName("codec_name")
        private String codecName;
        private String language;
        private int channels;
        
        public String getGuid() { return guid; }
        public String getCodecName() { return codecName; }
        public String getLanguage() { return language; }
        public int getChannels() { return channels; }
    }

    public static class Quality {
        private int bitrate;
        private String resolution;
        private String url;
        @SerializedName("is_m3u8")
        private boolean isM3u8;
        
        public int getBitrate() { return bitrate; }
        public String getResolution() { return resolution; }
        public String getUrl() { return url; }
        public boolean isM3u8() { return isM3u8; }
    }

    /**
     * 直连质量（夸克网盘等云存储直链）
     */
    public static class DirectLinkQuality {
        private int bitrate;
        private String resolution;
        private boolean progressive;
        private String url;
        @SerializedName("is_m3u8")
        private boolean isM3u8;
        @SerializedName("expired_at")
        private int expiredAt;
        
        public int getBitrate() { return bitrate; }
        public String getResolution() { return resolution; }
        public boolean isProgressive() { return progressive; }
        public String getUrl() { return url; }
        public boolean isM3u8() { return isM3u8; }
        public int getExpiredAt() { return expiredAt; }
    }

    public static class DirectLinkAudioStream {
        @SerializedName("media_guid")
        private String mediaGuid;
        private String title;
        private String guid;
        @SerializedName("audio_type")
        private String audioType;
        private String language;
        
        public String getMediaGuid() { return mediaGuid; }
        public String getTitle() { return title; }
        public String getGuid() { return guid; }
        public String getAudioType() { return audioType; }
        public String getLanguage() { return language; }
    }
}
