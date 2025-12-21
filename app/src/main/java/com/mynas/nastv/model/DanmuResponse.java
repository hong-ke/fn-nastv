package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * 🎬 弹幕响应数据模型
 * API返回格式: { "集数": [ {弹幕对象} ] }
 */
public class DanmuResponse {
    // 直接使用Map来接收动态的集数key
    private Map<String, List<Danmu>> episodeDanmuMap;
    
    public DanmuResponse() {}
    
    public DanmuResponse(Map<String, List<Danmu>> episodeDanmuMap) {
        this.episodeDanmuMap = episodeDanmuMap;
    }
    
    public Map<String, List<Danmu>> getEpisodeDanmuMap() {
        return episodeDanmuMap;
    }
    
    public void setEpisodeDanmuMap(Map<String, List<Danmu>> episodeDanmuMap) {
        this.episodeDanmuMap = episodeDanmuMap;
    }
    
    /**
     * 🔍 根据集数获取弹幕列表
     */
    public List<Danmu> getDanmuByEpisode(int episodeNumber) {
        if (episodeDanmuMap == null) return null;
        return episodeDanmuMap.get(String.valueOf(episodeNumber));
    }
    
    /**
     * 🔍 根据集数获取弹幕列表（字符串key）
     */
    public List<Danmu> getDanmuByEpisode(String episodeKey) {
        if (episodeDanmuMap == null) return null;
        return episodeDanmuMap.get(episodeKey);
    }
    
    /**
     * 📊 获取总弹幕数量
     */
    public int getTotalDanmuCount() {
        if (episodeDanmuMap == null) return 0;
        int total = 0;
        for (List<Danmu> danmuList : episodeDanmuMap.values()) {
            if (danmuList != null) {
                total += danmuList.size();
            }
        }
        return total;
    }
    
    /**
     * 📊 检查是否有弹幕数据
     */
    public boolean hasDanmu() {
        return episodeDanmuMap != null && !episodeDanmuMap.isEmpty();
    }
    
    @Override
    public String toString() {
        if (episodeDanmuMap == null) {
            return "DanmuResponse{empty}";
        }
        
        StringBuilder sb = new StringBuilder("DanmuResponse{");
        for (Map.Entry<String, List<Danmu>> entry : episodeDanmuMap.entrySet()) {
            sb.append("Episode ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue() != null ? entry.getValue().size() : 0).append(" danmu, ");
        }
        sb.append("total: ").append(getTotalDanmuCount()).append("}");
        return sb.toString();
    }
}
