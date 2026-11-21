package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class ImageButton extends ImageView {

	public ImageButton(Context context) {
		this(context, null);
	}

	public ImageButton(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, com.android.internal.R.attr.imageButtonStyle);
	}

	public ImageButton(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public ImageButton(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
		setFocusable(true);
	}
}
