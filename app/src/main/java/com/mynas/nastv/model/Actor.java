package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 👥 演员数据模型
 * 用于VideoDetailActivity的演员和制作团队信息显示
 * 对应Web项目中的演员相关功能
 */
public class Actor implements Parcelable {
    private String id;              // 演员ID
    private String name;            // 演员姓名
    private String originalName;    // 原始姓名
    private String character;       // 饰演角色
    private String job;             // 职务 (演员、导演、编剧等)
    private String department;      // 部门 (Acting, Directing, Writing等)
    
    // 🖼️ 图片信息
    private String profileUrl;      // 头像URL
    private String profilePath;     // 头像路径
    
    // 📊 统计信息
    private float popularity;       // 知名度
    private int order;              // 排序顺序 (主演排前面)
    
    // 📝 详细信息
    private String biography;       // 个人简介
    private String birthday;        // 生日
    private String placeOfBirth;    // 出生地
    private String gender;          // 性别
    
    // 🏆 作品信息
    private String[] knownFor;      // 知名作品
    private int movieCredits;       // 电影作品数
    private int tvCredits;          // 电视剧作品数
    
    // 🔧 构造函数
    public Actor() {}
    
    public Actor(String id, String name, String character) {
        this.id = id;
        this.name = name;
        this.character = character;
        this.job = "Actor";
        this.department = "Acting";
    }
    
    public Actor(String id, String name, String character, String profileUrl) {
        this.id = id;
        this.name = name;
        this.character = character;
        this.profileUrl = profileUrl;
        this.job = "Actor";
        this.department = "Acting";
    }
    
    public Actor(String id, String name, String job, String department, boolean isCrewMember) {
        this.id = id;
        this.name = name;
        this.job = job;
        this.department = department;
        // isCrewMember参数用于区分此构造函数，但不存储该值
    }
    
    // 🔄 Parcelable实现
    protected Actor(Parcel in) {
        id = in.readString();
        name = in.readString();
        originalName = in.readString();
        character = in.readString();
        job = in.readString();
        department = in.readString();
        profileUrl = in.readString();
        profilePath = in.readString();
        popularity = in.readFloat();
        order = in.readInt();
        biography = in.readString();
        birthday = in.readString();
        placeOfBirth = in.readString();
        gender = in.readString();
        knownFor = in.createStringArray();
        movieCredits = in.readInt();
        tvCredits = in.readInt();
    }
    
