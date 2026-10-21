package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.AttributeSet;

public class EditText extends TextView {
	public EditText(Context context) {
		super(context);
		setFocusableInTouchMode(true);
	}

	public EditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		focusableByDefault(context, attrs, 0, 0);
	}

	public EditText(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		super(context, attrs, defStyle, defStyleRes);
		focusableByDefault(context, attrs, defStyle, defStyleRes);
	}

	/* Android's editTextStyle sets focusable and focusableInTouchMode, each overridden by the app's own
	 * attribute; ATL's EditText doesn't take that style */
	private void focusableByDefault(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.View, defStyle, defStyleRes);
		if (!a.hasValue(com.android.internal.R.styleable.View_focusableInTouchMode))
			setFocusableInTouchMode(true);
		else if (!a.hasValue(com.android.internal.R.styleable.View_focusable))
			setFocusable(FOCUSABLE);
		a.recycle();
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	protected native String native_getText(long widget);
	protected native void native_addTextChangedListener(long widget, TextWatcher watcher);
	protected native void native_removeTextChangedListener(long widget, TextWatcher watcher);
	protected native void native_setOnEditorActionListener(long widget, OnEditorActionListener l);
	protected native void native_setText(long widget, String text);
	protected native void native_setHint(long widget, CharSequence s);
	protected native CharSequence native_getHint(long widget); // gtk_entry_set_placeholder_text

	public Editable getText() {
		return new SpannableStringBuilder(native_getText(widget));
	}

	public Editable getEditableText() {
		return new SpannableStringBuilder(native_getText(widget));
	}

	@Override
	public void setText(CharSequence text) {
		native_setText(widget, text == null ? "" : text.toString());
	}
	@Override
	void setRawTextSize(float size) {}

	@Override
	public void removeTextChangedListener(TextWatcher watcher) {
		native_removeTextChangedListener(widget, watcher);
	}

	@Override
	public void addTextChangedListener(TextWatcher watcher) {
		native_addTextChangedListener(widget, watcher);
	}

	@Override
	public void setOnEditorActionListener(OnEditorActionListener l) {
		native_setOnEditorActionListener(widget, l);
	}

	@Override
	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {}

	@Override
	public void setHint(CharSequence s) {
		native_setHint(widget, s == null ? "" : s.toString());
	}

	@Override
	public CharSequence getHint() {
		return native_getHint(widget);
	}

	public void selectAll() {}
}
