package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 弹幕渲染引擎
 * 
 * 职责：
 * - 根据视频时间戳计算可见弹幕
 * - 应用影院模式样式（FR-02）
 * - 处理滚动/固定类型弹幕（FR-01）
 * - 自适应字体大小（FR-03）
 * - 优化：只允许2行弹幕，连续滚动，不遮挡
 * 
 * @author nastv
 * @version 2.0
 */
public class DanmuRenderer {
    
    private static final String TAG = "DanmuRenderer";
    
    private final Context context;
    private DanmuConfig config;
    private final Random random = new Random();
    
    // 弹幕数据源（按时间戳索引）
    private Map<String, List<DanmakuEntity>> danmakuDataMap;
    
    // 当前激活的弹幕列表
    private final List<DanmakuEntity> activeDanmakuList = new ArrayList<>();
    
    // 优化：只使用2个轨道
    private static final int MAX_TRACKS = 2;
    
    // 轨道状态：记录每个轨道最后一条弹幕的右边缘位置和速度
    private final float[] trackLastRightEdge = new float[MAX_TRACKS];
    private final float[] trackLastSpeed = new float[MAX_TRACKS];
    private final long[] trackLastStartTime = new long[MAX_TRACKS];
    private final int[] trackLastTextLength = new int[MAX_TRACKS];  // 记录上一条弹幕的文字长度
    
    // 性能参数
    private int viewWidth = 1920;
    private int viewHeight = 1080;
    private static final int FIXED_FONT_SIZE = 56;  // 统一固定字体大小
    private int fontSize = FIXED_FONT_SIZE;
    
    // 弹幕间距（像素）- 增大间距防止重叠
    private static final int DANMAKU_GAP = 350;
    
    // 长弹幕阈值（超过15个字符视为长弹幕）
    private static final int LONG_DANMAKU_THRESHOLD = 15;
    
    // 弹幕滚动速度（像素/秒）- 减慢速度
    private static final float SCROLL_SPEED = 150f;
    
    public DanmuRenderer(Context context, DanmuConfig config) {
        this.context = context;
        this.config = config != null ? config : DanmuConfig.createCinemaMode();
        calculateAdaptiveFontSize();
        resetTrackState();
    }
    
    private void resetTrackState() {
        for (int i = 0; i < MAX_TRACKS; i++) {
            trackLastRightEdge[i] = -1000;
            trackLastSpeed[i] = 0;
            trackLastStartTime[i] = 0;
            trackLastTextLength[i] = 0;
        }
    }
    
    public void setDanmakuData(Map<String, List<DanmakuEntity>> dataMap) {
        this.danmakuDataMap = dataMap;
        Log.d(TAG, "弹幕数据已加载，共 " + (dataMap != null ? dataMap.size() : 0) + " 个时间段");
    }
    
    public void updateViewSize(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        calculateAdaptiveFontSize();
        Log.d(TAG, "视图尺寸更新: " + width + "x" + height + ", 字体大小: " + fontSize);
    }
    
    public void updateConfig(DanmuConfig newConfig) {
        this.config = newConfig;
        calculateAdaptiveFontSize();
    }

    
    /**
     * 计算自适应字体大小 - 统一固定字体
     */
    private void calculateAdaptiveFontSize() {
        // 使用固定字体大小，不再根据屏幕变化
        fontSize = FIXED_FONT_SIZE;
        Log.d(TAG, "固定字体大小: " + fontSize + "px");
    }
    