    public static final Creator<Actor> CREATOR = new Creator<Actor>() {
        @Override
        public Actor createFromParcel(Parcel in) {
            return new Actor(in);
        }
        
        @Override
        public Actor[] newArray(int size) {
            return new Actor[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(originalName);
        dest.writeString(character);
        dest.writeString(job);
        dest.writeString(department);
        dest.writeString(profileUrl);
        dest.writeString(profilePath);
        dest.writeFloat(popularity);
        dest.writeInt(order);
        dest.writeString(biography);
        dest.writeString(birthday);
        dest.writeString(placeOfBirth);
        dest.writeString(gender);
        dest.writeStringArray(knownFor);
        dest.writeInt(movieCredits);
        dest.writeInt(tvCredits);
    }
    
    // 📖 Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    
    public String getCharacter() { return character; }
    public void setCharacter(String character) { this.character = character; }
    
    public String getJob() { return job; }
    public void setJob(String job) { this.job = job; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }
    
    public String getProfilePath() { return profilePath; }
    public void setProfilePath(String profilePath) { this.profilePath = profilePath; }
    
    public float getPopularity() { return popularity; }
    public void setPopularity(float popularity) { this.popularity = popularity; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
    
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    
    public String[] getKnownFor() { return knownFor; }
    public void setKnownFor(String[] knownFor) { this.knownFor = knownFor; }
    
    public int getMovieCredits() { return movieCredits; }
    public void setMovieCredits(int movieCredits) { this.movieCredits = movieCredits; }
    
    public int getTvCredits() { return tvCredits; }
    public void setTvCredits(int tvCredits) { this.tvCredits = tvCredits; }
    
    // 🔧 辅助方法
    
    /**
     * 🎭 是否为演员
     */
    public boolean isActor() {
        return "Actor".equalsIgnoreCase(job) || "Acting".equalsIgnoreCase(department);
    }
    
    /**
     * 🎬 是否为导演
     */
    public boolean isDirector() {
        return "Director".equalsIgnoreCase(job) || "Directing".equalsIgnoreCase(department);
    }
    
    /**
     * ✍️ 是否为编剧
     */
    public boolean isWriter() {
        return "Writer".equalsIgnoreCase(job) || "Writing".equalsIgnoreCase(department);
    }
    
    /**
     * 🎵 是否为制片人
     */
    public boolean isProducer() {
        return job != null && job.toLowerCase().contains("producer");
    }
    
    /**
     * 📱 获取显示名称
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        } else if (originalName != null && !originalName.isEmpty()) {
            return originalName;
        } else {
            return "未知演员";
        }
    }
    
    /**
     * 🎭 获取角色信息文本
     */
    public String getRoleText() {
        if (isActor() && character != null && !character.isEmpty()) {
            return "饰演 " + character;
        } else if (job != null && !job.isEmpty()) {
            return getLocalizedJob();
        } else {
            return "";
        }
    }
    
    /**
     * 🌐 获取本地化的职务名称
     */
    public String getLocalizedJob() {
        if (job == null) return "";
        
        switch (job.toLowerCase()) {
            case "actor":
                return "演员";
            case "director":
                return "导演";
            case "writer":
            case "screenplay":
                return "编剧";
            case "producer":
                return "制片人";
            case "executive producer":
                return "执行制片人";
            case "cinematography":
                return "摄影";
            case "music":
                return "作曲";
            case "editor":
                return "剪辑";
            case "production design":
                return "美术指导";
            case "costume design":
                return "服装设计";
            case "makeup":
                return "化妆";
            case "sound":
                return "音效";
            default:
                return job;
        }
    }
    
    /**
     * 📊 获取作品统计文本
     */
    public String getCreditsText() {
        int totalCredits = movieCredits + tvCredits;
        if (totalCredits > 0) {
            if (movieCredits > 0 && tvCredits > 0) {
                return String.format("%d部电影 • %d部电视剧", movieCredits, tvCredits);
            } else if (movieCredits > 0) {
                return String.format("%d部电影", movieCredits);
            } else {
                return String.format("%d部电视剧", tvCredits);
            }
        }
        return "";
    }
    
    /**
     * 🏆 获取知名作品文本
     */
    public String getKnownForText() {
        if (knownFor != null && knownFor.length > 0) {
            if (knownFor.length == 1) {
                return "代表作：" + knownFor[0];
            } else {
                return "代表作：" + String.join("、", knownFor);
            }
        }
        return "";
    }
    
    /**
     * 🌟 是否为主要演员 (前8位)
     */
    public boolean isMainCast() {
        return isActor() && order < 8;
    }
    
    /**
     * 🎭 获取性别文本
     */
    public String getGenderText() {
        if (gender == null) return "";
        
        switch (gender.toLowerCase()) {
            case "male":
            case "m":
            case "1":
                return "男";
            case "female":
            case "f":
            case "2":
                return "女";
            default:
                return "";
        }
    }
    
    /**
     * 📅 获取年龄 (如果有生日信息)
     */
    public String getAgeText() {
        if (birthday == null || birthday.isEmpty()) {
            return "";
        }
        
        try {
            // 简单的年龄计算 (假设生日格式为 yyyy-MM-dd)
            String[] parts = birthday.split("-");
            if (parts.length >= 1) {
                int birthYear = Integer.parseInt(parts[0]);
                int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                int age = currentYear - birthYear;
                return age + "岁";
            }
        } catch (NumberFormatException e) {
            // 解析失败，返回空字符串
        }
        
        return "";
    }
    
    /**
     * 📍 获取出生信息文本
     */
    public String getBirthInfoText() {
        StringBuilder info = new StringBuilder();
        
        if (birthday != null && !birthday.isEmpty()) {
            info.append(birthday);
        }
        
        if (placeOfBirth != null && !placeOfBirth.isEmpty()) {
            if (info.length() > 0) {
                info.append(" • ");
            }
            info.append(placeOfBirth);
        }
        
        return info.toString();
    }
    
    /**
     * 🖼️ 是否有头像图片
     */
    public boolean hasProfileImage() {
        return (profileUrl != null && !profileUrl.isEmpty()) || 
               (profilePath != null && !profilePath.isEmpty());
    }
    
    /**
     * 🖼️ 获取完整的头像URL
     */
    public String getFullProfileUrl() {
        if (profileUrl != null && !profileUrl.isEmpty()) {
            return profileUrl;
        } else if (profilePath != null && !profilePath.isEmpty()) {
            // 如果只有路径，需要拼接base URL
            // 这里需要根据实际的图片服务器地址进行拼接
            return "https://image.tmdb.org/t/p/w185" + profilePath; // 示例URL
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "Actor{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", character='" + character + '\'' +
                ", job='" + job + '\'' +
                ", order=" + order +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Actor actor = (Actor) obj;
        return id != null ? id.equals(actor.id) : actor.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
