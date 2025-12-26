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
import com.mynas.nastv.model.PersonInfo;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 演职人员适配器
 * 显示演员头像、姓名、角色
 */
public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.PersonViewHolder> {

    private List<PersonInfo> persons = new ArrayList<>();
    private OnPersonClickListener listener;

    public interface OnPersonClickListener {
        void onPersonClick(PersonInfo person, int position);
    }

    public PersonAdapter() {
        this(null);
    }

    public PersonAdapter(OnPersonClickListener listener) {
        this.listener = listener;
    }

    public void updatePersons(List<PersonInfo> personList) {
        this.persons.clear();
        if (personList != null) {
            this.persons.addAll(personList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_person, parent, false);
        return new PersonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) {
        PersonInfo person = persons.get(position);
        holder.bind(person);
    }

    @Override
    public int getItemCount() {
        return persons.size();
    }

    class PersonViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatarImage;
        private TextView nameText;
        private TextView roleText;

        public PersonViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.person_avatar);
            nameText = itemView.findViewById(R.id.person_name);
            roleText = itemView.findViewById(R.id.person_role);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPersonClick(persons.get(position), position);
                }
            });
        }

        public void bind(PersonInfo person) {
            // 加载头像
            String profilePath = person.getProfilePath();
            if (profilePath != null && !profilePath.isEmpty()) {
                String imageUrl = profilePath;
                if (!imageUrl.startsWith("http")) {
                    imageUrl = SharedPreferencesManager.getServerBaseUrl() + "/v/api/v1/sys/img" + profilePath + "?w=160";
                }
                Glide.with(avatarImage.getContext())
                        .load(imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.drawable.person_avatar_background)
                        .error(R.drawable.person_avatar_background)
                        .circleCrop()
                        .into(avatarImage);
            } else {
                avatarImage.setImageResource(R.drawable.person_avatar_background);
            }

            // 姓名
            nameText.setText(person.getName());

            // 角色/职位
            String role = person.getRole();
            String job = person.getJob();
            
            if (person.isActor() && role != null && !role.isEmpty()) {
                roleText.setText("饰演 " + role);
                roleText.setVisibility(View.VISIBLE);
            } else if (job != null && !job.isEmpty()) {
                roleText.setText(job);
                roleText.setVisibility(View.VISIBLE);
            } else {
                roleText.setVisibility(View.GONE);
            }

            // 焦点动画
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
        }
    }
}
