package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 弹幕渲染引擎
 * 
 * 职责：
 * - 根据视频时间戳计算可见弹幕
 * - 应用影院模式样式（FR-02）
 * - 处理滚动/固定类型弹幕（FR-01）
 * - 自适应字体大小（FR-03）
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmuRenderer {
    
    private static final String TAG = "DanmuRenderer";
    
    private final Context context;
    private DanmuConfig config;
    
    // 弹幕数据源（按时间戳索引）
    private Map<String, List<DanmakuEntity>> danmakuDataMap;
    
    // 当前激活的弹幕列表
    private final List<DanmakuEntity> activeDanmakuList = new ArrayList<>();
    
    // 轨道管理（防止重叠）
    private static final int MAX_TRACKS = 10;
    private final long[] trackOccupiedUntil = new long[MAX_TRACKS];
    
    // 性能参数
    private int viewWidth = 1920;
    private int viewHeight = 1080;
    private int fontSize = 36;
    
    public DanmuRenderer(Context context, DanmuConfig config) {
        this.context = context;
        this.config = config != null ? config : DanmuConfig.createCinemaMode();
        calculateAdaptiveFontSize();
    }
    
    /**
     * 设置弹幕数据源
     * 
     * @param dataMap 弹幕数据映射（key 为时间戳范围，value 为弹幕列表）
     */
    public void setDanmakuData(Map<String, List<DanmakuEntity>> dataMap) {
        this.danmakuDataMap = dataMap;
        Log.d(TAG, "弹幕数据已加载，共 " + (dataMap != null ? dataMap.size() : 0) + " 个时间段");
    }
    
    /**
     * 更新视图尺寸
     * 
     * @param width  视图宽度（像素）
     * @param height 视图高度（像素）
     */
    public void updateViewSize(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        calculateAdaptiveFontSize();
        Log.d(TAG, "视图尺寸更新: " + width + "x" + height + ", 字体大小: " + fontSize);
    }
    
    /**
     * 更新配置
     * 
     * @param newConfig 新配置
     */
    public void updateConfig(DanmuConfig newConfig) {
        this.config = newConfig;
        calculateAdaptiveFontSize();
    }
    
    /**
     * 计算自适应字体大小（FR-03）
     * 
     * 根据屏幕分辨率自动调整字体大小，确保 1080p/4K 视觉一致。
     */
    private void calculateAdaptiveFontSize() {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        
        // 基准字体大小（根据DPI）
        float baseSize;
        if (metrics.densityDpi >= DisplayMetrics.DENSITY_XXHIGH) {
            baseSize = 48; // 高DPI设备
        } else {
            baseSize = 36; // 标准设备
        }
        
        // 根据实际高度缩放（以 1080p 为基准）
        float scaleFactor = viewHeight / 1080.0f;
        fontSize = (int) (baseSize * scaleFactor);
        
        // 应用配置中的字体大小（如果已设置）
        if (config.fontSize > 0) {
            fontSize = config.fontSize;
        }
        
        Log.d(TAG, "自适应字体大小: " + fontSize + "px (DPI: " + metrics.densityDpi + ", 高度: " + viewHeight + ")");
    }
    
    /**
     * 计算当前可见的弹幕列表
     * 
     * @param currentPositionMs 当前播放位置（毫秒）
     * @return 可见弹幕列表
     */
    public List<DanmakuEntity> calculateVisibleDanmaku(long currentPositionMs) {
        List<DanmakuEntity> visibleList = new ArrayList<>();
        
        if (danmakuDataMap == null || danmakuDataMap.isEmpty()) {
            return visibleList;
        }
        
        // 计算渲染区域边界（影院模式 FR-02）
        int topBoundary = (int) (viewHeight * config.topMarginPercent);
        int bottomBoundary = (int) (viewHeight * (1.0f - config.bottomMarginPercent));
        
        // 重置轨道占用状态
        for (int i = 0; i < MAX_TRACKS; i++) {
            if (trackOccupiedUntil[i] < currentPositionMs) {
                trackOccupiedUntil[i] = 0;
            }
        }
        
        // 更新现有激活弹幕的位置
        List<DanmakuEntity> stillActive = new ArrayList<>();
        for (DanmakuEntity entity : activeDanmakuList) {
            if (updateDanmakuPosition(entity, currentPositionMs)) {
                // 仍然可见
                if (isWithinRenderArea(entity, topBoundary, bottomBoundary)) {
                    visibleList.add(entity);
                    stillActive.add(entity);
                }
            }
        }
        activeDanmakuList.clear();
        activeDanmakuList.addAll(stillActive);
        
        // 添加新弹幕（按 60s 为单位的 Bucket 查找以提高性能）
        // 飞牛 API 返回的数据通常按 60s 分片，key 格式为 "0-60000", "60000-120000" 等
        long bucketId = currentPositionMs / 60000;
        String bucketKey = (bucketId * 60000) + "-" + ((bucketId + 1) * 60000);
        
        List<DanmakuEntity> bucketData = danmakuDataMap.get(bucketKey);
        
        if (bucketData != null) {
            long timeWindowStart = currentPositionMs - 100; // 100ms 容错
            long timeWindowEnd = currentPositionMs + 100;
            
            for (DanmakuEntity entity : bucketData) {
                if (entity.time >= timeWindowStart && entity.time <= timeWindowEnd) {
                    // 检查是否已在激活列表中
                    boolean alreadyActive = false;
                    for (DanmakuEntity active : activeDanmakuList) {
                        if (active == entity) { // 同一对象引用
                            alreadyActive = true;
                            break;
                        }
                    }
                    
                    if (!alreadyActive) {
                        // 初始化新弹幕位置
                        if (initializeDanmakuPosition(entity, currentPositionMs, topBoundary, bottomBoundary)) {
                            activeDanmakuList.add(entity);
                            visibleList.add(entity);
                        }
                    }
                }
            }
        }
        
        return visibleList;
    }
    
    /**
     * 更新弹幕位置（基于帧间隔时间，实现线性滚动）
     * 
     * @param entity          弹幕实体
     * @param deltaTimeMs     帧间隔时间（毫秒）
     * @return true 如果弹幕仍然可见
     */
    private boolean updateDanmakuPositionSmooth(DanmakuEntity entity, float deltaTimeMs) {
        if (entity.isScrollType()) {
            // 🎬 线性滚动：根据帧间隔时间计算移动距离
            // 速度单位：像素/秒，deltaTimeMs 单位：毫秒
            float distance = (deltaTimeMs / 1000.0f) * config.scrollSpeed;
            entity.currentX -= distance;
            
            // 判断是否已完全离开屏幕（文本宽度估算）
            float textWidth = entity.text.length() * fontSize * 0.6f;
            return entity.currentX > -textWidth;
        } else {
            // 固定类型：检查显示时长（5秒）
            long elapsed = System.currentTimeMillis() - entity.startTimeMs;
            return elapsed < 5000;
        }
    }
    
    /**
     * 计算当前可见的弹幕列表（帧同步版本，实现线性滚动）
     * 
     * @param currentPositionMs 当前播放位置（毫秒）
     * @param deltaTimeMs       帧间隔时间（毫秒）
     * @return 可见弹幕列表
     */
    public List<DanmakuEntity> calculateVisibleDanmakuSmooth(long currentPositionMs, float deltaTimeMs) {
        List<DanmakuEntity> visibleList = new ArrayList<>();
        
        if (danmakuDataMap == null || danmakuDataMap.isEmpty()) {
            return visibleList;
        }
        
        // 计算渲染区域边界（影院模式 FR-02）
        int topBoundary = (int) (viewHeight * config.topMarginPercent);
        int bottomBoundary = (int) (viewHeight * (1.0f - config.bottomMarginPercent));
        
        // 重置过期轨道
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAX_TRACKS; i++) {
            if (trackOccupiedUntil[i] < now) {
                trackOccupiedUntil[i] = 0;
            }
        }
        
        // 🎬 更新现有激活弹幕的位置（使用帧间隔时间实现线性滚动）
        List<DanmakuEntity> stillActive = new ArrayList<>();
        for (DanmakuEntity entity : activeDanmakuList) {
            if (updateDanmakuPositionSmooth(entity, deltaTimeMs)) {
                // 仍然可见
                if (isWithinRenderArea(entity, topBoundary, bottomBoundary)) {
                    visibleList.add(entity);
                    stillActive.add(entity);
                }
            }
        }
        activeDanmakuList.clear();
        activeDanmakuList.addAll(stillActive);
        
        // 添加新弹幕（按 60s 为单位的 Bucket 查找）
        long bucketId = currentPositionMs / 60000;
        String bucketKey = (bucketId * 60000) + "-" + ((bucketId + 1) * 60000);
        
        List<DanmakuEntity> bucketData = danmakuDataMap.get(bucketKey);
        
        if (bucketData != null) {
            long timeWindowStart = currentPositionMs - 100; // 100ms 容错
            long timeWindowEnd = currentPositionMs + 100;
            
            for (DanmakuEntity entity : bucketData) {
                if (entity.time >= timeWindowStart && entity.time <= timeWindowEnd) {
                    // 检查是否已在激活列表中
                    boolean alreadyActive = false;
                    for (DanmakuEntity active : activeDanmakuList) {
                        if (active == entity) {
                            alreadyActive = true;
                            break;
                        }
                    }
                    
                    if (!alreadyActive) {
                        // 初始化新弹幕位置
                        if (initializeDanmakuPositionSmooth(entity, topBoundary, bottomBoundary)) {
                            activeDanmakuList.add(entity);
                            visibleList.add(entity);
                        }
                    }
                }
            }
        }
        
        return visibleList;
    }
    
    /**
     * 初始化弹幕位置（帧同步版本）
     */
    private boolean initializeDanmakuPositionSmooth(DanmakuEntity entity, int topBoundary, int bottomBoundary) {
        // 记录开始时间（用于固定弹幕的显示时长计算）
        entity.startTimeMs = System.currentTimeMillis();
        
        if (entity.isScrollType()) {
            // 滚动类型：从右侧开始
            entity.currentX = viewWidth;
            
            // 分配轨道
            int track = findAvailableTrackSmooth();
            if (track < 0) {
                return false; // 没有可用轨道，丢弃此弹幕
            }
            
            entity.trackIndex = track;
            entity.currentY = topBoundary + (track + 1) * (fontSize + 10);
            
            // 标记轨道占用时间
            float textWidth = entity.text.length() * fontSize * 0.6f;
            long travelTime = (long) ((viewWidth + textWidth) / config.scrollSpeed * 1000);
            trackOccupiedUntil[track] = System.currentTimeMillis() + travelTime;
            
        } else if (entity.isTopFixed()) {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = topBoundary + fontSize;
        } else {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = bottomBoundary - fontSize;
        }
        
        return true;
    }
    
    /**
     * 查找可用轨道（使用系统时间）
     */
    private int findAvailableTrackSmooth() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAX_TRACKS; i++) {
            if (trackOccupiedUntil[i] <= now) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 更新弹幕位置（旧版本，保留兼容）
     * 
     * @param entity          弹幕实体
     * @param currentTimeMs   当前时间（毫秒）
     * @return true 如果弹幕仍然可见
     */
    private boolean updateDanmakuPosition(DanmakuEntity entity, long currentTimeMs) {
        long elapsed = currentTimeMs - entity.time;
        
        if (entity.isScrollType()) {
            // 滚动类型：从右向左移动
            float distance = (elapsed / 1000.0f) * config.scrollSpeed;
            entity.currentX = viewWidth - distance;
            
            // 判断是否已完全离开屏幕
            return entity.currentX > -200; // 留一些余量
        } else {
            // 固定类型：位置不变，但有显示时长限制（5秒）
            return elapsed < 5000;
        }
    }
    
    /**
     * 初始化弹幕位置
     * 
     * @param entity         弹幕实体
     * @param currentTimeMs  当前时间
     * @param topBoundary    顶部边界
     * @param bottomBoundary 底部边界
     * @return true 如果成功初始化
     */
    private boolean initializeDanmakuPosition(DanmakuEntity entity, long currentTimeMs, 
                                              int topBoundary, int bottomBoundary) {
        if (entity.isScrollType()) {
            // 滚动类型：从右侧开始
            entity.currentX = viewWidth;
            
            // 分配轨道
            int track = findAvailableTrack(currentTimeMs);
            if (track < 0) {
                return false; // 没有可用轨道，丢弃此弹幕
            }
            
            entity.trackIndex = track;
            entity.currentY = topBoundary + (track + 1) * (fontSize + 10); // 轨道间距 10px
            
            // 标记轨道占用时间（根据弹幕长度估算）
            float textWidth = entity.text.length() * fontSize * 0.6f; // 估算文本宽度
            long travelTime = (long) ((viewWidth + textWidth) / config.scrollSpeed * 1000);
            trackOccupiedUntil[track] = currentTimeMs + travelTime;
            
        } else if (entity.isTopFixed()) {
            // 顶部固定
            entity.currentX = viewWidth / 2.0f; // 居中
            entity.currentY = topBoundary + fontSize;
            
        } else {
            // 底部固定
            entity.currentX = viewWidth / 2.0f; // 居中
            entity.currentY = bottomBoundary - fontSize;
        }
        
        return true;
    }
    
    /**
     * 查找可用轨道
     * 
     * @param currentTimeMs 当前时间
     * @return 轨道索引，-1 表示无可用轨道
     */
    private int findAvailableTrack(long currentTimeMs) {
        for (int i = 0; i < MAX_TRACKS; i++) {
            if (trackOccupiedUntil[i] <= currentTimeMs) {
                return i;
            }
        }
        return -1; // 所有轨道都被占用
    }
    
    /**
     * 检查弹幕是否在渲染区域内
     * 
     * @param entity         弹幕实体
     * @param topBoundary    顶部边界
     * @param bottomBoundary 底部边界
     * @return true 如果在渲染区域内
     */
    private boolean isWithinRenderArea(DanmakuEntity entity, int topBoundary, int bottomBoundary) {
        return entity.currentY >= topBoundary && entity.currentY <= bottomBoundary;
    }
    
    /**
     * 获取当前字体大小
     * 
     * @return 字体大小（像素）
     */
    public int getFontSize() {
        return fontSize;
    }
    
    /**
     * 清空所有激活弹幕
     */
    public void clear() {
        activeDanmakuList.clear();
    }
}
