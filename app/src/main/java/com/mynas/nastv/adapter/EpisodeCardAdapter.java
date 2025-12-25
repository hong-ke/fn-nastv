package com.mynas.nastv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mynas.nastv.R;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 📺 横向剧集卡片适配器
 * 用于显示分组后的剧集列表，支持横向滚动
 */
public class EpisodeCardAdapter extends RecyclerView.Adapter<EpisodeCardAdapter.EpisodeViewHolder> {

    private List<EpisodeListResponse.Episode> episodes = new ArrayList<>();
    private OnEpisodeClickListener listener;
    private int currentEpisodeNumber = -1;

    public interface OnEpisodeClickListener {
        void onEpisodeClick(EpisodeListResponse.Episode episode, int position);
    }

    public EpisodeCardAdapter(OnEpisodeClickListener listener) {
        this.listener = listener;
    }

    public void updateEpisodes(List<EpisodeListResponse.Episode> episodeList) {
        this.episodes.clear();
        if (episodeList != null) {
            this.episodes.addAll(episodeList);
        }
        notifyDataSetChanged();
    }

    public void setCurrentEpisode(int episodeNumber) {
        int oldPosition = findPositionByEpisodeNumber(currentEpisodeNumber);
        int newPosition = findPositionByEpisodeNumber(episodeNumber);
        
        this.currentEpisodeNumber = episodeNumber;
        
        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition >= 0 && newPosition != oldPosition) {
            notifyItemChanged(newPosition);
        }
    }

    private int findPositionByEpisodeNumber(int episodeNumber) {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).getEpisodeNumber() == episodeNumber) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode_card, parent, false);
        return new EpisodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        EpisodeListResponse.Episode episode = episodes.get(position);
        holder.bind(episode, episode.getEpisodeNumber() == currentEpisodeNumber);
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private ImageView thumbnailImage;
        private TextView titleText;
        private TextView overviewText;
        private TextView durationText;
        private TextView currentIndicator;
        private TextView resolutionText;

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.episode_thumbnail);
            titleText = itemView.findViewById(R.id.episode_title);
            overviewText = itemView.findViewById(R.id.episode_overview);
            durationText = itemView.findViewById(R.id.episode_duration);
            currentIndicator = itemView.findViewById(R.id.episode_current_indicator);
            resolutionText = itemView.findViewById(R.id.episode_resolution);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEpisodeClick(episodes.get(position), position);
                }
            });
        }

        public void bind(EpisodeListResponse.Episode episode, boolean isCurrent) {
            // 📱 加载缩略图
            String stillPath = episode.getStillPath();
            if (stillPath != null && !stillPath.isEmpty()) {
                String imageUrl = stillPath;
                if (!imageUrl.startsWith("http")) {
                    // stillPath 格式: /2b/10/xxx.webp
                    // 需要拼接成: baseUrl + /v/api/v1/sys/img + stillPath
                    String baseUrl = SharedPreferencesManager.getServerBaseUrl();
                    imageUrl = baseUrl + "/v/api/v1/sys/img" + stillPath + "?w=320";
                }
                Glide.with(thumbnailImage.getContext())
                        .load(imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.color.tv_card_background)
                        .error(R.color.tv_card_background)
                        .centerCrop()
                        .into(thumbnailImage);
            } else {
                thumbnailImage.setImageResource(R.color.tv_card_background);
            }

            // 📝 标题：第X集. 标题
            String title = episode.getTitle();
            String displayTitle = "第" + episode.getEpisodeNumber() + "集";
            if (title != null && !title.isEmpty()) {
                displayTitle += ". " + title;
            }
            titleText.setText(displayTitle);

            // 📝 简介
            String overview = episode.getOverview();
            if (overview != null && !overview.isEmpty()) {
                overviewText.setText(overview);
                overviewText.setVisibility(View.VISIBLE);
            } else {
                overviewText.setVisibility(View.GONE);
            }

            // ⏱️ 时长 (格式化为 mm:ss)
            int runtime = episode.getRuntime();
            if (runtime > 0) {
                int minutes = runtime;
                int seconds = 0;
                durationText.setText(String.format("%d:%02d", minutes, seconds));
                durationText.setVisibility(View.VISIBLE);
            } else {
                durationText.setVisibility(View.GONE);
            }

            // 🎯 当前播放指示器
            if (isCurrent) {
                currentIndicator.setVisibility(View.VISIBLE);
                itemView.setSelected(true);
            } else {
                currentIndicator.setVisibility(View.GONE);
                itemView.setSelected(false);
            }

            // 📺 清晰度标签
            String resolution = episode.getResolution();
            if (resolution != null && !resolution.isEmpty()) {
                resolutionText.setText(resolution);
                resolutionText.setVisibility(View.VISIBLE);
            } else {
                resolutionText.setVisibility(View.GONE);
            }

            // 焦点动画
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
        }
    }
}
