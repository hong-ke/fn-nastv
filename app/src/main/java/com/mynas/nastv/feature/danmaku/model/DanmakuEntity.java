package com.mynas.nastv.feature.danmaku.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 弹幕实体类
 * 
 * 对应后端 /danmu/get API 返回的弹幕数据。
 * 使用 @SerializedName 注解防止混淆问题。
 * 支持对象池复用，减少内存波动（NFR-03）。
 * 
 * @author nastv
 * @version 1.1
 */
public class DanmakuEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // 对象池配置
    private static final int MAX_POOL_SIZE = 200;
    private static final Queue<DanmakuEntity> sPool = new LinkedList<>();

    /**
     * 从对象池获取实例
     */
    public static DanmakuEntity obtain() {
        synchronized (sPool) {
            DanmakuEntity entity = sPool.poll();
            if (entity != null) {
                entity.reset();
                return entity;
            }
        }
        return new DanmakuEntity();
    }

    /**
     * 将实例回收到对象池
     */
    public void recycle() {
        synchronized (sPool) {
            if (sPool.size() < MAX_POOL_SIZE) {
                this.reset();
                sPool.offer(this);
            }
        }
    }
    
    /**
     * 弹幕出现时间（视频播放时间戳，单位：毫秒）
     */
    @SerializedName("time")
    public long time;
    
    /**
     * 弹幕文本内容
     */
    @SerializedName("text")
    public String text;
    
    /**
     * 弹幕类型模式
     * 0 = 滚动（从右向左）
     * 1 = 顶部固定
     * 2 = 底部固定
     */
    @SerializedName("mode")
    public int mode;
    
    /**
     * 弹幕颜色（十六进制颜色代码，如 "#FFFFFF"）
     */
    @SerializedName("color")
    public String color;
    
    // 运行时计算字段（不序列化）
    
    /**
     * 当前 X 坐标位置（用于滚动类型弹幕）
     */
    public transient float currentX;
    
    /**
     * 当前 Y 坐标位置
     */
    public transient float currentY;
    
    /**
     * 弹幕轨道编号（用于防止重叠）
     */
    public transient int trackIndex;
    
    /**
     * 弹幕开始显示的系统时间（用于帧同步滚动）
     */
    public transient long startTimeMs;
    
    /**
     * 弹幕滚动速度（像素/秒）
     */
    public transient float speed;
    
    /**
     * 构造函数
     */
    public DanmakuEntity() {
        this.time = 0;
        this.text = "";
        this.mode = 0;
        this.color = "#FFFFFF";
        this.currentX = 0;
        this.currentY = 0;
        this.trackIndex = 0;
    }
    
    /**
     * 重置实体数据（用于对象池复用）
     * 
     * 清除所有字段值，防止内存泄漏。
     */
    public void reset() {
        this.time = 0;
        this.text = null;
        this.mode = 0;
        this.color = null;
        this.currentX = 0;
        this.currentY = 0;
        this.trackIndex = 0;
        this.startTimeMs = 0;
        this.speed = 0;
    }
    
    /**
     * 判断是否为滚动类型弹幕
     */
    public boolean isScrollType() {
        return mode == 0;
    }
    
    /**
     * 判断是否为顶部固定类型弹幕
     */
    public boolean isTopFixed() {
        return mode == 1;
    }
    
    /**
     * 判断是否为底部固定类型弹幕
     */
    public boolean isBottomFixed() {
        return mode == 2;
    }
    
    @Override
    public String toString() {
        return "DanmakuEntity{" +
                "time=" + time +
                ", text='" + text + '\'' +
                ", mode=" + mode +
                ", color='" + color + '\'' +
                '}';
    }
}
