package com.mynas.nastv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mynas.nastv.R;
import com.mynas.nastv.model.MediaItem;
import com.mynas.nastv.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 继续观看适配器
 * 显示用户最近观看的内容
 */
public class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.ContinueWatchingViewHolder> {
    
    private List<MediaItem> continueWatchingItems = new ArrayList<>();
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(MediaItem item, int position);
    }
    
    public ContinueWatchingAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void updateItems(List<MediaItem> items) {
        this.continueWatchingItems.clear();
        this.continueWatchingItems.addAll(items);
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ContinueWatchingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_continue_watching, parent, false);
        return new ContinueWatchingViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContinueWatchingViewHolder holder, int position) {
        MediaItem item = continueWatchingItems.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return continueWatchingItems.size();
    }
    
    class ContinueWatchingViewHolder extends RecyclerView.ViewHolder {
        private ImageView posterImage;
        private TextView titleText;
        private TextView subtitleText;
        private TextView progressText;
        private TextView episodeText;
        private ProgressBar progressBar;
        
        public ContinueWatchingViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.continue_poster);
            titleText = itemView.findViewById(R.id.continue_title);
            subtitleText = itemView.findViewById(R.id.continue_subtitle);
            progressText = itemView.findViewById(R.id.continue_progress);
            episodeText = itemView.findViewById(R.id.continue_episode_text);
            progressBar = itemView.findViewById(R.id.continue_progress_bar);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(continueWatchingItems.get(position), position);
                }
            });
        }
        
        public void bind(MediaItem item) {
            // 加载海报图片
            String posterUrl = item.getPosterUrl();
            android.util.Log.d("ContinueWatching", "[调试] 加载海报: " + item.getTitle() + " -> " + posterUrl);
            
            if (posterUrl != null && !posterUrl.isEmpty()) {
                Glide.with(posterImage.getContext())
                        .asBitmap()
                        .load(posterUrl)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.color.tv_card_background)
                        .error(R.color.tv_card_background)
                        .centerCrop()
                        .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.Bitmap>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                                    com.bumptech.glide.request.target.Target<android.graphics.Bitmap> target, 
                                    boolean isFirstResource) {
                                android.util.Log.e("ContinueWatching", "[调试] 海报加载失败: " + posterUrl);
                                if (e != null) {
                                    android.util.Log.e("ContinueWatching", "[调试] 详细错误: " + e.getCause());
                                }
                                return false;
                            }
                            @Override
                            public boolean onResourceReady(android.graphics.Bitmap resource, Object model, 
                                    com.bumptech.glide.request.target.Target<android.graphics.Bitmap> target, 
                                    com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                android.util.Log.d("ContinueWatching", "[调试] 海报加载成功: " + posterUrl);
                                return false;
                            }
                        })
                        .into(posterImage);
            } else {
                android.util.Log.w("ContinueWatching", "[调试] 海报URL为空: " + item.getTitle());
                posterImage.setImageResource(R.color.tv_card_background);
            }
            
            titleText.setText(item.getTitle());
            
            // 类型显示：Episode 类型不显示，Movie 显示
            String type = item.getType();
            String subtitle = item.getSubtitle();
            if ("Episode".equalsIgnoreCase(type)) {
                // Episode 类型不显示类型标签
                subtitleText.setVisibility(View.GONE);
            } else if (subtitle != null && !subtitle.isEmpty()) {
                subtitleText.setText(subtitle);
                subtitleText.setVisibility(View.VISIBLE);
            } else if (type != null && !type.isEmpty()) {
                subtitleText.setText(type);
                subtitleText.setVisibility(View.VISIBLE);
            } else {
                subtitleText.setVisibility(View.GONE);
            }
            
            // 显示剧集进度文本（第X季·第X集）
            int seasonNum = item.getSeasonNumber();
            int episodeNum = item.getEpisodeNumber();
            if (seasonNum > 0 || episodeNum > 0) {
                String episodeProgress = FormatUtils.formatProgressText(seasonNum, episodeNum);
                if (!episodeProgress.isEmpty()) {
                    episodeText.setText(episodeProgress);
                    episodeText.setVisibility(View.VISIBLE);
                } else {
                    episodeText.setVisibility(View.GONE);
                }
            } else {
                episodeText.setVisibility(View.GONE);
            }
            
            // 显示进度条
            long watchedTs = item.getWatchedTs();
            long totalDuration = item.getTotalDuration();
            if (watchedTs > 0 && totalDuration > 0) {
                int progressPercent = FormatUtils.calculateProgressPercent(watchedTs, totalDuration);
                progressBar.setProgress(progressPercent);
                progressBar.setVisibility(View.VISIBLE);
                
                // 隐藏旧的进度文本
                progressText.setVisibility(View.GONE);
            } else {
                // 使用旧的进度百分比
                float progress = item.getWatchedProgress();
                if (progress > 0 && progress < 100) {
                    progressBar.setProgress((int) progress);
                    progressBar.setVisibility(View.VISIBLE);
                    progressText.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.GONE);
                    progressText.setVisibility(View.GONE);
                }
            }
        }
    }
}