    public List<DanmakuEntity> calculateVisibleDanmaku(long currentPositionMs) {
        List<DanmakuEntity> visibleList = new ArrayList<>();
        
        if (danmakuDataMap == null || danmakuDataMap.isEmpty()) {
            return visibleList;
        }
        
        int topBoundary = (int) (viewHeight * config.topMarginPercent);
        int bottomBoundary = (int) (viewHeight * (1.0f - config.bottomMarginPercent));
        
        List<DanmakuEntity> stillActive = new ArrayList<>();
        for (DanmakuEntity entity : activeDanmakuList) {
            if (updateDanmakuPosition(entity, currentPositionMs)) {
                if (isWithinRenderArea(entity, topBoundary, bottomBoundary)) {
                    visibleList.add(entity);
                    stillActive.add(entity);
                }
            }
        }
        activeDanmakuList.clear();
        activeDanmakuList.addAll(stillActive);
        
        long bucketId = currentPositionMs / 60000;
        String bucketKey = (bucketId * 60000) + "-" + ((bucketId + 1) * 60000);
        
        List<DanmakuEntity> bucketData = danmakuDataMap.get(bucketKey);
        
        if (bucketData != null) {
            long timeWindowStart = currentPositionMs - 100;
            long timeWindowEnd = currentPositionMs + 100;
            
            for (DanmakuEntity entity : bucketData) {
                if (entity.time >= timeWindowStart && entity.time <= timeWindowEnd) {
                    boolean alreadyActive = false;
                    for (DanmakuEntity active : activeDanmakuList) {
                        if (active == entity) {
                            alreadyActive = true;
                            break;
                        }
                    }
                    
                    if (!alreadyActive) {
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
    
    private boolean updateDanmakuPositionSmooth(DanmakuEntity entity, float deltaTimeMs) {
        if (entity.isScrollType()) {
            float distance = (deltaTimeMs / 1000.0f) * entity.speed;
            entity.currentX -= distance;
            
            float textWidth = entity.text.length() * fontSize * 0.55f;
            return entity.currentX > -textWidth;
        } else {
            long elapsed = System.currentTimeMillis() - entity.startTimeMs;
            return elapsed < 5000;
        }
    }
    
    /**
     * 计算当前可见的弹幕列表（帧同步版本）
     * 优化：连续滚动，不等待一屏结束
     * 新增：清理已使用的弹幕，释放内存
     */
    public List<DanmakuEntity> calculateVisibleDanmakuSmooth(long currentPositionMs, float deltaTimeMs) {
        List<DanmakuEntity> visibleList = new ArrayList<>();
        
        if (danmakuDataMap == null || danmakuDataMap.isEmpty()) {
            return visibleList;
        }
        
        int lineHeight = fontSize + 16;
        int topMargin = 30;
        
        List<DanmakuEntity> stillActive = new ArrayList<>();
        for (DanmakuEntity entity : activeDanmakuList) {
            if (updateDanmakuPositionSmooth(entity, deltaTimeMs)) {
                visibleList.add(entity);
                stillActive.add(entity);
                
                if (entity.trackIndex >= 0 && entity.trackIndex < MAX_TRACKS) {
                    float textWidth = entity.text.length() * fontSize * 0.55f;
                    float rightEdge = entity.currentX + textWidth;
                    trackLastRightEdge[entity.trackIndex] = rightEdge;
                }
            } else {
                // 弹幕已离开屏幕，回收到对象池
                entity.recycle();
            }
        }
        activeDanmakuList.clear();
        activeDanmakuList.addAll(stillActive);
        
        long bucketId = currentPositionMs / 60000;
        String bucketKey = (bucketId * 60000) + "-" + ((bucketId + 1) * 60000);
        
        List<DanmakuEntity> bucketData = danmakuDataMap.get(bucketKey);
        
        if (bucketData != null) {
            long timeWindowStart = currentPositionMs - 100;
            long timeWindowEnd = currentPositionMs + 100;
            
            // 使用迭代器，支持在遍历时删除已使用的弹幕
            Iterator<DanmakuEntity> iterator = bucketData.iterator();
            while (iterator.hasNext()) {
                DanmakuEntity entity = iterator.next();
                
                if (entity.time >= timeWindowStart && entity.time <= timeWindowEnd) {
                    boolean alreadyActive = false;
                    for (DanmakuEntity active : activeDanmakuList) {
                        if (active == entity) {
                            alreadyActive = true;
                            break;
                        }
                    }
                    
                    if (!alreadyActive) {
                        if (initializeDanmakuPositionSmooth(entity, topMargin, lineHeight)) {
                            activeDanmakuList.add(entity);
                            visibleList.add(entity);
                            // 从缓存中移除已使用的弹幕，释放内存
                            iterator.remove();
                        }
                    }
                }
            }
        }
        
        // 清理已过期的时间桶（当前时间之前超过2分钟的桶）
        cleanupOldBuckets(currentPositionMs);
        
        return visibleList;
    }
    
    /**
     * 清理已过期的时间桶，释放内存
     */
    private void cleanupOldBuckets(long currentPositionMs) {
        if (danmakuDataMap == null) return;
        
        // 清理当前时间之前超过2分钟的桶
        long cleanupThreshold = currentPositionMs - 120000; // 2分钟前
        
        Iterator<Map.Entry<String, List<DanmakuEntity>>> iterator = danmakuDataMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<DanmakuEntity>> entry = iterator.next();
            String key = entry.getKey();
            
            // 解析桶的结束时间
            try {
                String[] parts = key.split("-");
                if (parts.length == 2) {
                    long bucketEnd = Long.parseLong(parts[1]);
                    if (bucketEnd < cleanupThreshold) {
                        // 回收桶中的所有弹幕实体
                        List<DanmakuEntity> entities = entry.getValue();
                        if (entities != null) {
                            for (DanmakuEntity entity : entities) {
                                entity.recycle();
                            }
                            entities.clear();
                        }
                        iterator.remove();
                        Log.d(TAG, "清理过期弹幕桶: " + key);
                    }
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
    }

    
    /**
     * 初始化弹幕位置（帧同步版本）
     * 优化：智能轨道分配，确保不遮挡，连续滚动
     */
    private boolean initializeDanmakuPositionSmooth(DanmakuEntity entity, int topMargin, int lineHeight) {
        entity.startTimeMs = System.currentTimeMillis();
        
        if (entity.isScrollType()) {
            float textWidth = entity.text.length() * fontSize * 0.55f;
            
            // 使用固定的慢速度，不再有随机变化
            entity.speed = SCROLL_SPEED;
            
            // 智能选择轨道
            int bestTrack = findBestTrack(textWidth, entity.speed);
            
            if (bestTrack < 0) {
                return false;
            }
            
            entity.trackIndex = bestTrack;
            
            // 计算Y坐标：2行弹幕，可以有轻微的随机偏移
            int baseY = topMargin + (bestTrack + 1) * lineHeight;
            int randomOffset = random.nextInt(11) - 5;
            entity.currentY = baseY + randomOffset;
            
            entity.currentX = viewWidth;
            
            trackLastRightEdge[bestTrack] = viewWidth + textWidth;
            trackLastSpeed[bestTrack] = entity.speed;
            trackLastStartTime[bestTrack] = System.currentTimeMillis();
            trackLastTextLength[bestTrack] = entity.text.length();  // 记录弹幕长度
            
        } else if (entity.isTopFixed()) {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = topMargin + fontSize;
            entity.speed = 0;
        } else {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = topMargin + lineHeight * 2;
            entity.speed = 0;
        }
        
        return true;
    }
    
    /**
     * 智能选择最佳轨道
     */
    private int findBestTrack(float newTextWidth, float newSpeed) {
        int startTrack = random.nextInt(MAX_TRACKS);
        
        for (int i = 0; i < MAX_TRACKS; i++) {
            int track = (startTrack + i) % MAX_TRACKS;
            
            if (canLaunchOnTrack(track, newTextWidth, newSpeed)) {
                return track;
            }
        }
        
        int bestTrack = -1;
        float minRightEdge = Float.MAX_VALUE;
        
        for (int track = 0; track < MAX_TRACKS; track++) {
            if (trackLastRightEdge[track] < minRightEdge) {
                minRightEdge = trackLastRightEdge[track];
                bestTrack = track;
            }
        }
        
        // 计算实际间距：如果上一条是长弹幕（>15字），使用2倍间距
        if (bestTrack >= 0) {
            int lastLength = trackLastTextLength[bestTrack];
            int effectiveGap = (lastLength > LONG_DANMAKU_THRESHOLD) ? DANMAKU_GAP * 2 : DANMAKU_GAP;
            
            if (minRightEdge < viewWidth - effectiveGap) {
                return bestTrack;
            }
        }
        
        return -1;
    }
    
    /**
     * 检查是否可以在指定轨道发射新弹幕
     * 增大间距要求，防止重叠
     * 长弹幕（>15字）后的下一条弹幕需要2倍间隔
     */
    private boolean canLaunchOnTrack(int track, float newTextWidth, float newSpeed) {
        float lastRightEdge = trackLastRightEdge[track];
        
        if (lastRightEdge < 0) {
            return true;
        }
        
        // 计算实际间距：如果上一条是长弹幕（>15字），使用2倍间距
        int lastLength = trackLastTextLength[track];
        int effectiveGap = (lastLength > LONG_DANMAKU_THRESHOLD) ? DANMAKU_GAP * 2 : DANMAKU_GAP;
        
        // 增大间距要求：最后一条弹幕的右边缘必须已经进入屏幕足够距离
        if (lastRightEdge < viewWidth - effectiveGap) {
            float lastSpeed = trackLastSpeed[track];
            
            // 更严格的追赶检测
            if (newSpeed > lastSpeed * 0.9f && lastSpeed > 0) {
                // 计算新弹幕是否会在旧弹幕离开屏幕前追上
                float speedDiff = newSpeed - lastSpeed;
                if (speedDiff > 0) {
                    float distance = viewWidth - lastRightEdge + effectiveGap + newTextWidth;
                    float catchUpTime = distance / speedDiff;
                    
                    // 旧弹幕完全离开屏幕所需时间
                    float oldExitTime = (lastRightEdge + 200) / lastSpeed;
                    
                    if (catchUpTime < oldExitTime) {
                        return false;
                    }
                }
            }
            return true;
        }
        
        return false;
    }
    
    private boolean updateDanmakuPosition(DanmakuEntity entity, long currentTimeMs) {
        long elapsed = currentTimeMs - entity.time;
        
        if (entity.isScrollType()) {
            float distance = (elapsed / 1000.0f) * config.scrollSpeed;
            entity.currentX = viewWidth - distance;
            return entity.currentX > -200;
        } else {
            return elapsed < 5000;
        }
    }
    
    private boolean initializeDanmakuPosition(DanmakuEntity entity, long currentTimeMs, 
                                              int topBoundary, int bottomBoundary) {
        if (entity.isScrollType()) {
            entity.currentX = viewWidth;
            entity.speed = SCROLL_SPEED;  // 使用固定慢速度
            
            int track = random.nextInt(MAX_TRACKS);
            entity.trackIndex = track;
            
            int lineHeight = fontSize + 16;
            entity.currentY = topBoundary + 30 + (track + 1) * lineHeight;
            
        } else if (entity.isTopFixed()) {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = topBoundary + fontSize;
        } else {
            entity.currentX = viewWidth / 2.0f;
            entity.currentY = bottomBoundary - fontSize;
        }
        
        return true;
    }
    
    private boolean isWithinRenderArea(DanmakuEntity entity, int topBoundary, int bottomBoundary) {
        return entity.currentY >= topBoundary && entity.currentY <= bottomBoundary;
    }
    
    public int getFontSize() {
        return fontSize;
    }
    
    public void clear() {
        activeDanmakuList.clear();
        resetTrackState();
    }
}
