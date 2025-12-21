package com.mynas.nastv.model;

/**
 * 🎬 播放API响应模型
 * 响应 /fnos/v/api/v1/play/play
 */
public class PlayApiResponse {
    private boolean success;
    private String message;
    private PlaySessionData data;

    public PlayApiResponse() {}

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public PlaySessionData getData() { return data; }
    public void setData(PlaySessionData data) { this.data = data; }

    /**
     * 播放会话数据
     */
    public static class PlaySessionData {
        private String session_id;
        private String stream_url;
        private int duration;
        private String media_guid;
        private String play_link;
        private String video_guid;
        private String audio_guid;
        private int video_index;
        private int audio_index;

        public PlaySessionData() {}

        // Getters and Setters
        public String getSession_id() { return session_id; }
        public void setSession_id(String session_id) { this.session_id = session_id; }

        public String getStream_url() { return stream_url; }
        public void setStream_url(String stream_url) { this.stream_url = stream_url; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }

        public String getMedia_guid() { return media_guid; }
        public void setMedia_guid(String media_guid) { this.media_guid = media_guid; }

        public String getPlay_link() { return play_link; }
        public void setPlay_link(String play_link) { this.play_link = play_link; }

        public String getVideo_guid() { return video_guid; }
        public void setVideo_guid(String video_guid) { this.video_guid = video_guid; }

        public String getAudio_guid() { return audio_guid; }
        public void setAudio_guid(String audio_guid) { this.audio_guid = audio_guid; }

        public int getVideo_index() { return video_index; }
        public void setVideo_index(int video_index) { this.video_index = video_index; }

        public int getAudio_index() { return audio_index; }
        public void setAudio_index(int audio_index) { this.audio_index = audio_index; }

        @Override
        public String toString() {
            return "PlaySessionData{" +
                    "session_id='" + session_id + '\'' +
                    ", stream_url='" + stream_url + '\'' +
                    ", duration=" + duration +
                    ", media_guid='" + media_guid + '\'' +
                    ", play_link='" + play_link + '\'' +
                    ", video_guid='" + video_guid + '\'' +
                    ", audio_guid='" + audio_guid + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "PlayApiResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
