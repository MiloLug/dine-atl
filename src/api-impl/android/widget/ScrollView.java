/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/widget/ScrollView.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/* The toolkit's scroll view (UIScrollView, GtkScrolledWindow) scrolls and reports; Java's scroll
 * offset mirrors it and asks it to move. */
public class ScrollView extends FrameLayout {
	public ScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public ScrollView(Context context) {
		super(context);
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
			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
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

	public void setFillViewport(boolean fillViewport) {}

	private int getScrollRange() {
		if (getChildCount() == 0)
			return 0;
		View child = getChildAt(0);
		return Math.max(0, child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
	}

	@Override
	public void scrollTo(int x, int y) {
		// we rely on the fact the View.scrollBy calls scrollTo.
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
			y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
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
		x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
		y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
		if (x != getScrollX() || y != getScrollY())
			atlScrollTo(x, y, true);
	}

	// AOSP also focuses a view in the new range
	public boolean fullScroll(int direction) {
		int y = direction == View.FOCUS_DOWN ? getScrollRange() : 0;
		boolean scrolls = y != getScrollY();
		smoothScrollTo(getScrollX(), y);
		return scrolls;
	}

	@Override
	public void requestChildFocus(View child, View focused) {
		if (focused != null)
			scrollToChild(focused);
		super.requestChildFocus(child, focused);
	}

	@Override
	public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
		// offset into coordinate space of this scroll view
		rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
		return scrollToChildRect(rectangle, immediate);
	}

	private void scrollToChild(View child) {
		Rect rect = new Rect();
		child.getDrawingRect(rect);

		/* Offset from child's local coordinates to ScrollView coordinates */
		offsetDescendantRectToMyCoords(child, rect);

		int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(rect);

		if (scrollDelta != 0) {
			scrollBy(0, scrollDelta);
		}
	}

	private boolean scrollToChildRect(Rect rect, boolean immediate) {
		final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
		final boolean scroll = delta != 0;
		if (scroll) {
			if (immediate) {
				scrollBy(0, delta);
			} else {
				smoothScrollBy(0, delta);
			}
		}
		return scroll;
	}

	protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
		if (getChildCount() == 0)
			return 0;

		int height = getHeight();
		int screenTop = getScrollY();
		int screenBottom = screenTop + height;

		int fadingEdge = getVerticalFadingEdgeLength();

		// leave room for top fading edge as long as rect isn't at very top
		if (rect.top > 0) {
			screenTop += fadingEdge;
		}

		// leave room for bottom fading edge as long as rect isn't at very bottom
		if (rect.bottom < getChildAt(0).getHeight()) {
			screenBottom -= fadingEdge;
		}

		int scrollYDelta = 0;

		if (rect.bottom > screenBottom && rect.top > screenTop) {
			// need to move down to get it in view: move down just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if (rect.height() > height) {
				// just enough to get screen size chunk on
				scrollYDelta += (rect.top - screenTop);
			} else {
				// get entire rect at bottom of screen
				scrollYDelta += (rect.bottom - screenBottom);
			}

			// make sure we aren't scrolling beyond the end of our content
			int bottom = getChildAt(0).getBottom();
			int distanceToBottom = bottom - screenBottom;
			scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

		} else if (rect.top < screenTop && rect.bottom < screenBottom) {
			// need to move up to get it in view: move up just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if (rect.height() > height) {
				// screen size chunk
				scrollYDelta -= (screenBottom - rect.bottom);
			} else {
				// entire rect at top
				scrollYDelta -= (screenTop - rect.top);
			}

			// make sure we aren't scrolling any further than the top our content
			scrollYDelta = Math.max(scrollYDelta, -getScrollY());
		}
		return scrollYDelta;
	}

	// 0 when n is before the start or the child fits; else n, at most where the child's end shows
	static int clamp(int n, int my, int child) {
		if (my >= child || n < 0) {
			return 0;
		}
		if ((my + n) > child) {
			return child - my;
		}
		return n;
	}
}
