package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 媒体分类数据模型
 * 对应Web项目中的媒体分类功能
 * 用于主页分类显示和列表页筛选
 */
public class MediaCategory implements Parcelable {
    private String id;           // 分类ID
    private String name;         // 分类名称 (电影、电视剧、动漫等)
    private String type;         // 分类类型 (movie, tv, anime等)
    private int iconResource;    // 分类图标资源ID
    private int count;           // 该分类下的媒体数量
    private String description;  // 分类描述
    private boolean isSelected;  // 是否选中状态
    
    // 构造函数
    public MediaCategory() {}
    
    public MediaCategory(String id, String name, String type, int iconResource) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.iconResource = iconResource;
        this.count = 0;
        this.isSelected = false;
    }
    
    public MediaCategory(String id, String name, String type, int iconResource, int count) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.iconResource = iconResource;
        this.count = count;
        this.isSelected = false;
    }
    
    // Parcelable实现
    protected MediaCategory(Parcel in) {
        id = in.readString();
        name = in.readString();
        type = in.readString();
        iconResource = in.readInt();
        count = in.readInt();
        description = in.readString();
        isSelected = in.readByte() != 0;
    }
    
    public static final Creator<MediaCategory> CREATOR = new Creator<MediaCategory>() {
        @Override
        public MediaCategory createFromParcel(Parcel in) {
            return new MediaCategory(in);
        }
        
        @Override
        public MediaCategory[] newArray(int size) {
            return new MediaCategory[size];
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
        dest.writeString(type);
        dest.writeInt(iconResource);
        dest.writeInt(count);
        dest.writeString(description);
        dest.writeByte((byte) (isSelected ? 1 : 0));
    }
    
    // Getter和Setter方法
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public int getIconResource() {
        return iconResource;
    }
    
    public void setIconResource(int iconResource) {
        this.iconResource = iconResource;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isSelected() {
        return isSelected;
    }
    
    public void setSelected(boolean selected) {
        isSelected = selected;
    }
    
    // 辅助方法
    @Override
    public String toString() {
        return "MediaCategory{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", count=" + count +
                ", isSelected=" + isSelected +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MediaCategory that = (MediaCategory) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
