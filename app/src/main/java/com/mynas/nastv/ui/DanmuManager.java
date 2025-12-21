package com.mynas.nastv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mynas.nastv.model.Danmu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🎬 弹幕管理器
 * 负责弹幕的显示、动画和时间同步
 */
public class DanmuManager {
    private static final String TAG = "DanmuManager";
    
    private final Context context;
    private final FrameLayout danmuContainer;
    private final Handler mainHandler;
    private final List<Danmu> danmuList;
    private final List<TextView> activeDanmuViews;
    
    // 弹幕配置 - 参考web弹幕细节
    private static final int DANMU_SPEED = 8000; // 🔧 弹幕滚动时间（毫秒）- 改为8秒（0.5倍速度）
    private static final int DANMU_SIZE_SP = 18; // 弹幕字体大小
    private static final int DANMU_LINE_HEIGHT = 28; // 🔧 弹幕行高（dp）- 减小到28dp，更紧凑
    private static final int MAX_DANMU_LINES = 3; // 🔧 最大弹幕行数 - 减少到3行（顶部1/4区域）
    private static final long CHANNEL_TIMEOUT = 10000; // 🎯 通道超时时间（毫秒）- 增加到10秒
    private static final int DANMU_MARGIN_DP = 4; // 🔧 弹幕边距 - 减小到4dp
    
    // 🎨 web弹幕样式细节
    private static final float DANMU_ALPHA = 0.85f; // 弹幕透明度
    private static final int DANMU_STROKE_WIDTH = 2; // 描边宽度（dp）
    private static final int MIN_DANMU_SPACING = 200; // 🔧 弹幕最小间距（px）- 增加间距防止层叠
    private static final int MAX_DANMU_PER_SECOND = 20; // 🔧 每秒最大弹幕数量 - 增加密度到20条
    
    // 弹幕通道管理
    private final boolean[] danmuTracks = new boolean[MAX_DANMU_LINES]; // true表示通道被占用
    private final long[] trackOccupyTime = new long[MAX_DANMU_LINES]; // 🎯 通道占用时间记录
    private final TextView[] trackLastDanmu = new TextView[MAX_DANMU_LINES]; // 🎯 每个通道的最后一条弹幕
    
    // 🎨 web弹幕密度控制
    private long lastDanmuTime = 0; // 上一条弹幕显示时间
    private int danmuCountInSecond = 0; // 当前秒内显示的弹幕数量
    private long currentSecond = 0; // 当前秒数
    
    // 🔧 弹幕去重控制
    private final Set<String> recentDanmuTexts = Collections.synchronizedSet(new HashSet<String>()); // 最近显示的弹幕文本
    private static final int DEDUP_TIME_WINDOW = 30; // 去重时间窗口（秒）
    
    // 🎯 通道分配优化
    private int lastUsedTrack = -1; // 上次使用的通道，用于均匀分配
    
    private boolean isEnabled = true;
    private long currentPosition = 0; // 当前播放位置（毫秒）
    
    public DanmuManager(Context context, FrameLayout danmuContainer) {
        this.context = context;
        this.danmuContainer = danmuContainer;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.danmuList = new CopyOnWriteArrayList<>();
        this.activeDanmuViews = new CopyOnWriteArrayList<>();
        
        Log.d(TAG, "🎬 弹幕管理器初始化完成");
    }
    
    /**
     * 🎬 加载弹幕数据
     */
    public void loadDanmuList(List<Danmu> danmuList) {
        if (danmuList == null) {
            Log.w(TAG, "⚠️ 弹幕数据为null");
            return;
        }
        
        this.danmuList.clear();
        this.danmuList.addAll(danmuList);
        
        // 按时间排序
        Collections.sort(this.danmuList, new Comparator<Danmu>() {
            @Override
            public int compare(Danmu d1, Danmu d2) {
                return Integer.compare(d1.getTime(), d2.getTime());
            }
        });
        
        Log.d(TAG, "✅ 加载弹幕数据: " + danmuList.size() + "条");
        
        // 输出前几条弹幕用于调试
        for (int i = 0; i < Math.min(5, this.danmuList.size()); i++) {
            Danmu danmu = this.danmuList.get(i);
            Log.d(TAG, "📊 弹幕[" + i + "]: " + danmu.getTime() + "s -> " + danmu.getText());
        }
    }
    
