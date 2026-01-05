package com.mynas.nastv.player;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.ToastUtils;

/**
 * 播放器设置辅助类
 * 负责画面比例、跳过片头等设置
 */
public class PlayerSettingsHelper {
    private static final String TAG = "PlayerSettingsHelper";

    private Context context;
    private SettingsCallback callback;

    public interface SettingsCallback {
        void onDecoderChanged(boolean useSoftware);
        void onAspectRatioChanged(int ratio);
        void onReloadVideoRequested();
    }

    public PlayerSettingsHelper(Context context) {
        this.context = context;
    }

    public void setCallback(SettingsCallback callback) {
        this.callback = callback;
    }

    /**
     * 显示设置对话框
     */
    public void showSettingsDialog() {
        String[] settingsItems = {
                "自动连播: " + (SharedPreferencesManager.isAutoPlayNext() ? "开" : "关"),
                "跳过片头/片尾",
                "画面比例: " + getAspectRatioLabel(SharedPreferencesManager.getAspectRatio())
        };

        new AlertDialog.Builder(context)
                .setTitle("设置")
                .setItems(settingsItems, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            toggleAutoPlayNext();
                            break;
                        case 1:
                            showSkipIntroOutroDialog();
                            break;
                        case 2:
                            showAspectRatioDialog();
                            break;
                    }
                })
                .show();
    }

    private void toggleAutoPlayNext() {
        boolean current = SharedPreferencesManager.isAutoPlayNext();
        SharedPreferencesManager.setAutoPlayNext(!current);
        ToastUtils.show(context, "自动连播: " + (!current ? "开" : "关"));
    }

    private void showSkipIntroOutroDialog() {
        String[] options = {
                "跳过片头: " + formatSkipTime(SharedPreferencesManager.getSkipIntro()),
                "跳过片尾: " + formatSkipTime(SharedPreferencesManager.getSkipOutro())
        };

        new AlertDialog.Builder(context)
                .setTitle("跳过片头/片尾")
                .setItems(options, (dialog, which) -> {
                    showSkipTimeDialog(which == 0);
                })
                .show();
    }

    private void showSkipTimeDialog(boolean isIntro) {
        String[] timeOptions = {"未设置", "30秒", "60秒", "90秒", "120秒", "自定义"};
        int[] timeValues = {0, 30, 60, 90, 120, -1};

        int currentValue = isIntro ? SharedPreferencesManager.getSkipIntro() : SharedPreferencesManager.getSkipOutro();
        int checkedItem = 0;
        for (int i = 0; i < timeValues.length - 1; i++) {
            if (timeValues[i] == currentValue) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(isIntro ? "跳过片头" : "跳过片尾")
                .setSingleChoiceItems(timeOptions, checkedItem, (dialog, which) -> {
                    if (which == 5) {
                        showCustomSkipTimeDialog(isIntro);
                    } else {
                        if (isIntro) {
                            SharedPreferencesManager.setSkipIntro(timeValues[which]);
                        } else {
                            SharedPreferencesManager.setSkipOutro(timeValues[which]);
                        }
                        ToastUtils.show(context, (isIntro ? "跳过片头: " : "跳过片尾: ") + timeOptions[which]);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showCustomSkipTimeDialog(boolean isIntro) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("输入秒数");

        int currentValue = isIntro ? SharedPreferencesManager.getSkipIntro() : SharedPreferencesManager.getSkipOutro();
        if (currentValue > 0) {
            input.setText(String.valueOf(currentValue));
        }

        new AlertDialog.Builder(context)
                .setTitle(isIntro ? "自定义跳过片头时间" : "自定义跳过片尾时间")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int seconds = Integer.parseInt(input.getText().toString());
                        if (isIntro) {
                            SharedPreferencesManager.setSkipIntro(seconds);
                        } else {
                            SharedPreferencesManager.setSkipOutro(seconds);
                        }
                        ToastUtils.show(context, "已设置为 " + seconds + " 秒");
                    } catch (NumberFormatException e) {
                        ToastUtils.show(context, "请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatSkipTime(int seconds) {
        if (seconds <= 0) return "未设置";
        return seconds + "秒";
    }

    private void showAspectRatioDialog() {
        String[] ratioOptions = {"默认", "16:9", "4:3", "填充屏幕"};
        int currentRatio = SharedPreferencesManager.getAspectRatio();

        new AlertDialog.Builder(context)
                .setTitle("画面比例")
                .setSingleChoiceItems(ratioOptions, currentRatio, (dialog, which) -> {
                    SharedPreferencesManager.setAspectRatio(which);
                    if (callback != null) {
                        callback.onAspectRatioChanged(which);
                    }
                    ToastUtils.show(context, "画面比例: " + ratioOptions[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private String getAspectRatioLabel(int ratio) {
        switch (ratio) {
            case 1: return "16:9";
            case 2: return "4:3";
            case 3: return "填充";
            default: return "默认";
        }
    }
}
