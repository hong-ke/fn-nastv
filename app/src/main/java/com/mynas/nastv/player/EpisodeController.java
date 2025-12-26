package com.mynas.nastv.player;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;

import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PlayStartInfo;
import com.mynas.nastv.utils.ToastUtils;

import java.util.List;

/**
 * 剧集控制器
 * 负责剧集列表管理、选集、下一集等功能
 */
public class EpisodeController {
    private static final String TAG = "EpisodeController";

    private Context context;
    private MediaManager mediaManager;
    private List<EpisodeListResponse.Episode> episodeList;
    private int currentEpisodeNumber = 0;
    private String seasonGuid;
    private EpisodeCallback callback;

    public interface EpisodeCallback {
        void onEpisodeLoaded(PlayStartInfo playInfo, EpisodeListResponse.Episode episode);
        void onEpisodeLoadError(String error);
        void onEpisodeListLoaded(List<EpisodeListResponse.Episode> episodes);
        void onNoMoreEpisodes();
        void onLastEpisodeFinished();
    }

    public EpisodeController(Context context) {
        this.context = context;
        this.mediaManager = new MediaManager(context);
    }

    public void setCallback(EpisodeCallback callback) {
        this.callback = callback;
    }

    public void setSeasonGuid(String seasonGuid) {
        this.seasonGuid = seasonGuid;
    }

    public void setCurrentEpisodeNumber(int episodeNumber) {
        this.currentEpisodeNumber = episodeNumber;
    }

    public int getCurrentEpisodeNumber() {
        return currentEpisodeNumber;
    }

    public List<EpisodeListResponse.Episode> getEpisodeList() {
        return episodeList;
    }

    public boolean hasEpisodeList() {
        return episodeList != null && !episodeList.isEmpty();
    }

    /**
     * 加载剧集列表
     */
    public void loadEpisodeList() {
        if (seasonGuid == null || seasonGuid.isEmpty()) {
            Log.d(TAG, "No season guid, skip loading episode list");
            return;
        }

        mediaManager.getEpisodeList(seasonGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodes) {
                episodeList = episodes;
                Log.d(TAG, "Loaded " + episodes.size() + " episodes");
                if (callback != null) {
                    callback.onEpisodeListLoaded(episodes);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load episode list: " + error);
            }
        });
    }

    /**
     * 显示选集菜单
     */
    public void showEpisodeMenu() {
        if (episodeList == null || episodeList.isEmpty()) {
            ToastUtils.show(context, "暂无剧集列表");
            return;
        }

        String[] episodeLabels = new String[episodeList.size()];
        int currentIndex = -1;

        for (int i = 0; i < episodeList.size(); i++) {
            EpisodeListResponse.Episode ep = episodeList.get(i);
            String title = ep.getTitle();
            if (title != null && !title.isEmpty()) {
                episodeLabels[i] = "第" + ep.getEpisodeNumber() + "集 " + title;
            } else {
                episodeLabels[i] = "第" + ep.getEpisodeNumber() + "集";
            }
            if (ep.getEpisodeNumber() == currentEpisodeNumber) {
                currentIndex = i;
            }
        }

        final int checkedItem = currentIndex;

        new AlertDialog.Builder(context)
                .setTitle("选集")
                .setSingleChoiceItems(episodeLabels, checkedItem, (dialog, which) -> {
                    EpisodeListResponse.Episode selectedEp = episodeList.get(which);
                    if (selectedEp.getEpisodeNumber() != currentEpisodeNumber) {
                        playEpisode(selectedEp);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * 播放指定剧集
     */
    public void playEpisode(EpisodeListResponse.Episode episode) {
        Log.d(TAG, "playEpisode: " + episode.getEpisodeNumber());
        ToastUtils.show(context, "正在加载第" + episode.getEpisodeNumber() + "集...");

        mediaManager.startPlayWithInfo(episode.getGuid(), new MediaManager.MediaCallback<PlayStartInfo>() {
            @Override
            public void onSuccess(PlayStartInfo playInfo) {
                currentEpisodeNumber = episode.getEpisodeNumber();
                if (callback != null) {
                    callback.onEpisodeLoaded(playInfo, episode);
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onEpisodeLoadError(error);
                }
            }
        });
    }

    /**
     * 播放下一集
     */
    public void playNextEpisode() {
        if (episodeList == null || episodeList.isEmpty()) {
            if (callback != null) {
                callback.onNoMoreEpisodes();
            }
            return;
        }

        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == currentEpisodeNumber) {
                if (i + 1 < episodeList.size()) {
                    playEpisode(episodeList.get(i + 1));
                } else {
                    if (callback != null) {
                        callback.onNoMoreEpisodes();
                    }
                }
                return;
            }
        }

        if (callback != null) {
            callback.onNoMoreEpisodes();
        }
    }

    /**
     * 自动播放下一集（播放结束时调用）
     */
    public void playNextEpisodeAuto() {
        if (episodeList == null || episodeList.isEmpty()) {
            if (callback != null) {
                callback.onLastEpisodeFinished();
            }
            return;
        }

        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == currentEpisodeNumber) {
                if (i + 1 < episodeList.size()) {
                    ToastUtils.show(context, "自动播放下一集...");
                    playEpisode(episodeList.get(i + 1));
                } else {
                    ToastUtils.show(context, "已播放完最后一集");
                    if (callback != null) {
                        callback.onLastEpisodeFinished();
                    }
                }
                return;
            }
        }

        if (callback != null) {
            callback.onLastEpisodeFinished();
        }
    }

    /**
     * 检查是否有下一集
     */
    public boolean hasNextEpisode() {
        if (episodeList == null || episodeList.isEmpty()) {
            return false;
        }
        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == currentEpisodeNumber) {
                return i + 1 < episodeList.size();
            }
        }
        return false;
    }
}
