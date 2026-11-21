package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class Toolbar extends View {

	public Toolbar(Context context) {
		super(context);
	}

	public Toolbar(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public Toolbar(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public Toolbar(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public void setNavigationIcon(int resId) {}

	public void setNavigationOnClickListener(OnClickListener listener) {}
}