    /**
     * 🕒 更新当前播放位置
     */
    public void updatePosition(long positionMs) {
        this.currentPosition = positionMs;
        
        // 检查是否有需要显示的弹幕
        long positionSeconds = positionMs / 1000;
        
        // 🔍 详细日志：显示当前播放时间和弹幕检查情况
        if (positionSeconds % 10 == 0) { // 每10秒输出一次日志，避免刷屏
            Log.d(TAG, "🕒 当前播放位置: " + positionSeconds + "秒, 弹幕总数: " + danmuList.size());
        }
        
        for (Danmu danmu : danmuList) {
            if (danmu.getTime() == positionSeconds) {
                Log.d(TAG, "🎬 时间匹配，准备显示弹幕: " + positionSeconds + "秒 -> " + danmu.getText());
                showDanmu(danmu);
            }
        }
    }
    
    /**
     * 🎬 显示单条弹幕
     */
    private void showDanmu(Danmu danmu) {
        if (!isEnabled) {
            Log.w(TAG, "⚠️ 弹幕功能已禁用，跳过显示: " + danmu.getText());
            return;
        }
        
        // 🔧 弹幕去重检查
        String danmuText = danmu.getText();
        if (danmuText == null || danmuText.trim().isEmpty()) {
            Log.w(TAG, "⚠️ 弹幕文本为空，跳过显示");
            return;
        }
        
        if (recentDanmuTexts.contains(danmuText)) {
            Log.w(TAG, "⚠️ 弹幕重复，跳过显示: " + danmuText);
            return;
        }
        
        if (danmuContainer == null) {
            Log.e(TAG, "❌ 弹幕容器为null，无法显示弹幕: " + danmu.getText());
            return;
        }
        
        Log.d(TAG, "🎬 开始显示弹幕: " + danmu.getText() + " (" + danmu.getTime() + "s)");
        Log.d(TAG, "📐 弹幕容器尺寸: " + danmuContainer.getWidth() + "x" + danmuContainer.getHeight());
        
        // 创建弹幕TextView
        TextView danmuView = createDanmuView(danmu);
        if (danmuView == null) {
            Log.e(TAG, "❌ 弹幕视图创建失败: " + danmu.getText());
            return;
        }
        
        Log.d(TAG, "✅ 弹幕视图创建成功: " + danmu.getText());
        
        // 寻找可用的弹幕通道
        int track = findAvailableTrack();
        if (track == -1) {
            Log.w(TAG, "⚠️ 没有可用的弹幕通道，跳过弹幕: " + danmu.getText());
            return;
        }
        
        // 设置弹幕位置
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        
        // 🔧 计算弹幕位置 - 使用整个容器高度而不是限制在1/4区域
        int containerHeight = danmuContainer.getHeight();
        
        // 🔧 修复：使用整个容器高度来分配弹幕行
        // 计算每行的实际高度，确保三行弹幕均匀分布在容器顶部
        int lineHeight = dpToPx(DANMU_LINE_HEIGHT);
        int topMargin = dpToPx(DANMU_MARGIN_DP) + (track * lineHeight);
        
        // 确保不超出容器边界
        int maxAllowedTopMargin = containerHeight - lineHeight;
        if (topMargin > maxAllowedTopMargin) {
            topMargin = maxAllowedTopMargin;
        }
        
        // 🔧 修复：弹幕从屏幕内开始显示，而不是屏幕外
        params.setMargins(danmuContainer.getWidth() - 200, topMargin, 0, 0); // 从右边200px开始
        params.gravity = Gravity.TOP | Gravity.START;
        
        Log.d(TAG, "📐 弹幕位置: 左边距=" + (danmuContainer.getWidth() - 200) + 
              ", 上边距=" + topMargin + ", 容器=" + danmuContainer.getWidth() + "x" + danmuContainer.getHeight() +
              ", 行高=" + lineHeight + ", 通道=" + track);
        
        danmuView.setLayoutParams(params);
        
        // 添加到容器
        Log.d(TAG, "📱 添加弹幕视图到容器: " + danmu.getText() + ", 通道: " + track);
        danmuContainer.addView(danmuView);
        activeDanmuViews.add(danmuView);
        Log.d(TAG, "📊 容器子视图数量: " + danmuContainer.getChildCount());
        
        // 占用通道并记录时间
        danmuTracks[track] = true;
        trackOccupyTime[track] = System.currentTimeMillis(); // 🎯 记录通道占用时间
        trackLastDanmu[track] = danmuView; // 🎯 记录最后一条弹幕
        
        // 🎨 增加密度计数
        danmuCountInSecond++;
        lastDanmuTime = System.currentTimeMillis();
        
        // 🔧 添加到去重集合，并延时清理
        recentDanmuTexts.add(danmuText);
        mainHandler.postDelayed(() -> {
            recentDanmuTexts.remove(danmuText);
            Log.d(TAG, "🧹 去重清理: " + danmuText);
        }, DEDUP_TIME_WINDOW * 1000); // 30秒后清理
        
        // 开始滚动动画
        Log.d(TAG, "🎭 开始弹幕动画: " + danmu.getText());
        startDanmuAnimation(danmuView, track, danmu);
    }
    
