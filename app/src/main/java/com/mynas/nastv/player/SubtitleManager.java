package com.mynas.nastv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 字幕管理器
 * 负责字幕的加载、解析和显示
 */
public class SubtitleManager {
    private static final String TAG = "SubtitleManager";

    private Context context;
    private File cacheDir;
    private TextView subtitleTextView;
    private List<StreamListResponse.SubtitleStream> subtitleStreams;
    private int currentSubtitleIndex = -1;
    private List<SubtitleEntry> currentSubtitles;
    private Handler subtitleHandler = new Handler(Looper.getMainLooper());
    private Runnable subtitleRunnable;
    private PositionProvider positionProvider;
    private SubtitleCallback callback;

    public interface PositionProvider {
        long getCurrentPosition();
    }

    public interface SubtitleCallback {
        void onSubtitleLoaded(String title);
        void onSubtitleError(String error);
        void onInternalSubtitleDetected();
    }

    public static class SubtitleEntry {
        public long startTime;
        public long endTime;
        public String text;

        public SubtitleEntry(long start, long end, String text) {
            this.startTime = start;
            this.endTime = end;
            this.text = text;
        }
    }

    public SubtitleManager(Context context, TextView subtitleTextView) {
        this.context = context;
        this.subtitleTextView = subtitleTextView;
        this.cacheDir = context.getCacheDir();
    }

    public void setPositionProvider(PositionProvider provider) {
        this.positionProvider = provider;
    }

    public void setCallback(SubtitleCallback callback) {
        this.callback = callback;
    }

    public List<StreamListResponse.SubtitleStream> getSubtitleStreams() {
        return subtitleStreams;
    }

    public void setSubtitleStreams(List<StreamListResponse.SubtitleStream> streams) {
        this.subtitleStreams = streams;
    }

    public int getCurrentSubtitleIndex() {
        return currentSubtitleIndex;
    }

    public void setCurrentSubtitleIndex(int index) {
        this.currentSubtitleIndex = index;
    }

    /**
     * 重置字幕状态
     */
    public void reset() {
        stopSubtitleSync();
        currentSubtitles = null;
        subtitleStreams = null;
        currentSubtitleIndex = -1;
    }

