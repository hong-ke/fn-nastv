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
import com.mynas.nastv.utils.FormatUtils;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强版剧集列表适配器
 * 显示剧集缩略图、标题、简介、时长，支持当前剧集高亮
 */
public class EpisodeEnhancedAdapter extends RecyclerView.Adapter<EpisodeEnhancedAdapter.EpisodeViewHolder> {

    private List<EpisodeListResponse.Episode> episodes = new ArrayList<>();
    private OnEpisodeClickListener listener;
    private int currentEpisodeNumber = -1; // 当前播放的剧集号

    public interface OnEpisodeClickListener {
        void onEpisodeClick(EpisodeListResponse.Episode episode, int position);
    }

    public EpisodeEnhancedAdapter(OnEpisodeClickListener listener) {
        this.listener = listener;
    }

    public void updateEpisodes(List<EpisodeListResponse.Episode> episodeList) {
        this.episodes.clear();
        if (episodeList != null) {
            this.episodes.addAll(episodeList);
        }
        notifyDataSetChanged();
    }

    /**
     * 设置当前播放的剧集号（用于高亮显示）
     */
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
                .inflate(R.layout.item_episode_enhanced, parent, false);
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

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.episode_thumbnail);
            titleText = itemView.findViewById(R.id.episode_title);
            overviewText = itemView.findViewById(R.id.episode_overview);
            durationText = itemView.findViewById(R.id.episode_duration);
            currentIndicator = itemView.findViewById(R.id.episode_current_indicator);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEpisodeClick(episodes.get(position), position);
                }
            });
        }

        public void bind(EpisodeListResponse.Episode episode, boolean isCurrent) {
            // 加载缩略图
            String stillPath = episode.getStillPath();
            android.util.Log.d("EpisodeAdapter", "Episode " + episode.getEpisodeNumber() + " stillPath: " + stillPath);
            if (stillPath != null && !stillPath.isEmpty()) {
                String imageUrl = stillPath;
                if (!imageUrl.startsWith("http")) {
                    imageUrl = SharedPreferencesManager.getServerBaseUrl() + "/v/api/v1/sys/img" + stillPath + "?w=320";
                }
                android.util.Log.d("EpisodeAdapter", "Loading image: " + imageUrl);
                Glide.with(thumbnailImage.getContext())
                        .load(imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.color.tv_card_background)
                        .error(R.color.tv_card_background)
                        .centerCrop()
                        .into(thumbnailImage);
            } else {
                android.util.Log.d("EpisodeAdapter", "No stillPath for episode " + episode.getEpisodeNumber());
                thumbnailImage.setImageResource(R.color.tv_card_background);
            }

            // 标题：第X集. 标题
            String title = episode.getTitle();
            String displayTitle = "第" + episode.getEpisodeNumber() + "集";
            if (title != null && !title.isEmpty()) {
                displayTitle += ". " + title;
            }
            titleText.setText(displayTitle);

            // 简介
            String overview = episode.getOverview();
            if (overview != null && !overview.isEmpty()) {
                overviewText.setText(overview);
                overviewText.setVisibility(View.VISIBLE);
            } else {
                overviewText.setVisibility(View.GONE);
            }

            // 时长
            int runtime = episode.getRuntime();
            if (runtime > 0) {
                // runtime 是分钟，转换为秒后格式化
                String durationStr = FormatUtils.formatDuration(runtime * 60L);
                durationText.setText(durationStr);
                durationText.setVisibility(View.VISIBLE);
            } else {
                durationText.setVisibility(View.GONE);
            }

            // 当前播放指示器
            if (isCurrent) {
                currentIndicator.setVisibility(View.VISIBLE);
                itemView.setSelected(true);
            } else {
                currentIndicator.setVisibility(View.GONE);
                itemView.setSelected(false);
            }

            // 焦点动画
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
        }
    }
}
