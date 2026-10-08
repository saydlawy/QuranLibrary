package com.example.quranlibrary.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.db.Video;

import java.util.Objects;

public class VideoAdapter extends ListAdapter<Video, VideoAdapter.VideoViewHolder> {

    public VideoAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Video> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Video>() {
                @Override
                public boolean areItemsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    return oldItem.id == newItem.id
                            && oldItem.title.equals(newItem.title)
                            && Objects.equals(oldItem.downloadStatus, newItem.downloadStatus)
                            && oldItem.progress == newItem.progress
                            && equalsNullable(oldItem.filePath, newItem.filePath)
                            && equalsNullable(oldItem.errorMessage, newItem.errorMessage);
                }
            };

    private static boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView status;
        private final TextView path;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.video_title);
            status = itemView.findViewById(R.id.video_status);
            path = itemView.findViewById(R.id.video_path);
        }

        void bind(Video video) {
            title.setText(video.title);
            status.setText(statusText(video));
            path.setText(video.filePath == null || video.filePath.isEmpty()
                    ? itemView.getContext().getString(R.string.file_path_unknown)
                    : video.filePath);
        }

        private String statusText(Video video) {
            if (video.downloadStatus == com.example.quranlibrary.data.model.DownloadStatus.COMPLETED) {
                return itemView.getContext().getString(R.string.download_completed);
            }
            if (video.downloadStatus == com.example.quranlibrary.data.model.DownloadStatus.FAILED) {
                return itemView.getContext().getString(R.string.download_failed);
            }
            return video.downloadStatus.name() + " (" + video.progress + "%)";
        }
    }
}
