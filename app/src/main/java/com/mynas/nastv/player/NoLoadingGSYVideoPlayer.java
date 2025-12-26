package com.mynas.nastv.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.mynas.nastv.R;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

/**
 * 自定义 GSYVideoPlayer，禁用内置 loading 显示
 * 使用自定义布局，loading 视图尺寸为 0
 */
public class NoLoadingGSYVideoPlayer extends StandardGSYVideoPlayer {

    public NoLoadingGSYVideoPlayer(Context context) {
        super(context);
    }

    public NoLoadingGSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoLoadingGSYVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    /**
     * 使用自定义布局，loading 视图尺寸为 0
     */
    @Override
    public int getLayoutId() {
        return R.layout.custom_video_layout_standard;
    }

    /**
     * 覆盖：正常状态 UI
     */
    @Override
    protected void changeUiToNormal() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：准备中状态 UI - 不显示 loading
     */
    @Override
    protected void changeUiToPreparingShow() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：缓冲中状态 UI - 不显示 loading
     */
    @Override
    protected void changeUiToPlayingBufferingShow() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：播放中状态 UI
     */
    @Override
    protected void changeUiToPlayingShow() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：暂停状态 UI
     */
    @Override
    protected void changeUiToPauseShow() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：播放完成状态 UI
     */
    @Override
    protected void changeUiToCompleteShow() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：错误状态 UI
     */
    @Override
    protected void changeUiToError() {
        setViewShowState(mTopContainer, INVISIBLE);
        setViewShowState(mBottomContainer, INVISIBLE);
        setViewShowState(mStartButton, INVISIBLE);
        setViewShowState(mLoadingProgressBar, GONE);
        setViewShowState(mThumbImageViewLayout, INVISIBLE);
        setViewShowState(mLockScreen, GONE);
    }

    /**
     * 覆盖：设置 loading 可见性 - 始终隐藏
     */
    @Override
    protected void setViewShowState(View view, int visibility) {
        if (view == mLoadingProgressBar || view == mStartButton) {
            // 始终隐藏 loading 和 start 按钮
            if (view != null) {
                view.setVisibility(GONE);
            }
            return;
        }
        super.setViewShowState(view, visibility);
    }
}
