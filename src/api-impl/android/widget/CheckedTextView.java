package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public class CheckedTextView extends TextView {

	public CheckedTextView(Context context) {
		super(context);
	}

	public CheckedTextView(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public CheckedTextView(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public CheckedTextView(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public void setChecked(boolean checked) {}

	public void setCheckMarkDrawable(Drawable d) {}
}
