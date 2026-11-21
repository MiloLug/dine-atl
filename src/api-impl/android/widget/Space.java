package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class Space extends View {

	public Space(Context context) {
		super(context);
	}

	public Space(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public Space(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public Space(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}
}
