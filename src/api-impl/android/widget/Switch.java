package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class Switch extends CompoundButton {

	public Switch(Context context) {
		super(context);
	}

	public Switch(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public Switch(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public Switch(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	@Override
	int kind() {
		return KIND_SWITCH;
	}

	public void setTextOn(CharSequence text) {}
	public void setTextOff(CharSequence text) {}
}
