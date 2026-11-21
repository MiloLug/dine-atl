package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class RatingBar extends AbsSeekBar {

	public RatingBar(Context context) {
		this(context, null);
	}

	public RatingBar(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public RatingBar(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public RatingBar(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public void setRating(float rating) {
		setProgress((int)rating);
	}
}
