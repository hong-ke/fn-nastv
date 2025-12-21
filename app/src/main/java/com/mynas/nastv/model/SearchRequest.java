package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🔍 搜索请求模型
 */
public class SearchRequest {
    private String keyword;
    private String type;  // "movie", "tv", "anime", "all"
    private String sort;  // "latest", "rating", "year", "name"
    private int page;
    private int limit;
    
    @SerializedName("include_adult")
    private boolean includeAdult;
    
    // 筛选条件
    private String genre;
    private String year;
    @SerializedName("min_rating")
    private float minRating;
    @SerializedName("max_rating")
    private float maxRating;
    
    // 构造函数
    public SearchRequest() {}
    
    public SearchRequest(String keyword) {
        this.keyword = keyword;
        this.type = "all";
        this.sort = "latest";
        this.page = 1;
        this.limit = 20;
        this.includeAdult = false;
    }
    
    public SearchRequest(String keyword, String type, int page, int limit) {
        this.keyword = keyword;
        this.type = type;
        this.page = page;
        this.limit = limit;
        this.sort = "latest";
        this.includeAdult = false;
    }
    
    // Getters and Setters
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    
    public boolean isIncludeAdult() { return includeAdult; }
    public void setIncludeAdult(boolean includeAdult) { this.includeAdult = includeAdult; }
    
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    
    public float getMinRating() { return minRating; }
    public void setMinRating(float minRating) { this.minRating = minRating; }
    
    public float getMaxRating() { return maxRating; }
    public void setMaxRating(float maxRating) { this.maxRating = maxRating; }
}
