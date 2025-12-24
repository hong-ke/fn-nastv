package com.mynas.nastv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.model.FavoriteListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 📚 收藏列表适配器
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {
    
    private List<FavoriteListResponse.FavoriteItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(FavoriteListResponse.FavoriteItem item, int position);
    }
    
    public FavoriteAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void updateItems(List<FavoriteListResponse.FavoriteItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }
    
    public void appendItems(List<FavoriteListResponse.FavoriteItem> newItems) {
        if (newItems != null && !newItems.isEmpty()) {
            int startPos = items.size();
            items.addAll(newItems);
            notifyItemRangeInserted(startPos, newItems.size());
        }
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_favorite, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteListResponse.FavoriteItem item = items.get(position);
        holder.bind(item, position);
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView posterImage;
        TextView ratingBadge;
        TextView typeBadge;
        TextView titleText;
        TextView infoText;
        
        ViewHolder(View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.poster_image);
            ratingBadge = itemView.findViewById(R.id.rating_badge);
            typeBadge = itemView.findViewById(R.id.type_badge);
            titleText = itemView.findViewById(R.id.title_text);
            infoText = itemView.findViewById(R.id.info_text);
        }
        
        void bind(FavoriteListResponse.FavoriteItem item, int position) {
            // 标题
            titleText.setText(item.getDisplayTitle() != null ? item.getDisplayTitle() : "未知");
            
            // 类型标签
            String type = item.getType();
            if ("movie".equalsIgnoreCase(type)) {
                typeBadge.setText("电影");
            } else if ("tv".equalsIgnoreCase(type)) {
                typeBadge.setText("电视剧");
            } else if ("episode".equalsIgnoreCase(type)) {
                typeBadge.setText("单集");
            } else {
                typeBadge.setText(type != null ? type : "");
            }
            
            // 年份/信息
            StringBuilder info = new StringBuilder();
            String year = item.getYear();
            if (year != null && !year.isEmpty()) {
                info.append(year);
            }
            if (item.getNumberOfSeasons() > 0) {
                if (info.length() > 0) info.append(" · ");
                info.append(item.getNumberOfSeasons()).append("季");
            }
            infoText.setText(info.toString());
            
            // 评分
            double rating = item.getRating();
            if (rating > 0) {
                ratingBadge.setText(String.format("%.1f", rating));
                ratingBadge.setVisibility(View.VISIBLE);
            } else {
                ratingBadge.setVisibility(View.GONE);
            }
            
            // 海报
            String poster = item.getPoster();
            if (poster != null && !poster.isEmpty()) {
                String posterUrl = poster;
                if (!posterUrl.startsWith("http")) {
                    posterUrl = SharedPreferencesManager.getImageServiceUrl() + poster + "?w=300";
                }
                Glide.with(itemView.getContext())
                    .load(posterUrl)
                    .placeholder(R.drawable.bg_card)
                    .into(posterImage);
            } else {
                posterImage.setImageResource(R.drawable.bg_card);
            }
            
            // 点击事件
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item, position);
                }
            });
            
            // 焦点效果
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.1f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            });
        }
    }
}
