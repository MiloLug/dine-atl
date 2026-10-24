package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class Button extends TextView {
	// the ui slot's enum ui_button
	static final int KIND_PUSH = 0;
	static final int KIND_TOGGLE = 1;
	static final int KIND_CHECK = 2;
	static final int KIND_RADIO = 3;
	static final int KIND_SWITCH = 4;

	public Button(Context context) {
		this(context, null);
	}

	public Button(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public Button(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public Button(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		clickableByDefault(context, attrs, defStyleAttr, defStyleRes);
	}

	/* Android's button styles make buttons clickable and focusable, each overridden by the app's own
	 * attribute; ATL's buttons don't take those styles */
	private void clickableByDefault(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.View, defStyleAttr, defStyleRes);
		if (!a.hasValue(com.android.internal.R.styleable.View_clickable))
			setClickable(true);
		if (!a.hasValue(com.android.internal.R.styleable.View_focusable))
			setFocusable(FOCUSABLE);
		a.recycle();
	}

	// the toolkit control the class shows; View's constructor asks, before the subclass's fields exist
	int kind() {
		return KIND_PUSH;
	}

	@Override
	protected long native_constructor(Context context, AttributeSet attrs) {
		return native_new(kind());
	}

	private native long native_new(int kind);

	private native void native_setState(long widget, boolean pressed, boolean checked);

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		native_setState(widget, isPressed(), this instanceof Checkable && ((Checkable)this).isChecked());
	}
}
