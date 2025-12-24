package com.mynas.nastv.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 📊 FormatUtils - 格式化工具类
 * 提供评分、进度、文件大小、时长等格式化方法
 */
public class FormatUtils {

    /**
     * 格式化评分显示
     * @param rating 评分值 (0-10)
     * @return 格式化后的字符串，如 "7.6"，如果评分为0或负数返回空字符串
     */
    public static String formatRating(double rating) {
        if (rating <= 0) {
            return "";
        }
        return String.format(Locale.US, "%.1f", rating);
    }

    /**
     * 计算播放进度百分比
     * @param currentPosition 当前位置（秒）
     * @param duration 总时长（秒）
     * @return 百分比 (0-100)，如果duration为0返回0
     */
    public static int calculateProgressPercent(long currentPosition, long duration) {
        if (duration <= 0) {
            return 0;
        }
        int percent = (int) ((currentPosition * 100) / duration);
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * 格式化进度文本
     * @param seasonNumber 季数
     * @param episodeNumber 集数
     * @return 格式化后的字符串，如 "第1季·第3集"
     */
    public static String formatProgressText(int seasonNumber, int episodeNumber) {
        if (seasonNumber > 0 && episodeNumber > 0) {
            return "第" + seasonNumber + "季·第" + episodeNumber + "集";
        } else if (episodeNumber > 0) {
            return "第" + episodeNumber + "集";
        } else if (seasonNumber > 0) {
            return "第" + seasonNumber + "季";
        }
        return "";
    }

    /**
     * 格式化文件大小
     * @param bytes 字节数
     * @return 人类可读格式，如 "596.51 MB"
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        if (gb < 1024) {
            return String.format(Locale.US, "%.2f GB", gb);
        }
        double tb = gb / 1024.0;
        return String.format(Locale.US, "%.2f TB", tb);
    }

    /**
     * 格式化时长
     * @param seconds 秒数
     * @return 人类可读格式，如 "46分钟46秒" 或 "1小时30分钟"
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "0秒";
        }
        if (seconds < 60) {
            return seconds + "秒";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) {
            if (remainingSeconds > 0) {
                return minutes + "分钟" + remainingSeconds + "秒";
            }
            return minutes + "分钟";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (remainingMinutes > 0) {
            return hours + "小时" + remainingMinutes + "分钟";
        }
        return hours + "小时";
    }

    /**
     * 格式化时长（简短格式，用于播放器）
     * @param seconds 秒数
     * @return 格式如 "01:30:45" 或 "30:45"
     */
    public static String formatDurationShort(long seconds) {
        if (seconds < 0) {
            return "00:00";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    /**
     * 格式化日期
     * @param timestamp Unix时间戳（秒）
     * @return 本地化日期字符串，如 "2024-12-23"
     */
    public static String formatDate(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        // 如果时间戳是秒级，转换为毫秒
        long millis = timestamp < 10000000000L ? timestamp * 1000 : timestamp;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /**
     * 格式化日期时间
     * @param timestamp Unix时间戳（秒）
     * @return 本地化日期时间字符串，如 "2024-12-23 15:30"
     */
    public static String formatDateTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        // 如果时间戳是秒级，转换为毫秒
        long millis = timestamp < 10000000000L ? timestamp * 1000 : timestamp;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /**
     * 从日期字符串提取年份
     * @param dateStr 日期字符串，如 "2024-12-23"
     * @return 年份字符串，如 "2024"，如果解析失败返回空字符串
     */
    public static String extractYear(String dateStr) {
        if (dateStr == null || dateStr.length() < 4) {
            return "";
        }
        return dateStr.substring(0, 4);
    }

    /**
     * 格式化码率
     * @param bps 比特率（bps）
     * @return 人类可读格式，如 "8.5 Mbps"
     */
    public static String formatBitrate(long bps) {
        if (bps <= 0) {
            return "";
        }
        double kbps = bps / 1000.0;
        if (kbps < 1000) {
            return String.format(Locale.US, "%.1f Kbps", kbps);
        }
        double mbps = kbps / 1000.0;
        return String.format(Locale.US, "%.1f Mbps", mbps);
    }

    /**
     * 格式化采样率
     * @param sampleRate 采样率字符串，如 "48000"
     * @return 人类可读格式，如 "48kHz"
     */
    public static String formatSampleRate(String sampleRate) {
        if (sampleRate == null || sampleRate.isEmpty()) {
            return "";
        }
        try {
            int rate = Integer.parseInt(sampleRate);
            if (rate >= 1000) {
                return (rate / 1000) + "kHz";
            }
            return rate + "Hz";
        } catch (NumberFormatException e) {
            return sampleRate;
        }
    }

    // ==================== 流信息格式化方法 ====================

    /**
     * 格式化视频流信息
     * 格式: "1080P H264 8Mbps·10bit SDR"
     * @param video 视频流对象
     * @return 格式化后的视频信息字符串
     */
    public static String formatVideoInfo(com.mynas.nastv.model.StreamListResponse.VideoStream video) {
        if (video == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        
        // 分辨率
        String resolution = video.getResolution();
        if (resolution != null && !resolution.isEmpty()) {
            sb.append(resolution);
        } else if (video.getHeight() > 0) {
            sb.append(video.getHeight()).append("P");
        }
        
        // 编码
        String codec = video.getCodec();
        if (codec != null && !codec.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(codec.toUpperCase());
        }
        
        // 码率
        long bitrate = video.getBitrate();
        if (bitrate > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(formatBitrate(bitrate));
        }
        
        // 位深
        int bitDepth = video.getBitDepth();
        if (bitDepth > 0) {
            sb.append("·").append(bitDepth).append("bit");
        }
        
        // HDR/SDR
        String colorRange = video.getColorRangeType();
        if (colorRange != null && !colorRange.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(colorRange);
        }
        
        return sb.toString();
    }

    /**
     * 格式化音频流信息
     * 格式: "中文 AAC stereo·48kHz"
     * @param audio 音频流对象
     * @return 格式化后的音频信息字符串
     */
    public static String formatAudioInfo(com.mynas.nastv.model.StreamListResponse.AudioStream audio) {
        if (audio == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        
        // 语言
        String language = audio.getLanguage();
        if (language != null && !language.isEmpty()) {
            sb.append(formatLanguage(language));
        }
        
        // 编码
        String codec = audio.getCodec();
        if (codec != null && !codec.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(codec.toUpperCase());
        }
        
        // 声道
        String channelLayout = audio.getChannelLayout();
        if (channelLayout != null && !channelLayout.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(channelLayout);
        }
        
        // 采样率
        String sampleRate = audio.getSampleRate();
        if (sampleRate != null && !sampleRate.isEmpty()) {
            sb.append("·").append(formatSampleRate(sampleRate));
        }
        
        return sb.toString();
    }

    /**
     * 格式化字幕流信息
     * 格式: "中文 SRT 外挂"
     * @param subtitle 字幕流对象
     * @return 格式化后的字幕信息字符串
     */
    public static String formatSubtitleInfo(com.mynas.nastv.model.StreamListResponse.SubtitleStream subtitle) {
        if (subtitle == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        
        // 语言
        String language = subtitle.getLanguage();
        if (language != null && !language.isEmpty()) {
            sb.append(formatLanguage(language));
        }
        
        // 格式
        String format = subtitle.getFormat();
        if (format != null && !format.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(format.toUpperCase());
        } else {
            // 尝试从 codecName 获取格式
            String codecName = subtitle.getCodecName();
            if (codecName != null && !codecName.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(codecName.toUpperCase());
            }
        }
        
        // 是否外挂
        if (subtitle.isExternal()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("外挂");
        }
        
        return sb.toString();
    }

    /**
     * 格式化语言代码为中文名称
     * @param languageCode 语言代码，如 "chi", "eng", "jpn"
     * @return 中文语言名称，如 "中文", "英语", "日语"
     */
    public static String formatLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return "";
        }
        String code = languageCode.toLowerCase();
        switch (code) {
            case "chi":
            case "zho":
            case "zh":
            case "chinese":
                return "中文";
            case "eng":
            case "en":
            case "english":
                return "英语";
            case "jpn":
            case "ja":
            case "japanese":
                return "日语";
            case "kor":
            case "ko":
            case "korean":
                return "韩语";
            case "fra":
            case "fre":
            case "fr":
            case "french":
                return "法语";
            case "deu":
            case "ger":
            case "de":
            case "german":
                return "德语";
            case "spa":
            case "es":
            case "spanish":
                return "西班牙语";
            case "ita":
            case "it":
            case "italian":
                return "意大利语";
            case "por":
            case "pt":
            case "portuguese":
                return "葡萄牙语";
            case "rus":
            case "ru":
            case "russian":
                return "俄语";
            case "ara":
            case "ar":
            case "arabic":
                return "阿拉伯语";
            case "tha":
            case "th":
            case "thai":
                return "泰语";
            case "vie":
            case "vi":
            case "vietnamese":
                return "越南语";
            case "und":
            case "unknown":
                return "未知";
            default:
                return languageCode;
        }
    }
}
