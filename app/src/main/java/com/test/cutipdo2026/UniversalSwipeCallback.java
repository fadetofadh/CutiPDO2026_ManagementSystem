package com.test.cutipdo2026;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class UniversalSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final OnSwipeListener listener;
    private final Paint paint = new Paint();
    private final Drawable approveIcon;
    private final Drawable declineIcon;

    public interface OnSwipeListener {
        void onApprove(int position);
        default void onDecline(int position) {}
        default boolean canSwipe(int position) { return true; }
    }

    public UniversalSwipeCallback(OnSwipeListener listener, android.content.Context context, int swipeDirs) {
        super(0, swipeDirs);
        this.listener = listener;
        this.approveIcon = ContextCompat.getDrawable(context, android.R.drawable.checkbox_on_background);
        this.declineIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_close_clear_cancel);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int position = viewHolder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || !listener.canSwipe(position)) {
            return 0; // Disable swipe
        }
        return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        if (direction == ItemTouchHelper.RIGHT) {
            listener.onApprove(viewHolder.getAdapterPosition());
        } else if (direction == ItemTouchHelper.LEFT) {
            listener.onDecline(viewHolder.getAdapterPosition());
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;
            float height = (float) itemView.getBottom() - (float) itemView.getTop();
            float width = height / 3;

            if (dX > 0) { // Swiping Right (Approve)
                paint.setColor(Color.parseColor("#4CAF50"));
                RectF background = new RectF((float) itemView.getLeft(), (float) itemView.getTop(), dX, (float) itemView.getBottom());
                c.drawRect(background, paint);

                if (approveIcon != null) {
                    int margin = (int) (height - width) / 2;
                    int top = itemView.getTop() + margin;
                    int bottom = itemView.getBottom() - margin;
                    int left = itemView.getLeft() + margin;
                    int right = itemView.getLeft() + margin + (int) width;

                    approveIcon.setBounds(left, top, right, bottom);
                    approveIcon.setTint(Color.WHITE);
                    approveIcon.draw(c);
                }
            } else if (dX < 0) { // Swiping Left (Decline)
                paint.setColor(Color.parseColor("#E53E3E"));
                RectF background = new RectF((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());
                c.drawRect(background, paint);

                if (declineIcon != null) {
                    int margin = (int) (height - width) / 2;
                    int top = itemView.getTop() + margin;
                    int bottom = itemView.getBottom() - margin;
                    int left = itemView.getRight() - margin - (int) width;
                    int right = itemView.getRight() - margin;

                    declineIcon.setBounds(left, top, right, bottom);
                    declineIcon.setTint(Color.WHITE);
                    declineIcon.draw(c);
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
