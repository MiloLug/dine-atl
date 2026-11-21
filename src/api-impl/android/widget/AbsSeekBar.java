package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public abstract class AbsSeekBar extends ProgressBar {

	public AbsSeekBar(Context context) {
		super(context);
	}

	public AbsSeekBar(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public AbsSeekBar(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public AbsSeekBar(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public Drawable getThumb() {
		return new Drawable();
	}

	public void setKeyProgressIncrement(int keyProgressIncrement) {}

	public int getKeyProgressIncrement() { return 0; }

	public int getThumbOffset() { return 0; }

	public void setThumbTintList(ColorStateList tint) {}

	public void setThumb(Drawable thumb) {}
}
