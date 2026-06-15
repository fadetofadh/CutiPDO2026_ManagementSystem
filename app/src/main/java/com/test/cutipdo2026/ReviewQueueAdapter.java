package com.test.cutipdo2026;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.chauthai.swipereveallayout.SwipeRevealLayout;
import com.chauthai.swipereveallayout.ViewBinderHelper;
import java.util.ArrayList;

public class ReviewQueueAdapter extends RecyclerView.Adapter<ReviewQueueAdapter.QueueViewHolder> {

    private final ArrayList<QueuedRequest> batchList;
    private final ViewBinderHelper binderHelper = new ViewBinderHelper();
    private final OnItemActionListener actionListener;
    private SwipeRevealLayout currentlyOpenedLayout;

    public interface OnItemActionListener {
        void onEditSelected(QueuedRequest request, int position);
        void onDeleteSelected(int position);
    }

    public ReviewQueueAdapter(ArrayList<QueuedRequest> batchList, OnItemActionListener actionListener) {
        this.batchList = batchList;
        this.actionListener = actionListener;
        this.binderHelper.setOpenOnlyOne(true);
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_universal_request, parent, false);
        return new QueueViewHolder(view);
    }

    private boolean isSelectionMode() {
        for (QueuedRequest req : batchList) {
            if (req.isMarked) return true;
        }
        return false;
    }

    public void clearAllMarks() {
        for (QueuedRequest req : batchList) {
            req.isMarked = false;
        }
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        QueuedRequest item = batchList.get(position);
        binderHelper.bind(holder.swipeLayout, item.getEmployeeName() + position);

        holder.tvRowTitle.setText(holder.itemView.getContext().getString(R.string.item_title_format, item.getEmployeeName(), item.getLeaveType()));
        holder.tvRowSubtitle.setText(holder.itemView.getContext().getString(R.string.item_subtitle_format, item.getTargetDate(), item.getTotalDays(), item.getDescription()));

        // Marking state visual
        holder.indicatorMarked.setVisibility(item.isMarked ? View.VISIBLE : View.GONE);
        holder.surfaceLayout.setBackgroundColor(item.isMarked ? Color.parseColor("#EDF2F7") : Color.WHITE);
        
        // 🔒 Disable swipe-left when item is marked
        holder.swipeLayout.setLockDrag(item.isMarked);

        // Listen for open/close to manage global swipe state
        holder.swipeLayout.setSwipeListener(new SwipeRevealLayout.SimpleSwipeListener() {
            @Override
            public void onOpened(SwipeRevealLayout view) {
                currentlyOpenedLayout = view;
            }

            @Override
            public void onClosed(SwipeRevealLayout view) {
                if (currentlyOpenedLayout == view) {
                    currentlyOpenedLayout = null;
                }
            }
        });

        // Standard Android Selection Logic
        holder.surfaceLayout.setOnClickListener(v -> {
            if (isSelectionMode()) {
                // If we are selecting items, tap toggles marking
                item.isMarked = !item.isMarked;
                notifyItemChanged(holder.getAdapterPosition());
            } else if (currentlyOpenedLayout != null) {
                // 💡 FIX: Tapping ANY item (self or other) closes the currently open swipe
                currentlyOpenedLayout.close(true);
            }
        });

        holder.surfaceLayout.setOnLongClickListener(v -> {
            if (!isSelectionMode()) {
                item.isMarked = true;
                notifyItemChanged(holder.getAdapterPosition());
                return true;
            }
            return false;
        });

        holder.btnEdit.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                holder.swipeLayout.close(true);
                actionListener.onEditSelected(item, pos);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                holder.swipeLayout.close(true);
                actionListener.onDeleteSelected(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return batchList.size();
    }

    public static class QueueViewHolder extends RecyclerView.ViewHolder {
        SwipeRevealLayout swipeLayout;
        LinearLayout actionLayout, surfaceLayout, btnEdit, btnDelete;
        TextView tvRowTitle, tvRowSubtitle;
        View indicatorMarked;

        QueueViewHolder(View itemView) {
            super(itemView);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
            actionLayout = itemView.findViewById(R.id.actionLayout);
            surfaceLayout = itemView.findViewById(R.id.surfaceLayout);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvRowTitle = itemView.findViewById(R.id.tvRowTitle);
            tvRowSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
            indicatorMarked = itemView.findViewById(R.id.indicatorMarked);
        }
    }
}
