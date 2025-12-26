package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 播放响应模型
 */
public class PlayResponse {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("data")
    private PlayData data;
    
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
    
    public PlayData getData() {
        return data;
    }
    
    public void setData(PlayData data) {
        this.data = data;
    }
    
    /**
     * 播放数据
     */
    public static class PlayData {
        @SerializedName("play_link")
        private String playLink;
        
        @SerializedName("media_guid")
        private String mediaGuid;
        
        @SerializedName("video_guid")
        private String videoGuid;
        
        @SerializedName("audio_guid")
        private String audioGuid;
        
        @SerializedName("subtitle_guid")
        private String subtitleGuid;
        
        @SerializedName("resolution")
        private String resolution;
        
        @SerializedName("bitrate")
        private long bitrate;
        
        @SerializedName("duration")
        private int duration;
        
        // Getters and setters
        public String getPlayLink() {
            return playLink;
        }
        
        public void setPlayLink(String playLink) {
            this.playLink = playLink;
        }
        
        public String getMediaGuid() {
            return mediaGuid;
        }
        
        public void setMediaGuid(String mediaGuid) {
            this.mediaGuid = mediaGuid;
        }
        
        public String getVideoGuid() {
            return videoGuid;
        }
        
        public void setVideoGuid(String videoGuid) {
            this.videoGuid = videoGuid;
        }
        
        public String getAudioGuid() {
            return audioGuid;
        }
        
        public void setAudioGuid(String audioGuid) {
            this.audioGuid = audioGuid;
        }
        
        public String getSubtitleGuid() {
            return subtitleGuid;
        }
        
        public void setSubtitleGuid(String subtitleGuid) {
            this.subtitleGuid = subtitleGuid;
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
        
        public int getDuration() {
            return duration;
        }
        
        public void setDuration(int duration) {
            this.duration = duration;
        }
        
        @Override
        public String toString() {
            return "PlayData{" +
                    "playLink='" + playLink + '\'' +
                    ", mediaGuid='" + mediaGuid + '\'' +
                    ", videoGuid='" + videoGuid + '\'' +
                    ", audioGuid='" + audioGuid + '\'' +
                    ", subtitleGuid='" + subtitleGuid + '\'' +
                    ", resolution='" + resolution + '\'' +
                    ", bitrate=" + bitrate +
                    ", duration=" + duration +
                    '}';
        }
    }
}
