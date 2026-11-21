package android.widget;

import android.view.View;

public class TabWidget extends View {
	public TabWidget(android.content.Context context) {
		this(context, null);
	}

	public TabWidget(android.content.Context context, android.util.AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TabWidget(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public TabWidget(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public void setDividerDrawable(android.graphics.drawable.Drawable drawable) {}

	public void setStripEnabled(boolean stripEnabled) {}
}