    /**
     * 🎨 创建弹幕视图 - 参考web弹幕细节
     */
    private TextView createDanmuView(Danmu danmu) {
        TextView textView = new TextView(context);
        
        try {
            // 设置文本内容
            textView.setText(danmu.getText());
            Log.d(TAG, "🎨 创建弹幕视图: " + danmu.getText());
            
            // 🎨 解析颜色 - 支持web弹幕多种颜色格式
            int textColor = parseWebDanmuColor(danmu.getColor());
            textView.setTextColor(textColor);
            
            // 🎨 设置透明度 - web弹幕常见特性
            textView.setAlpha(DANMU_ALPHA);
            
            // 🎨 根据弹幕模式调整字体大小
            float fontSize = getDanmuFontSize(danmu);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
            textView.setSingleLine(true);
            
            // 🎨 设置描边效果 - web弹幕的经典特性
            textView.setShadowLayer(dpToPx(DANMU_STROKE_WIDTH), 0, 0, Color.BLACK);
            
            // 🎨 边框效果（如果需要）
            if (danmu.isBorder()) {
                textView.setBackground(context.getDrawable(android.R.drawable.edit_text));
            }
            
            // 🎨 设置内边距确保文字清晰
            textView.setPadding(8, 4, 8, 4);
            
            Log.d(TAG, "✅ 弹幕视图属性设置完成: " + danmu.getText() + 
                  ", 颜色: " + Integer.toHexString(textColor) + 
                  ", 模式: " + danmu.getMode() + 
                  ", 字体大小: " + fontSize);
            return textView;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 创建弹幕视图失败: " + danmu.getText(), e);
            return null;
        }
    }
    
    /**
     * 🎨 解析web弹幕颜色
     */
    private int parseWebDanmuColor(String colorStr) {
        if (colorStr != null && colorStr.startsWith("#")) {
            try {
                return Color.parseColor(colorStr);
            } catch (Exception e) {
                Log.w(TAG, "⚠️ 颜色解析失败: " + colorStr + ", 使用默认白色");
            }
        }
        return Color.WHITE; // 默认白色
    }
    
    /**
     * 🎨 获取弹幕字体大小 - 根据模式和重要性调整
     */
    private float getDanmuFontSize(Danmu danmu) {
        float baseSize = DANMU_SIZE_SP;
        
        // 根据弹幕模式调整大小
        switch (danmu.getMode()) {
            case Danmu.Mode.TOP:    // 顶部弹幕稍大
            case Danmu.Mode.BOTTOM: // 底部弹幕稍大
                return baseSize + 2;
            case Danmu.Mode.SCROLL: // 滚动弹幕正常大小
            default:
                return baseSize;
        }
    }
    
    /**
     * 🔍 寻找可用的弹幕通道（支持超时清理和碰撞检测）
     */
    private int findAvailableTrack() {
        long currentTime = System.currentTimeMillis();
        
        // 🧹 首先清理超时的通道
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            if (danmuTracks[i] && (currentTime - trackOccupyTime[i]) > CHANNEL_TIMEOUT) {
                Log.w(TAG, "🧹 清理超时通道: " + i + " (占用时间: " + (currentTime - trackOccupyTime[i]) / 1000.0 + "秒)");
                danmuTracks[i] = false;
                trackOccupyTime[i] = 0;
                trackLastDanmu[i] = null;
            }
        }
        
