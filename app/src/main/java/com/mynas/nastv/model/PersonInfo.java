package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 👥 PersonInfo - 演职人员数据模型
 * 用于展示导演、演员、编剧等信息
 */
public class PersonInfo {

    @SerializedName("guid")
    private String guid;

    @SerializedName("name")
    private String name;

    @SerializedName("role")
    private String role;  // 角色名，如 "饰演 阿宝"

    @SerializedName("job")
    private String job;   // 职位：Director/Actor/Writer

    @SerializedName("profile_path")
    private String profilePath;  // 头像路径

    @SerializedName("order")
    private int order;  // 排序

    @SerializedName("character")
    private String character;  // 角色名（另一种字段名）

    @SerializedName("department")
    private String department;  // 部门

    @SerializedName("known_for_department")
    private String knownForDepartment;

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

    public String getRole() {
        // 优先返回 role，如果为空则返回 character
        if (role != null && !role.isEmpty()) {
            return role;
        }
        return character;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getProfilePath() {
        return profilePath;
    }

    public void setProfilePath(String profilePath) {
        this.profilePath = profilePath;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getKnownForDepartment() {
        return knownForDepartment;
    }

    public void setKnownForDepartment(String knownForDepartment) {
        this.knownForDepartment = knownForDepartment;
    }

    /**
     * 判断是否是导演
     */
    public boolean isDirector() {
        return "Director".equalsIgnoreCase(job) || "Directing".equalsIgnoreCase(department);
    }

    /**
     * 判断是否是演员
     */
    public boolean isActor() {
        return "Actor".equalsIgnoreCase(job) || "Acting".equalsIgnoreCase(department) 
                || "Acting".equalsIgnoreCase(knownForDepartment);
    }

    /**
     * 判断是否是编剧
     */
    public boolean isWriter() {
        return "Writer".equalsIgnoreCase(job) || "Writing".equalsIgnoreCase(department);
    }

    /**
     * 获取显示用的职位名称
     */
    public String getJobDisplayName() {
        if (isDirector()) {
            return "导演";
        } else if (isActor()) {
            return "演员";
        } else if (isWriter()) {
            return "编剧";
        }
        return job != null ? job : "";
    }

    /**
     * 获取显示用的角色描述
     */
    public String getRoleDescription() {
        String roleName = getRole();
        if (roleName != null && !roleName.isEmpty()) {
            return "饰演 " + roleName;
        }
        return getJobDisplayName();
    }
}
