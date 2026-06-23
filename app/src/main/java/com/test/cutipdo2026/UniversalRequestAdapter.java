package com.test.cutipdo2026;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.chauthai.swipereveallayout.SwipeRevealLayout;
import com.chauthai.swipereveallayout.ViewBinderHelper;
import java.util.List;

public class UniversalRequestAdapter extends RecyclerView.Adapter<UniversalRequestAdapter.RequestViewHolder> {

    private final List<LeaveRequestData> requestList;
    private final ViewBinderHelper binderHelper = new ViewBinderHelper();
    private final OnItemActionListener actionListener;
    private SwipeRevealLayout currentlyOpenedLayout;
    private boolean isSwipeLocked = false;

    public interface OnItemActionListener {
        void onEditSelected(LeaveRequestData request, int position);
        void onDeleteSelected(int position);
        void onApproveQuick(int position);
    }

    public void onApproveQuick(int position) {
        if (actionListener != null) {
            actionListener.onApproveQuick(position);
        }
    }

    public UniversalRequestAdapter(List<LeaveRequestData> requestList, OnItemActionListener actionListener) {
        this.requestList = requestList;
        this.actionListener = actionListener;
        this.binderHelper.setOpenOnlyOne(true);
    }

    public void setSwipeLocked(boolean locked) {
        this.isSwipeLocked = locked;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_universal_request, parent, false);
        return new RequestViewHolder(view);
    }

    private boolean isSelectionMode() {
        for (LeaveRequestData req : requestList) {
            if (req.isMarked) return true;
        }
        return false;
    }

    public void clearAllMarks() {
        for (LeaveRequestData req : requestList) {
            req.isMarked = false;
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (LeaveRequestData req : requestList) {
            req.isMarked = true;
        }
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        LeaveRequestData item = requestList.get(position);
        binderHelper.bind(holder.swipeLayout, item.employeeName + position);

        holder.tvRowTitle.setText(holder.itemView.getContext().getString(R.string.item_title_format, item.employeeName, item.leaveType));
        holder.tvRowSubtitle.setText(holder.itemView.getContext().getString(R.string.item_subtitle_format, item.getFormattedDate(), item.totalDays, (item.description != null ? item.description : "-")));

        // Marking visual state
        holder.indicatorMarked.setVisibility(item.isMarked ? View.VISIBLE : View.GONE);
        holder.surfaceLayout.setBackgroundColor(item.isMarked ? Color.parseColor("#EDF2F7") : Color.WHITE);

        // 🔒 Disable swipe-reveal drawer if item is marked OR if global lock is on
        holder.swipeLayout.setLockDrag(item.isMarked || isSwipeLocked);

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
                item.isMarked = !item.isMarked;
                notifyItemChanged(holder.getAdapterPosition());
            } else if (currentlyOpenedLayout != null) {
                // 💡 FIX: Tapping ANY item closes the currently open swipe
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
        return requestList.size();
    }

    public static class RequestViewHolder extends RecyclerView.ViewHolder {
        SwipeRevealLayout swipeLayout;
        LinearLayout surfaceLayout, btnEdit, btnDelete;
        TextView tvRowTitle, tvRowSubtitle;
        View indicatorMarked;

        RequestViewHolder(View itemView) {
            super(itemView);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
            surfaceLayout = itemView.findViewById(R.id.surfaceLayout);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvRowTitle = itemView.findViewById(R.id.tvRowTitle);
            tvRowSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
            indicatorMarked = itemView.findViewById(R.id.indicatorMarked);
        }
    }
}
