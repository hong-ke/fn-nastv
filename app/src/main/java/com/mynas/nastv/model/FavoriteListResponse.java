package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * ⭐ 收藏列表响应模型
 * 对应 /v/api/v1/favorite/list 接口
 */
public class FavoriteListResponse {

    @SerializedName("total")
    private int total;

    @SerializedName("list")
    private List<FavoriteItem> list;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<FavoriteItem> getList() {
        return list;
    }

    public void setList(List<FavoriteItem> list) {
        this.list = list;
    }

    /**
     * 收藏项目
     */
    public static class FavoriteItem {
        @SerializedName("guid")
        private String guid;

        @SerializedName("title")
        private String title;

        @SerializedName("tv_title")
        private String tvTitle;

        @SerializedName("type")
        private String type;  // Movie/TV/Episode

        @SerializedName("poster")
        private String poster;

        @SerializedName("vote_average")
        private String voteAverage;

        @SerializedName("air_date")
        private String airDate;

        @SerializedName("number_of_episodes")
        private int numberOfEpisodes;

        @SerializedName("number_of_seasons")
        private int numberOfSeasons;

        @SerializedName("overview")
        private String overview;

        @SerializedName("is_favorite")
        private int isFavorite;

        @SerializedName("favorite_time")
        private long favoriteTime;  // 收藏时间

        // Getters and Setters
        public String getGuid() {
            return guid;
        }

        public void setGuid(String guid) {
            this.guid = guid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTvTitle() {
            return tvTitle;
        }

        public void setTvTitle(String tvTitle) {
            this.tvTitle = tvTitle;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPoster() {
            return poster;
        }

        public void setPoster(String poster) {
            this.poster = poster;
        }

        public String getVoteAverage() {
            return voteAverage;
        }

        public void setVoteAverage(String voteAverage) {
            this.voteAverage = voteAverage;
        }

        public String getAirDate() {
            return airDate;
        }

        public void setAirDate(String airDate) {
            this.airDate = airDate;
        }

        public int getNumberOfEpisodes() {
            return numberOfEpisodes;
        }

        public void setNumberOfEpisodes(int numberOfEpisodes) {
            this.numberOfEpisodes = numberOfEpisodes;
        }

        public int getNumberOfSeasons() {
            return numberOfSeasons;
        }

        public void setNumberOfSeasons(int numberOfSeasons) {
            this.numberOfSeasons = numberOfSeasons;
        }

        public String getOverview() {
            return overview;
        }

        public void setOverview(String overview) {
            this.overview = overview;
        }

        public boolean isFavorite() {
            return isFavorite == 1;
        }

        public void setIsFavorite(int isFavorite) {
            this.isFavorite = isFavorite;
        }

        public long getFavoriteTime() {
            return favoriteTime;
        }

        public void setFavoriteTime(long favoriteTime) {
            this.favoriteTime = favoriteTime;
        }

        /**
         * 获取显示标题
         */
        public String getDisplayTitle() {
            if (tvTitle != null && !tvTitle.isEmpty()) {
                return tvTitle;
            }
            return title;
        }

        /**
         * 获取年份
         */
        public String getYear() {
            if (airDate != null && airDate.length() >= 4) {
                return airDate.substring(0, 4);
            }
            return "";
        }

        /**
         * 获取评分（double类型）
         */
        public double getRating() {
            if (voteAverage != null && !voteAverage.isEmpty()) {
                try {
                    return Double.parseDouble(voteAverage);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
    }
}
