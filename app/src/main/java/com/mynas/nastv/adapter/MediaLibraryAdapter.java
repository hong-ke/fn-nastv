package com.mynas.nastv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mynas.nastv.R;
import com.mynas.nastv.manager.MediaManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体库列表适配器
 * 显示左侧导航栏的媒体库列表
 */
public class MediaLibraryAdapter extends RecyclerView.Adapter<MediaLibraryAdapter.MediaLibraryViewHolder> {
    
    private List<MediaManager.MediaDbItem> mediaLibraries = new ArrayList<>();
    private OnLibraryClickListener listener;
    private int selectedPosition = -1;
    
    public interface OnLibraryClickListener {
        void onLibraryClick(MediaManager.MediaDbItem library, int position);
    }
    
    public MediaLibraryAdapter(OnLibraryClickListener listener) {
        this.listener = listener;
    }
    
    public void updateLibraries(List<MediaManager.MediaDbItem> libraries) {
        this.mediaLibraries.clear();
        this.mediaLibraries.addAll(libraries);
        notifyDataSetChanged();
    }
    
    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }
    }
    
    @NonNull
    @Override
    public MediaLibraryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_library, parent, false);
        return new MediaLibraryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MediaLibraryViewHolder holder, int position) {
        MediaManager.MediaDbItem library = mediaLibraries.get(position);
        holder.bind(library, position == selectedPosition);
    }
    
    @Override
    public int getItemCount() {
        return mediaLibraries.size();
    }
    
    /**
     * 获取媒体库列表
     */
    public List<MediaManager.MediaDbItem> getLibraries() {
        return mediaLibraries;
    }
    
    class MediaLibraryViewHolder extends RecyclerView.ViewHolder {
        private TextView libraryName;
        private TextView libraryCount;
        private View itemView;
        
        public MediaLibraryViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            libraryName = itemView.findViewById(R.id.library_name);
            libraryCount = itemView.findViewById(R.id.library_count);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onLibraryClick(mediaLibraries.get(position), position);
                }
            });
        }
        
        public void bind(MediaManager.MediaDbItem library, boolean isSelected) {
            libraryName.setText(library.getName());
            
            // 显示真实的媒体库项目数量
            int count = library.getItemCount();
            if (count > 0) {
                libraryCount.setText(String.valueOf(count));
            } else {
                libraryCount.setText("0");
            }
            
            // 设置选中状态
            if (isSelected) {
                itemView.setBackgroundColor(itemView.getContext().getColor(R.color.tv_accent_light));
            } else {
                itemView.setBackground(null);
            }
        }
    }
}
