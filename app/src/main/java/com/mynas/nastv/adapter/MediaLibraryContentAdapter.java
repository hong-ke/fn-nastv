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
import com.mynas.nastv.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 📚 媒体库内容适配器
 * 用于显示每个媒体库的内容列表（水平滚动）
 */
public class MediaLibraryContentAdapter extends RecyclerView.Adapter<MediaLibraryContentAdapter.MediaContentViewHolder> {

    private List<MediaItem> mediaItems = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MediaItem item, int position);
    }

    public MediaLibraryContentAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateItems(List<MediaItem> items) {
        this.mediaItems.clear();
        if (items != null) {
            this.mediaItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_content, parent, false);
        return new MediaContentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaContentViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    class MediaContentViewHolder extends RecyclerView.ViewHolder {
        private ImageView posterImage;
        private TextView titleText;
        private TextView subtitleText;

        public MediaContentViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.media_poster);
            titleText = itemView.findViewById(R.id.media_title);
            subtitleText = itemView.findViewById(R.id.media_subtitle);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(mediaItems.get(position), position);
                }
            });
        }

        public void bind(MediaItem item) {
            // 🖼️ 加载海报图片
            String posterUrl = item.getPosterUrl();
            if (posterUrl != null && !posterUrl.isEmpty()) {
                Glide.with(posterImage.getContext())
                        .asBitmap()
                        .load(posterUrl)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.color.tv_card_background)
                        .error(R.color.tv_card_background)
                        .centerCrop()
                        .into(posterImage);
            } else {
                posterImage.setImageResource(R.color.tv_card_background);
            }

            // 标题
            titleText.setText(item.getTitle());

            // 副标题
            String subtitle = item.getSubtitle();
            if (subtitle == null || subtitle.isEmpty()) {
                subtitle = item.getType();
            }
            subtitleText.setText(subtitle);

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
