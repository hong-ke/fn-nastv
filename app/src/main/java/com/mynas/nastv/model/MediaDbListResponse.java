package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 📚 媒体库列表响应数据模型
 */
public class MediaDbListResponse extends BaseResponse<List<MediaDbListResponse.MediaDb>> {
    
    /**
     * 媒体库信息 - 匹配实际API返回格式
     */
    public static class MediaDb {
        private String guid;
        
        @SerializedName("title")
        private String name;    // API返回title字段，映射到name
        
        @SerializedName("category") 
        private String type;    // API返回category字段，映射到type
        
        @SerializedName("posters")
        private List<String> posters;
        
        @SerializedName("view_type")
        private int viewType;
        
        // Getters and Setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public List<String> getPosters() {
            return posters;
        }
        
        public void setPosters(List<String> posters) {
            this.posters = posters;
        }
        
        public int getViewType() {
            return viewType;
        }
        
        public void setViewType(int viewType) {
            this.viewType = viewType;
        }
    }
}
