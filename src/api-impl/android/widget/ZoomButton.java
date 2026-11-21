package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class ZoomButton extends View {

	public ZoomButton(Context context) {
		this(context, null);
	}

	public ZoomButton(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ZoomButton(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public ZoomButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public void setZoomSpeed(long speed) {}
}
