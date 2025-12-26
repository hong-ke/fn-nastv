package com.mynas.nastv.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack;

/**
 * 播放器回调处理器
 * 封装 GSYVideoPlayer 的回调逻辑
 */
public class PlayerCallbackHandler implements VideoAllCallBack {
    private static final String TAG = "PlayerCallbackHandler";

    private PlayerEventListener listener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PlayerEventListener {
        void onPlayerPrepared();
        void onPlayerError(String error);
        void onPlayerComplete();
        void onPlayerResume();
        void onPlayerPause();
        void onBlankClicked();
        void onBufferingStart();
        void onBufferingEnd();
    }

    public PlayerCallbackHandler(PlayerEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStartPrepared(String url, Object... objects) {
        Log.d(TAG, "onStartPrepared");
    }

    @Override
    public void onPrepared(String url, Object... objects) {
        Log.d(TAG, "onPrepared");
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayerPrepared());
        }
    }

    @Override
    public void onClickStartError(String url, Object... objects) {
        Log.e(TAG, "onClickStartError");
    }

    @Override
    public void onClickStop(String url, Object... objects) {
        Log.d(TAG, "onClickStop");
    }

    @Override
    public void onClickStopFullscreen(String url, Object... objects) {
        Log.d(TAG, "onClickStopFullscreen");
    }

    @Override
    public void onClickResume(String url, Object... objects) {
        Log.d(TAG, "onClickResume");
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayerResume());
        }
    }

    @Override
    public void onClickResumeFullscreen(String url, Object... objects) {
        Log.d(TAG, "onClickResumeFullscreen");
    }

    @Override
    public void onClickSeekbar(String url, Object... objects) {
        Log.d(TAG, "onClickSeekbar");
    }

    @Override
    public void onClickSeekbarFullscreen(String url, Object... objects) {
        Log.d(TAG, "onClickSeekbarFullscreen");
    }

    @Override
    public void onClickStartThumb(String url, Object... objects) {
        Log.d(TAG, "onClickStartThumb");
    }

    @Override
    public void onClickBlank(String url, Object... objects) {
        Log.d(TAG, "onClickBlank");
        if (listener != null) {
            mainHandler.post(() -> listener.onBlankClicked());
        }
    }

    @Override
    public void onClickBlankFullscreen(String url, Object... objects) {
        Log.d(TAG, "onClickBlankFullscreen");
        if (listener != null) {
            mainHandler.post(() -> listener.onBlankClicked());
        }
    }

    @Override
    public void onAutoComplete(String url, Object... objects) {
        Log.d(TAG, "onAutoComplete");
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayerComplete());
        }
    }

    @Override
    public void onComplete(String url, Object... objects) {
        Log.d(TAG, "onComplete");
    }

    @Override
    public void onEnterFullscreen(String url, Object... objects) {
        Log.d(TAG, "onEnterFullscreen");
    }

    @Override
    public void onQuitFullscreen(String url, Object... objects) {
        Log.d(TAG, "onQuitFullscreen");
    }

    @Override
    public void onQuitSmallWidget(String url, Object... objects) {
        Log.d(TAG, "onQuitSmallWidget");
    }

    @Override
    public void onEnterSmallWidget(String url, Object... objects) {
        Log.d(TAG, "onEnterSmallWidget");
    }

    @Override
    public void onTouchScreenSeekVolume(String url, Object... objects) {
        Log.d(TAG, "onTouchScreenSeekVolume");
    }

    @Override
    public void onTouchScreenSeekPosition(String url, Object... objects) {
        Log.d(TAG, "onTouchScreenSeekPosition");
    }

    @Override
    public void onTouchScreenSeekLight(String url, Object... objects) {
        Log.d(TAG, "onTouchScreenSeekLight");
    }

    @Override
    public void onPlayError(String url, Object... objects) {
        String errorMsg = objects.length > 0 ? objects[0].toString() : "未知错误";
        Log.e(TAG, "onPlayError: " + errorMsg);
        if (listener != null) {
            mainHandler.post(() -> listener.onPlayerError(errorMsg));
        }
    }

    @Override
    public void onClickStartIcon(String url, Object... objects) {
        Log.d(TAG, "onClickStartIcon");
    }
}
