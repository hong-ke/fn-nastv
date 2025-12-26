package com.mynas.nastv.model;

/**
 * 播放API请求模型
 * POST /fnos/v/api/v1/play/play
 */
public class PlayApiRequest {
    private String media_guid;
    private String video_guid;
    private String video_encoder;
    private String resolution;
    private int bitrate;
    private int startTimestamp;
    private String audio_encoder;
    private String audio_guid;
    private String subtitle_guid;
    private int channels;

    // 构造器
    public PlayApiRequest() {}

    public PlayApiRequest(String mediaGuid, String videoGuid, String audioGuid) {
        this.media_guid = mediaGuid;
        this.video_guid = videoGuid;
        this.audio_guid = audioGuid;
        
        // 默认参数
        this.video_encoder = "h264";
        this.resolution = "720";
        this.bitrate = 2107398;
        this.startTimestamp = 0;
        this.audio_encoder = "aac";
        this.subtitle_guid = "";
        this.channels = 2;
    }

    /**
     * 构造函数：使用原始视频流信息（推荐，获取最高画质）
     */
    public PlayApiRequest(String mediaGuid, String videoGuid, String audioGuid, 
                         String videoCodec, String originalResolution, long originalBitrate) {
        this.media_guid = mediaGuid;
        this.video_guid = videoGuid;
        this.audio_guid = audioGuid;
        
        // 使用原始视频流参数（原画质量）
        this.video_encoder = videoCodec != null ? videoCodec : "h264";
        this.resolution = originalResolution != null ? originalResolution : "720";
        this.bitrate = (int) (originalBitrate > 0 ? originalBitrate : 2107398);
        this.startTimestamp = 0;
        this.audio_encoder = "aac";
        this.subtitle_guid = "";
        this.channels = 2;
    }

    // Getters and Setters
    public String getMedia_guid() { return media_guid; }
    public void setMedia_guid(String media_guid) { this.media_guid = media_guid; }

    public String getVideo_guid() { return video_guid; }
    public void setVideo_guid(String video_guid) { this.video_guid = video_guid; }

    public String getVideo_encoder() { return video_encoder; }
    public void setVideo_encoder(String video_encoder) { this.video_encoder = video_encoder; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }

    public int getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(int startTimestamp) { this.startTimestamp = startTimestamp; }

    public String getAudio_encoder() { return audio_encoder; }
    public void setAudio_encoder(String audio_encoder) { this.audio_encoder = audio_encoder; }

    public String getAudio_guid() { return audio_guid; }
    public void setAudio_guid(String audio_guid) { this.audio_guid = audio_guid; }

    public String getSubtitle_guid() { return subtitle_guid; }
    public void setSubtitle_guid(String subtitle_guid) { this.subtitle_guid = subtitle_guid; }

    public int getChannels() { return channels; }
    public void setChannels(int channels) { this.channels = channels; }

    @Override
    public String toString() {
        return "PlayApiRequest{" +
                "media_guid='" + media_guid + '\'' +
                ", video_guid='" + video_guid + '\'' +
                ", video_encoder='" + video_encoder + '\'' +
                ", resolution='" + resolution + '\'' +
                ", bitrate=" + bitrate +
                ", startTimestamp=" + startTimestamp +
                ", audio_encoder='" + audio_encoder + '\'' +
                ", audio_guid='" + audio_guid + '\'' +
                ", subtitle_guid='" + subtitle_guid + '\'' +
                ", channels=" + channels +
                '}';
    }
}
