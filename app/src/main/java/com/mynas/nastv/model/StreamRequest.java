package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * 流信息请求
 * 对应 POST /v/api/v1/stream 接口
 */
public class StreamRequest {
    @SerializedName("media_guid")
    private String mediaGuid;
    
    private int level;
    
    private String ip;
    
    private Map<String, List<String>> header;

    public StreamRequest(String mediaGuid) {
        this.mediaGuid = mediaGuid;
        this.level = 1;
        this.ip = "";
        this.header = new HashMap<>();
        this.header.put("User-Agent", Arrays.asList("trim_player"));
    }

    public String getMediaGuid() { return mediaGuid; }
    public int getLevel() { return level; }
    public String getIp() { return ip; }
    public Map<String, List<String>> getHeader() { return header; }
}
