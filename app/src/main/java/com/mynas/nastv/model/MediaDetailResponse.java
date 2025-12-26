package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 媒体详情响应数据模型
 */
public class MediaDetailResponse {
    
    @SerializedName("guid")
    private String guid;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("original_title")
    private String originalTitle;
    
    @SerializedName("overview")
    private String overview;
    
    @SerializedName("posters")  // 电影/电视剧列表API返回posters
    private String postersField;
    
    @SerializedName("poster")   // 剧集详情API返回poster
    private String posterField;
    
    @SerializedName("backdrops")  // [修复] 实际字段名是backdrops不是backdrop
    private String backdrop;
    
    @SerializedName("stills")  // 剧集剧照
    private String still;
    
    @SerializedName("vote_average")
    private String voteAverageStr;  // [修复] vote_average是字符串，需要手动转换
    
    @SerializedName("vote_count")
    private int voteCount;
    
    @SerializedName("air_date")
    private String airDate;
    
    @SerializedName("first_air_date")
    private String firstAirDate;
    
    @SerializedName("release_date")
    private String releaseDate;
    
    @SerializedName("runtime")
    private int runtime;
    
    @SerializedName("season_number")
    private int seasonNumber;
    
    @SerializedName("episode_number")
    private int episodeNumber;
    
    @SerializedName("episode_count")
    private int episodeCount;
    
    @SerializedName("number_of_seasons")
    private int numberOfSeasons;
    
    @SerializedName("number_of_episodes")
    private int numberOfEpisodes;
    
    @SerializedName("local_number_of_episodes")
    private int localNumberOfEpisodes;
    
    @SerializedName("type")
    private String type;
    
    @SerializedName("status")
    private String status;
    
    @SerializedName("genres")
    private int[] genres;  // [修复] genres是数字数组，不是字符串
    
    @SerializedName("genres_str")
    private String genresStr;  // 类型标签字符串，如 "剧情 爱情"
    
    @SerializedName("origin_country")
    private String[] originCountry;  // 制作地区数组
    
    @SerializedName("content_rating")
    private String contentRating;  // 内容分级，如 "TV-PG"
    
    @SerializedName("production_companies")
    private String productionCompanies;
    
    @SerializedName("production_countries")
    private String[] productionCountries;  // [修复] production_countries是字符串数组
    
    @SerializedName("spoken_languages")
    private String spokenLanguages;
    
    @SerializedName("homepage")
    private String homepage;
    
    @SerializedName("imdb_id")
    private String imdbId;
    
    @SerializedName("tmdb_id")
    private int tmdbId;

    @SerializedName("douban_id")
    private long doubanId;

    @SerializedName("parent_guid")
    private String parentGuid;
    
    @SerializedName("created_time")
    private String createdTime;
    
    @SerializedName("updated_time")
    private String updatedTime;
    
    // Getters
    public String getGuid() { return guid; }
    public String getTitle() { return title; }
    public String getOriginalTitle() { return originalTitle; }
    public String getOverview() { return overview; }
    public String getPoster() { 
        // 优先使用poster字段（剧集详情API），如果为空则使用posters字段（列表API）
        if (posterField != null && !posterField.isEmpty()) {
            return posterField;
        }
        return postersField;
    }
    public String getBackdrop() { return backdrop; }
    public String getStill() { return still; }
    public double getVoteAverage() { 
        try {
            return voteAverageStr != null ? Double.parseDouble(voteAverageStr) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    public int getVoteCount() { return voteCount; }
    public String getAirDate() { return airDate; }
    public String getFirstAirDate() { return firstAirDate; }
    public String getReleaseDate() { return releaseDate; }
    public int getRuntime() { return runtime; }
    public int getSeasonNumber() { return seasonNumber; }
    public int getEpisodeNumber() { return episodeNumber; }
    public int getEpisodeCount() { return episodeCount; }
    public int getNumberOfSeasons() { return numberOfSeasons; }
    public int getNumberOfEpisodes() { return numberOfEpisodes; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public int[] getGenresArray() { return genres; }
    public String getGenresStr() { return genresStr; }
    public String[] getOriginCountryArray() { return originCountry; }
    public String getOriginCountry() { 
        if (originCountry != null && originCountry.length > 0) {
            return String.join(" ", originCountry);
        }
        return null;
    }
    public String getContentRating() { return contentRating; }
    
    /**
     * 获取格式化的类型标签字符串
     * 优先使用 genresStr，如果没有则返回空
     */
    public String getGenres() {
        return genresStr;
    }
    public String getProductionCompanies() { return productionCompanies; }
    public String[] getProductionCountries() { return productionCountries; }
    public String getSpokenLanguages() { return spokenLanguages; }
    public String getHomepage() { return homepage; }
    public String getImdbId() { return imdbId; }
    public int getTmdbId() { return tmdbId; }
    public long getDoubanId() { return doubanId; }
    public String getParentGuid() { return parentGuid; }
    public String getCreatedTime() { return createdTime; }
    public String getUpdatedTime() { return updatedTime; }
    
    // Setters
    public void setGuid(String guid) { this.guid = guid; }
    public void setTitle(String title) { this.title = title; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    public void setOverview(String overview) { this.overview = overview; }
    public void setPoster(String poster) { 
        this.posterField = poster;
        this.postersField = poster;
    }
    public void setBackdrop(String backdrop) { this.backdrop = backdrop; }
    public void setVoteAverageStr(String voteAverageStr) { this.voteAverageStr = voteAverageStr; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    public void setAirDate(String airDate) { this.airDate = airDate; }
    public void setFirstAirDate(String firstAirDate) { this.firstAirDate = firstAirDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    public void setRuntime(int runtime) { this.runtime = runtime; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }
    public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }
    public void setEpisodeCount(int episodeCount) { this.episodeCount = episodeCount; }
    public void setNumberOfSeasons(int numberOfSeasons) { this.numberOfSeasons = numberOfSeasons; }
    public void setNumberOfEpisodes(int numberOfEpisodes) { this.numberOfEpisodes = numberOfEpisodes; }
    
    public int getLocalNumberOfEpisodes() { return localNumberOfEpisodes; }
    public void setLocalNumberOfEpisodes(int localNumberOfEpisodes) { this.localNumberOfEpisodes = localNumberOfEpisodes; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setGenres(int[] genres) { this.genres = genres; }
    public void setGenresStr(String genresStr) { this.genresStr = genresStr; }
    public void setOriginCountry(String[] originCountry) { this.originCountry = originCountry; }
    public void setContentRating(String contentRating) { this.contentRating = contentRating; }
    public void setProductionCompanies(String productionCompanies) { this.productionCompanies = productionCompanies; }
    public void setProductionCountries(String[] productionCountries) { this.productionCountries = productionCountries; }
    public void setSpokenLanguages(String spokenLanguages) { this.spokenLanguages = spokenLanguages; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public void setTmdbId(int tmdbId) { this.tmdbId = tmdbId; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
    public void setUpdatedTime(String updatedTime) { this.updatedTime = updatedTime; }
    
    @Override
    public String toString() {
        return "MediaDetailResponse{" +
                "guid='" + guid + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", seasonNumber=" + seasonNumber +
                ", episodeNumber=" + episodeNumber +
                ", voteAverage=" + getVoteAverage() +
                ", runtime=" + runtime +
                '}';
    }
}