        // 🎨 web弹幕密度控制
        long currentSecondTime = currentTime / 1000;
        if (currentSecondTime != currentSecond) {
            currentSecond = currentSecondTime;
            danmuCountInSecond = 0; // 重置秒内计数
        }
        
        if (danmuCountInSecond >= MAX_DANMU_PER_SECOND) {
            Log.w(TAG, "⚠️ 达到每秒最大弹幕数量限制: " + MAX_DANMU_PER_SECOND);
            return -1;
        }
        
        // 🔍 调试：输出当前所有通道状态
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            String trackState = danmuTracks[i] ? "占用" : "空闲";
            boolean isSafe = isTrackSafeForNewDanmu(i);
            Log.d(TAG, "通道" + i + ": " + trackState + " | 安全=" + isSafe + 
                  " | 占用时间=" + (trackOccupyTime[i] > 0 ? (currentTime - trackOccupyTime[i]) / 1000.0 : 0) + "秒");
        }
        
        // 🔧 简化通道分配策略：优先选择完全空闲的通道
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            if (!danmuTracks[i]) {
                Log.d(TAG, "✅ 找到完全空闲通道: " + i);
                return i;
            }
        }
        
        // 🔧 其次选择安全的通道（即使被占用但已经有足够间距）
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            if (isTrackSafeForNewDanmu(i)) {
                Log.d(TAG, "✅ 找到安全通道: " + i);
                return i;
            }
        }
        
        // 🔧 最后策略：强制使用负载最轻的通道
        int leastBusyTrack = findLeastBusyTrack();
        if (leastBusyTrack != -1) {
            Log.w(TAG, "⚠️ 强制使用负载最轻通道: " + leastBusyTrack);
            return leastBusyTrack;
        }
        
        Log.w(TAG, "❌ 所有通道都被占用，当前活跃弹幕数: " + activeDanmuViews.size());
        return -1; // 没有可用通道
    }
    
    /**
     * 🎯 寻找负载最轻的通道（根据最后使用时间）
     */
    private int findLeastBusyTrack() {
        long currentTime = System.currentTimeMillis();
        int bestTrack = -1;
        long oldestTime = currentTime; // 寻找最久未使用的通道
        
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            if (!danmuTracks[i]) {
                // 完全空闲的通道优先级最高
                return i;
            }
            
            // 🔧 简化：直接选择最久未使用的通道，不再检查安全性
            if (trackOccupyTime[i] < oldestTime) {
                oldestTime = trackOccupyTime[i];
                bestTrack = i;
            }
        }
        
        return bestTrack;
    }
    
    /**
     * 🔄 轮循寻找下一个可用通道
     */
    private int findNextAvailableTrack() {
        // 从上次使用的通道的下一个开始寻找
        int startTrack = (lastUsedTrack + 1) % MAX_DANMU_LINES;
        
        for (int offset = 0; offset < MAX_DANMU_LINES; offset++) {
            int trackIndex = (startTrack + offset) % MAX_DANMU_LINES;
            
            if (!danmuTracks[trackIndex] || isTrackSafeForNewDanmu(trackIndex)) {
                lastUsedTrack = trackIndex; // 更新最后使用的通道
                return trackIndex;
            }
        }
        
        return -1;
    }
    
    /**
     * 🎲 随机选择一个安全通道
     */
    private int findRandomSafeTrack() {
        List<Integer> availableTracks = new ArrayList<>();
        
        // 收集所有可用通道
        for (int i = 0; i < MAX_DANMU_LINES; i++) {
            if (!danmuTracks[i] || isTrackSafeForNewDanmu(i)) {
                availableTracks.add(i);
            }
        }
        
        if (!availableTracks.isEmpty()) {
            // 随机选择一个
            int randomIndex = (int) (Math.random() * availableTracks.size());
            int selectedTrack = availableTracks.get(randomIndex);
            lastUsedTrack = selectedTrack; // 更新最后使用的通道
            return selectedTrack;
        }
        
        return -1;
    }
    
    /**
     * 🎨 检查通道是否可以安全放置新弹幕（碰撞检测）
     */
    private boolean isTrackSafeForNewDanmu(int track) {
        if (track < 0 || track >= MAX_DANMU_LINES) return false;
        
        TextView lastDanmu = trackLastDanmu[track];
        if (lastDanmu == null) return true;
        
        // 🔧 修复碰撞检测逻辑：检查弹幕是否已经离开屏幕右边足够距离
        float lastDanmuX = lastDanmu.getX() + lastDanmu.getTranslationX();
        float lastDanmuRight = lastDanmuX + lastDanmu.getWidth();
        float containerWidth = danmuContainer.getWidth();
        
        // 🎯 新的安全检查逻辑：上一条弹幕的右边界必须离屏幕右边界足够远
        // 新弹幕会从右边200px开始，需要确保与上一条弹幕有足够间距
        float newDanmuStartX = containerWidth - 200; // 新弹幕起始位置
        float requiredDistance = MIN_DANMU_SPACING;
        
        // 🔧 修复：正确的碰撞检测 - 检查上一条弹幕是否已经向左移动足够距离
        // 上一条弹幕的右边界应该小于新弹幕的起始位置减去所需间距
        boolean isSafe = lastDanmuRight < (newDanmuStartX - requiredDistance);
        
        // 🔧 额外安全检查：如果上一条弹幕已经完全移出屏幕左边，也认为是安全的
        if (lastDanmuRight < 0) {
            isSafe = true;
        }
        
        Log.d(TAG, "🎯 通道" + track + "碰撞检测详细: " +
              "lastDanmuRight=" + lastDanmuRight + 
              ", newDanmuStartX=" + newDanmuStartX +
              ", 安全阈值=" + (newDanmuStartX - requiredDistance) +
              ", 需要距离=" + requiredDistance +
              ", 安全=" + isSafe);
        
        return isSafe;
    }
    
    /**
     * 🎭 开始弹幕滚动动画 - 支持web弹幕的动态速度和多种模式
     */
    private void startDanmuAnimation(TextView danmuView, int track, Danmu danmu) {
        if (danmuView == null || danmuContainer == null) {
            Log.e(TAG, "❌ 弹幕视图或容器为null，无法开始动画");
            return;
        }
        
        try {
            // 🎨 计算动态速度 - 根据弹幕长度调整（web弹幕特性）
            int dynamicSpeed = calculateDanmuSpeed(danmu.getText());
            
            // 🎯 根据弹幕模式确定动画类型
            switch (danmu.getMode()) {
                case Danmu.Mode.SCROLL:  // 滚动弹幕
                    startScrollAnimation(danmuView, track, dynamicSpeed);
                    break;
                case Danmu.Mode.TOP:     // 顶部固定弹幕
                    startFixedAnimation(danmuView, track, dynamicSpeed, true);
                    break;
                case Danmu.Mode.BOTTOM:  // 底部固定弹幕
                    startFixedAnimation(danmuView, track, dynamicSpeed, false);
                    break;
                default:
                    startScrollAnimation(danmuView, track, dynamicSpeed); // 默认滚动
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 启动弹幕动画失败: " + danmu.getText(), e);
            // 清理失败的弹幕
            cleanupDanmu(danmuView, track);
        }
    }
    
    /**
     * 🎨 计算弹幕动画速度 - 基于长度的web弹幕特性
     */
    private int calculateDanmuSpeed(String text) {
        if (text == null) return DANMU_SPEED;
        
        int textLength = text.length();
        int dynamicSpeed;
        
        if (textLength <= 10) {
            dynamicSpeed = DANMU_SPEED; // 短弹幕正常速度
        } else if (textLength <= 20) {
            dynamicSpeed = (int) (DANMU_SPEED * 1.3f); // 中等长度稍慢
        } else {
            dynamicSpeed = (int) (DANMU_SPEED * 1.6f); // 长弹幕更慢，确保可读
        }
        
        Log.d(TAG, "🎨 弹幕速度计算: 长度=" + textLength + ", 速度=" + dynamicSpeed + "ms");
        return dynamicSpeed;
    }
    
    /**
     * 🎭 滚动弹幕动画
     */
    private void startScrollAnimation(TextView danmuView, int track, int speed) {
        // 🔧 修复：弹幕动画从当前位置开始，向左滚动
        float startX = 0; // 从当前位置开始（已经通过margin设置了初始位置）
        float endX = -danmuView.getWidth() - danmuContainer.getWidth(); // 完全移出屏幕左边
        
        Log.d(TAG, "🎭 滚动动画参数: startX=" + startX + ", endX=" + endX + 
              ", 弹幕宽度=" + danmuView.getWidth() + ", 容器宽度=" + danmuContainer.getWidth() +
              ", 速度=" + speed + "ms");
        
        // 创建移动动画
        ObjectAnimator animator = ObjectAnimator.ofFloat(danmuView, "translationX", startX, endX);
        animator.setDuration(speed);
        animator.setInterpolator(new LinearInterpolator()); // 🎨 线性插值器确保匀速滚动（web弹幕特性）
        
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(TAG, "🎭 滚动动画结束，清理弹幕 (通道" + track + ")");
                cleanupDanmu(danmuView, track);
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                Log.d(TAG, "🎭 滚动动画取消，清理弹幕 (通道" + track + ")");
                cleanupDanmu(danmuView, track);
            }
        });
        
        animator.start();
        Log.d(TAG, "✅ 滚动动画已启动: " + danmuView.getText() + " (通道" + track + ")");
    }
    
    /**
     * 🎭 固定弹幕动画（顶部/底部）
     */
    private void startFixedAnimation(TextView danmuView, int track, int speed, boolean isTop) {
        // 固定弹幕：显示一段时间后消失，不滚动
        Log.d(TAG, "🎭 固定弹幕动画: " + (isTop ? "顶部" : "底部") + ", 显示时间=" + speed + "ms");
        
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            Log.d(TAG, "🎭 固定弹幕显示时间结束，清理弹幕 (通道" + track + ")");
            cleanupDanmu(danmuView, track);
        }, speed);
    }
    
    /**
     * 🧹 清理弹幕视图
     */
    private void cleanupDanmu(TextView danmuView, int track) {
        try {
            // 从容器中移除
            if (danmuContainer != null && danmuView.getParent() == danmuContainer) {
                danmuContainer.removeView(danmuView);
            }
            
            // 从活跃列表中移除
            activeDanmuViews.remove(danmuView);
            
            // 释放通道并重置时间
            if (track >= 0 && track < MAX_DANMU_LINES) {
                danmuTracks[track] = false;
                trackOccupyTime[track] = 0; // 🎯 重置通道占用时间
                trackLastDanmu[track] = null; // 🎯 清空最后一条弹幕记录
            }
            
            Log.d(TAG, "🧹 弹幕清理完成 (通道" + track + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 清理弹幕失败", e);
        }
    }
    
    /**
     * 📏 dp转px
     */
    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * 🎬 启用/禁用弹幕
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.d(TAG, "🎬 弹幕" + (enabled ? "启用" : "禁用"));
        
        if (!enabled) {
            clearAllDanmu();
        }
    }
    
    /**
     * 🧹 清除所有弹幕
     */
    public void clearAllDanmu() {
        Log.d(TAG, "🧹 清除所有弹幕");
        
        try {
            // 移除所有弹幕视图
            for (TextView danmuView : activeDanmuViews) {
                if (danmuView.getParent() == danmuContainer) {
                    danmuContainer.removeView(danmuView);
                }
            }
            activeDanmuViews.clear();
            
            // 🔧 重置所有通道状态
            for (int i = 0; i < MAX_DANMU_LINES; i++) {
                danmuTracks[i] = false;
                trackOccupyTime[i] = 0; // 重置占用时间
                trackLastDanmu[i] = null; // 清空最后弹幕记录
            }
            
            // 🔧 清空去重集合
            recentDanmuTexts.clear();
            
            Log.d(TAG, "✅ 所有弹幕和通道状态已重置");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 清除弹幕失败", e);
        }
    }
    
    /**
     * 🔄 重置弹幕管理器
     */
    public void reset() {
        Log.d(TAG, "🔄 重置弹幕管理器");
        clearAllDanmu();
        danmuList.clear();
        currentPosition = 0;
    }
    
    /**
     * 📊 获取状态信息
     */
    public String getStatusInfo() {
        return "弹幕: " + danmuList.size() + "条, 活跃: " + activeDanmuViews.size() + "个";
    }
    
    /**
     * 🧪 强制显示测试弹幕（调试用）- 测试所有三行
     */
    public void showTestDanmu() {
        if (danmuContainer == null) {
            Log.e(TAG, "❌ 弹幕容器为null，无法显示测试弹幕");
            return;
        }
        
        Log.d(TAG, "🧪 强制显示测试弹幕 - 测试所有三行");
        
        // 🔧 为每一行创建测试弹幕
        for (int trackNum = 0; trackNum < MAX_DANMU_LINES; trackNum++) {
            final int track = trackNum; // 用于lambda表达式
            
            // 延时显示，确保弹幕不会同时出现
            mainHandler.postDelayed(() -> {
                // 创建测试弹幕
                Danmu testDanmu = new Danmu("🧪第" + (track + 1) + "行测试弹幕🧪", 0, "#FF0000", 0);
                
                TextView testView = createDanmuView(testDanmu);
                if (testView == null) {
                    Log.e(TAG, "❌ 第" + (track + 1) + "行测试弹幕视图创建失败");
                    return;
                }
                
                // 设置弹幕位置
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                );
                
                // 🔧 计算弹幕位置 - 使用整个容器高度
                int containerHeight = danmuContainer.getHeight();
                
                // 🔧 修复：使用整个容器高度来分配弹幕行
                int lineHeight = dpToPx(DANMU_LINE_HEIGHT);
                int topMargin = dpToPx(DANMU_MARGIN_DP) + (track * lineHeight);
                
                // 确保不超出容器边界
                int maxAllowedTopMargin = containerHeight - lineHeight;
                if (topMargin > maxAllowedTopMargin) {
                    topMargin = maxAllowedTopMargin;
                }
                
                // 🔧 弹幕从屏幕内开始显示
                params.setMargins(danmuContainer.getWidth() - 200, topMargin, 0, 0); // 从右边200px开始
                params.gravity = Gravity.TOP | Gravity.START;
                
                Log.d(TAG, "📐 第" + (track + 1) + "行弹幕位置: 左边距=" + (danmuContainer.getWidth() - 200) + 
                      ", 上边距=" + topMargin + ", 容器=" + danmuContainer.getWidth() + "x" + danmuContainer.getHeight() +
                      ", 行高=" + lineHeight + ", 通道=" + track);
                
                testView.setLayoutParams(params);
                
                // 添加到容器
                danmuContainer.addView(testView);
                activeDanmuViews.add(testView);
                
                // 强制占用通道（用于测试）
                danmuTracks[track] = true;
                trackOccupyTime[track] = System.currentTimeMillis();
                trackLastDanmu[track] = testView;
                
                Log.d(TAG, "✅ 第" + (track + 1) + "行测试弹幕已添加，容器子视图数: " + danmuContainer.getChildCount());
                
                // 开始动画
                startDanmuAnimation(testView, track, testDanmu);
                
            }, track * 1000); // 每秒显示一行
        }
    }
    
    /**
     * 🔍 调试方法：验证三行弹幕位置计算
     */
    public void debugDanmuPositions() {
        if (danmuContainer == null) {
            Log.e(TAG, "❌ 弹幕容器为null，无法调试位置");
            return;
        }
        
        int containerHeight = danmuContainer.getHeight();
        int containerWidth = danmuContainer.getWidth();
        int lineHeight = dpToPx(DANMU_LINE_HEIGHT);
        int margin = dpToPx(DANMU_MARGIN_DP);
        
        Log.d(TAG, "🔍 弹幕紧凑布局调试信息:");
        Log.d(TAG, "📐 容器大小: " + containerWidth + "x" + containerHeight);
        Log.d(TAG, "📐 行高: " + DANMU_LINE_HEIGHT + "dp (" + lineHeight + "px), 边距: " + DANMU_MARGIN_DP + "dp (" + margin + "px)");
        
        for (int track = 0; track < MAX_DANMU_LINES; track++) {
            int topMargin = margin + (track * lineHeight);
            int maxAllowedTopMargin = containerHeight - lineHeight;
            
            if (topMargin > maxAllowedTopMargin) {
                topMargin = maxAllowedTopMargin;
            }
            
            int actualSpacing = track > 0 ? lineHeight : 0; // 实际行间距
            
            Log.d(TAG, "📐 通道" + track + " (第" + (track + 1) + "行): " +
                  "上边距=" + topMargin + "px" +
                  ", 下边界=" + (topMargin + lineHeight) + "px" +
                  ", 与上一行间距=" + actualSpacing + "px" +
                  ", 是否超出容器=" + (topMargin + lineHeight > containerHeight));
        }
        
        Log.d(TAG, "📊 总体布局: 第1行到第3行总高度=" + (margin + 2 * lineHeight + lineHeight) + "px");
    }
}