    /**
     * 加载字幕列表
     */
    public void loadSubtitleList(String itemGuid) {
        if (itemGuid == null || itemGuid.isEmpty()) {
            Log.e(TAG, "No item guid for subtitle loading");
            return;
        }

        Log.d(TAG, "Loading subtitle list for item: " + itemGuid);

        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = SignatureUtils.generateSignature(
                        "GET", "/v/api/v1/stream/list/" + itemGuid, "", null);

                retrofit2.Response<StreamListResponse> response =
                        ApiClient.getApiService()
                                .getStreamList(token, signature, itemGuid)
                                .execute();

                if (response.isSuccessful() && response.body() != null) {
                    StreamListResponse.StreamData data = response.body().getData();
                    if (data != null && data.getSubtitleStreams() != null) {
                        subtitleStreams = data.getSubtitleStreams();
                        Log.d(TAG, "Found " + subtitleStreams.size() + " subtitle streams");

                        // 查找字幕
                        int firstExternalIndex = -1;
                        int firstInternalIndex = -1;

                        for (int i = 0; i < subtitleStreams.size(); i++) {
                            StreamListResponse.SubtitleStream sub = subtitleStreams.get(i);
                            if (sub.isExternal() && firstExternalIndex == -1) {
                                firstExternalIndex = i;
                            }
                            if (!sub.isExternal() && firstInternalIndex == -1) {
                                firstInternalIndex = i;
                            }
                        }

                        // 优先使用外挂字幕
                        if (firstExternalIndex >= 0) {
                            final int index = firstExternalIndex;
                            new Handler(Looper.getMainLooper()).post(() -> loadSubtitle(index));
                        } else if (firstInternalIndex >= 0) {
                            // 只有内嵌字幕
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onInternalSubtitleDetected());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading subtitle list", e);
            }
        }).start();
    }

    /**
     * 加载指定字幕
     */
    public void loadSubtitle(int index) {
        if (subtitleStreams == null || index < 0 || index >= subtitleStreams.size()) {
            return;
        }

        StreamListResponse.SubtitleStream subtitle = subtitleStreams.get(index);
        String subtitleGuid = subtitle.getGuid();
        String format = normalizeFormat(subtitle.getFormat(), subtitle.getCodecName());

        Log.d(TAG, "Loading subtitle: " + subtitle.getTitle() + " format=" + format);

        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = SignatureUtils.generateSignature(
                        "GET", "/v/api/v1/subtitle/dl/" + subtitleGuid, "", null);

                retrofit2.Response<okhttp3.ResponseBody> response =
                        ApiClient.getApiService()
                                .downloadSubtitle(token, signature, subtitleGuid)
                                .execute();

                if (response.isSuccessful() && response.body() != null) {
                    byte[] subtitleBytes = response.body().bytes();
                    File subtitleFile = new File(context.getCacheDir(), "subtitle_" + subtitleGuid + "." + format);
                    FileOutputStream fos = new FileOutputStream(subtitleFile);
                    fos.write(subtitleBytes);
                    fos.close();

                    final int subtitleIndex = index;
                    final String finalFormat = format;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        parseAndApplySubtitle(subtitleFile, subtitle, finalFormat, subtitleIndex));
                } else {
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> 
                            callback.onSubtitleError("下载失败: " + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading subtitle", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSubtitleError("下载错误: " + e.getMessage()));
                }
            }
        }).start();
    }

    private void parseAndApplySubtitle(File file, StreamListResponse.SubtitleStream subtitle, 
                                       String format, int index) {
        try {
            String content = readFileContent(file);

            if ("srt".equals(format)) {
                currentSubtitles = parseSrtSubtitle(content);
            } else if ("ass".equals(format) || "ssa".equals(format)) {
                currentSubtitles = parseAssSubtitle(content);
            } else if ("vtt".equals(format)) {
                currentSubtitles = parseVttSubtitle(content);
            } else {
                currentSubtitles = parseSrtSubtitle(content);
            }

            if (currentSubtitles != null && !currentSubtitles.isEmpty()) {
                Log.d(TAG, "Parsed " + currentSubtitles.size() + " subtitle entries");
                currentSubtitleIndex = index;
                startSubtitleSync();
                if (callback != null) {
                    callback.onSubtitleLoaded(subtitle.getTitle());
                }
            } else {
                if (callback != null) {
                    callback.onSubtitleError("字幕解析失败");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing subtitle", e);
            if (callback != null) {
                callback.onSubtitleError("解析错误: " + e.getMessage());
            }
        }
    }

    private String normalizeFormat(String format, String codecName) {
        if (format == null || format.isEmpty()) {
            format = codecName;
        }
        if (format == null || format.isEmpty()) {
            return "srt";
        }
        switch (format.toLowerCase()) {
            case "subrip": return "srt";
            case "ass":
            case "ssa": return "ass";
            case "webvtt":
            case "vtt": return "vtt";
            default: return format.toLowerCase();
        }
    }

    private String readFileContent(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data, "UTF-8");
    }

    private List<SubtitleEntry> parseSrtSubtitle(String content) {
        List<SubtitleEntry> entries = new ArrayList<>();
        String[] blocks = content.split("\n\n|\r\n\r\n");
        for (String block : blocks) {
            String[] lines = block.trim().split("\n|\r\n");
            if (lines.length >= 3) {
                String timeLine = lines[1];
                if (timeLine.contains("-->")) {
                    String[] times = timeLine.split("-->");
                    if (times.length == 2) {
                        long startTime = parseSrtTime(times[0].trim());
                        long endTime = parseSrtTime(times[1].trim());
                        StringBuilder text = new StringBuilder();
                        for (int i = 2; i < lines.length; i++) {
                            if (text.length() > 0) text.append("\n");
                            text.append(lines[i].trim());
                        }
                        if (startTime >= 0 && endTime > startTime && text.length() > 0) {
                            entries.add(new SubtitleEntry(startTime, endTime, text.toString()));
                        }
                    }
                }
            }
        }
        return entries;
    }

    private long parseSrtTime(String time) {
        try {
            time = time.replace(",", ".");
            String[] parts = time.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                String[] secParts = parts[2].split("\\.");
                int seconds = Integer.parseInt(secParts[0]);
                int millis = secParts.length > 1 ? Integer.parseInt(secParts[1]) : 0;
                return hours * 3600000L + minutes * 60000L + seconds * 1000L + millis;
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse time failed: " + time);
        }
        return -1;
    }

    private List<SubtitleEntry> parseAssSubtitle(String content) {
        List<SubtitleEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n|\r\n");
        for (String line : lines) {
            if (line.startsWith("Dialogue:")) {
                String[] parts = line.substring(9).split(",", 10);
                if (parts.length >= 10) {
                    long startTime = parseAssTime(parts[1].trim());
                    long endTime = parseAssTime(parts[2].trim());
                    String text = parts[9].trim();
                    text = text.replaceAll("\\{[^}]*\\}", "");
                    text = text.replace("\\N", "\n").replace("\\n", "\n");
                    if (startTime >= 0 && endTime > startTime && !text.isEmpty()) {
                        entries.add(new SubtitleEntry(startTime, endTime, text));
                    }
                }
            }
        }
        return entries;
    }

    private long parseAssTime(String time) {
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                String[] secParts = parts[2].split("\\.");
                int seconds = Integer.parseInt(secParts[0]);
                int centis = secParts.length > 1 ? Integer.parseInt(secParts[1]) : 0;
                return hours * 3600000L + minutes * 60000L + seconds * 1000L + centis * 10L;
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse ASS time failed: " + time);
        }
        return -1;
    }

    private List<SubtitleEntry> parseVttSubtitle(String content) {
        return parseSrtSubtitle(content);
    }

    private void startSubtitleSync() {
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
        subtitleRunnable = new Runnable() {
            @Override
            public void run() {
                updateSubtitleDisplay();
                subtitleHandler.postDelayed(this, 100);
            }
        };
        subtitleHandler.post(subtitleRunnable);
    }

    public void stopSubtitleSync() {
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
            subtitleRunnable = null;
        }
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(View.GONE);
        }
    }

    private void updateSubtitleDisplay() {
        if (currentSubtitles == null || currentSubtitles.isEmpty() || 
            positionProvider == null || subtitleTextView == null) {
            return;
        }

        long currentPosition = positionProvider.getCurrentPosition();
        String currentText = null;
        for (SubtitleEntry entry : currentSubtitles) {
            if (currentPosition >= entry.startTime && currentPosition <= entry.endTime) {
                currentText = entry.text;
                break;
            }
        }

        if (currentText != null && !currentText.isEmpty()) {
            subtitleTextView.setText(currentText);
            subtitleTextView.setVisibility(View.VISIBLE);
        } else {
            subtitleTextView.setVisibility(View.GONE);
        }
    }

    public void disableSubtitle() {
        currentSubtitleIndex = -1;
        stopSubtitleSync();
    }

    public void release() {
        stopSubtitleSync();
        currentSubtitles = null;
        subtitleStreams = null;
    }
}
