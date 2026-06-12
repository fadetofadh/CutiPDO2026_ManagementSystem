package com.test.cutipdo2026;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.chauthai.swipereveallayout.SwipeRevealLayout;
import com.chauthai.swipereveallayout.ViewBinderHelper;
import java.util.ArrayList;

public class ReviewQueueAdapter extends RecyclerView.Adapter<ReviewQueueAdapter.QueueViewHolder> {

    private ArrayList<QueuedRequest> batchList;
    // Helper tracker to remember which specific rows are swiped open
    private final ViewBinderHelper binderHelper = new ViewBinderHelper();
    private OnItemActionListener actionListener;

    // Click Callback Interface layout to send clicks back to Activity
    public interface OnItemActionListener {
        void onEditSelected(QueuedRequest request, int position);
        void onDeleteSelected(int position);
    }

    public ReviewQueueAdapter(ArrayList<QueuedRequest> batchList, OnItemActionListener actionListener) {
        this.batchList = batchList;
        this.actionListener = actionListener;
        // Keeps only one row slide-open at any given time
        this.binderHelper.setOpenOnlyOne(true);
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queued_request, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        QueuedRequest item = batchList.get(position);

        // Bind the sliding animation state to this item's unique name memory slot
        binderHelper.bind(holder.swipeLayout, item.getEmployeeName() + position);

        holder.tvRowTitle.setText(holder.itemView.getContext().getString(R.string.item_title_format, item.getEmployeeName(), item.getLeaveType()));
        holder.tvRowSubtitle.setText(holder.itemView.getContext().getString(R.string.item_subtitle_format, item.getTargetDate(), item.getTotalDays(), item.getDescription()));

        // Route underlying bottom click triggers to your custom activity actions callback routines
        holder.btnEditRow.setOnClickListener(v -> {
            holder.swipeLayout.close(true); // Close drawer smoothly
            actionListener.onEditSelected(item, position);
        });

        holder.btnDeleteRow.setOnClickListener(v -> {
            holder.swipeLayout.close(true);
            actionListener.onDeleteSelected(position);
        });
    }

    @Override
    public int getItemCount() {
        return batchList.size();
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        SwipeRevealLayout swipeLayout;
        LinearLayout btnEditRow, btnDeleteRow;
        TextView tvRowTitle, tvRowSubtitle;

        QueueViewHolder(View itemView) {
            super(itemView);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
            btnEditRow = itemView.findViewById(R.id.btnEditRow);
            btnDeleteRow = itemView.findViewById(R.id.btnDeleteRow);
            tvRowTitle = itemView.findViewById(R.id.tvRowTitle);
            tvRowSubtitle = itemView.findViewById(R.id.tvRowSubtitle);
        }
    }
}