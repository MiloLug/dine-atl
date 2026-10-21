/*
 * Portions Copyright (C) 2009 The Android Open Source Project, from
 * core/java/android/widget/HorizontalScrollView.java at android-security-10.0.0_r75, Apache License
 * 2.0; see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/* ScrollView's horizontal twin: the same toolkit scroll node, across. */
public class HorizontalScrollView extends FrameLayout {

	public HorizontalScrollView(Context context) {
		super(context);
	}

	public HorizontalScrollView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	public HorizontalScrollView(Context context, AttributeSet attributeSet, int defStyleAttr) {
		super(context, attributeSet, defStyleAttr);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = 0;
		int height = 0;
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			ViewGroup.LayoutParams lp = child.getLayoutParams();
			int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, 0, lp.height);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			width = child.getMeasuredWidth();
			height = child.getMeasuredHeight();
		}
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
	}

	@Override
	public boolean shouldDelayChildPressedState() {
		return true;
	}

	public void setHorizontalScrollBarEnabled(boolean enabled) {}

	public void setFillViewport(boolean fillViewport) {}

	@Override
	public void scrollTo(int x, int y) {
		// we rely on the fact the View.scrollBy calls scrollTo.
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			x = ScrollView.clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
			y = ScrollView.clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
			if (x != getScrollX() || y != getScrollY()) {
				super.scrollTo(x, y);
			}
		}
	}

	public final void smoothScrollBy(int dx, int dy) {
		smoothScrollTo(getScrollX() + dx, getScrollY() + dy);
	}

	public final void smoothScrollTo(int x, int y) {
		if (getChildCount() == 0)
			return;
		View child = getChildAt(0);
		x = ScrollView.clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
		y = ScrollView.clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
		if (x != getScrollX() || y != getScrollY())
			atlScrollTo(x, y, true);
	}

	// AOSP also focuses a view in the new range
	public boolean fullScroll(int direction) {
		int range = getChildCount() > 0 ? Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight())) : 0;
		int x = direction == View.FOCUS_RIGHT ? range : 0;
		boolean scrolls = x != getScrollX();
		smoothScrollTo(x, getScrollY());
		return scrolls;
	}

	@Override
	public void requestChildFocus(View child, View focused) {
		if (focused != null) {
			Rect rect = new Rect();
			focused.getDrawingRect(rect);
			offsetDescendantRectToMyCoords(focused, rect);
			int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(rect);
			if (scrollDelta != 0)
				scrollBy(scrollDelta, 0);
		}
		super.requestChildFocus(child, focused);
	}

	@Override
	public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
		// offset into coordinate space of this scroll view
		rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
		final int delta = computeScrollDeltaToGetChildRectOnScreen(rectangle);
		final boolean scroll = delta != 0;
		if (scroll) {
			if (immediate) {
				scrollBy(delta, 0);
			} else {
				smoothScrollBy(delta, 0);
			}
		}
		return scroll;
	}

	protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
		if (getChildCount() == 0)
			return 0;

		int width = getWidth();
		int screenLeft = getScrollX();
		int screenRight = screenLeft + width;

		int scrollXDelta = 0;

		if (rect.right > screenRight && rect.left > screenLeft) {
			// need to move right to get it in view: move right just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if (rect.width() > width) {
				// just enough to get screen size chunk on
				scrollXDelta += (rect.left - screenLeft);
			} else {
				// get entire rect at right of screen
				scrollXDelta += (rect.right - screenRight);
			}

			// make sure we aren't scrolling beyond the end of our content
			int right = getChildAt(0).getRight();
			int distanceToRight = right - screenRight;
			scrollXDelta = Math.min(scrollXDelta, distanceToRight);

		} else if (rect.left < screenLeft && rect.right < screenRight) {
			// need to move right to get it in view: move right just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if (rect.width() > width) {
				// screen size chunk
				scrollXDelta -= (screenRight - rect.right);
			} else {
				// entire rect at left
				scrollXDelta -= (screenLeft - rect.left);
			}

			// make sure we aren't scrolling any further than the left our content
			scrollXDelta = Math.max(scrollXDelta, -getScrollX());
		}
		return scrollXDelta;
	}
}
