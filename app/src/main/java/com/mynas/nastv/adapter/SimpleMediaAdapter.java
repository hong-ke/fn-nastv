package com.mynas.nastv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.mynas.nastv.R;
import com.mynas.nastv.model.MediaItem;

import java.util.List;

/**
 * 📱 简化媒体适配器
 * 用于VideoListActivity显示媒体项目列表
 * 避免复杂的图片加载和复杂布局，专注基础功能
 */
public class SimpleMediaAdapter extends RecyclerView.Adapter<SimpleMediaAdapter.MediaViewHolder> {
    private static final String TAG = "SimpleMediaAdapter";
    
    private Context context;
    private List<MediaItem> mediaList;
    private OnItemClickListener onItemClickListener;
    
    // 📱 点击事件接口
    public interface OnItemClickListener {
        void onItemClick(MediaItem mediaItem, int position);
    }
    
    // 🔧 构造函数
    public SimpleMediaAdapter(Context context, List<MediaItem> mediaList) {
        this.context = context;
        this.mediaList = mediaList;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
    
    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_simple_media, parent, false);
        return new MediaViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem mediaItem = mediaList.get(position);
        holder.bind(mediaItem);
    }
    
    @Override
    public int getItemCount() {
        return mediaList != null ? mediaList.size() : 0;
    }
    
    /**
     * 🔄 更新数据
     */
    public void updateData(List<MediaItem> newMediaList) {
        this.mediaList = newMediaList;
        notifyDataSetChanged();
    }
    
    /**
     * 📱 媒体项目ViewHolder
     */
    public class MediaViewHolder extends RecyclerView.ViewHolder {
        private CardView cardView;
        private TextView titleText;
        private TextView subtitleText;
        private TextView ratingText;
        private TextView typeText;
        private TextView progressText;
        
        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            
            cardView = (CardView) itemView;
            titleText = itemView.findViewById(R.id.media_title);
            subtitleText = itemView.findViewById(R.id.media_subtitle);
            ratingText = itemView.findViewById(R.id.media_rating);
            typeText = itemView.findViewById(R.id.media_type);
            progressText = itemView.findViewById(R.id.media_progress);
            
            // 🎯 设置点击事件
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && onItemClickListener != null) {
                        onItemClickListener.onItemClick(mediaList.get(position), position);
                    }
                }
            });
            
            // 🎯 设置焦点事件 (Android TV)
            cardView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        // 获得焦点时放大
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                    } else {
                        // 失去焦点时恢复
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    }
                }
            });
        }
        
        /**
         * 🔗 绑定数据
         */
        public void bind(MediaItem mediaItem) {
            // 🏷️ 标题
            titleText.setText(mediaItem.getTitle());
            
            // 📝 副标题
            if (mediaItem.getSubtitle() != null && !mediaItem.getSubtitle().isEmpty()) {
                subtitleText.setText(mediaItem.getSubtitle());
                subtitleText.setVisibility(View.VISIBLE);
            } else {
                subtitleText.setVisibility(View.GONE);
            }
            
            // ⭐ 评分
            if (mediaItem.getRating() > 0) {
                ratingText.setText(String.format("⭐ %.1f", mediaItem.getRating()));
                ratingText.setVisibility(View.VISIBLE);
            } else {
                ratingText.setVisibility(View.GONE);
            }
            
            // 🎬 类型标签
            if (mediaItem.getType() != null) {
                String typeDisplay = getTypeDisplay(mediaItem.getType());
                typeText.setText(typeDisplay);
                typeText.setVisibility(View.VISIBLE);
            } else {
                typeText.setVisibility(View.GONE);
            }
            
            // 📊 进度信息
            if (mediaItem.isTvSeries()) {
                progressText.setText(mediaItem.getEpisodeProgressText());
                progressText.setVisibility(View.VISIBLE);
            } else if (mediaItem.hasWatchProgress()) {
                progressText.setText(mediaItem.getProgressText());
                progressText.setVisibility(View.VISIBLE);
            } else {
                progressText.setVisibility(View.GONE);
            }
        }
        
        /**
         * 🎭 获取类型显示文本
         */
        private String getTypeDisplay(String type) {
            switch (type) {
                case "movie":
                    return "🎬 电影";
                case "tv":
                    return "📺 电视剧";
                case "anime":
                    return "🎨 动漫";
                case "documentary":
                    return "📖 纪录片";
                case "variety":
                    return "🎪 综艺";
                default:
                    return "📱 未知";
            }
        }
    }
}
