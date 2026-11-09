/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/widget/TextView.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.widget;

import java.util.ArrayList;

import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.CharacterStyle;

import android.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.BoringLayout;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.BaseMovementMethod;
import android.text.method.KeyListener;
import android.text.method.MovementMethod;
import android.text.method.TransformationMethod;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ATLInput;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.Locale;

public class TextView extends View {
	private ColorStateList colors = new ColorStateList(new int[][] {new int[0]}, new int[1]);
	private CharSequence text = "";
	private BufferType buffer_type = BufferType.NORMAL; // AOSP's mBufferType
	static final int CHANGE_WATCHER_PRIORITY = 100;
	private ArrayList<TextWatcher> listeners;
	private ChangeWatcher changeWatcher;
	private TextPaint paint = new TextPaint();
	boolean single_line; // AOSP's mSingleLine
	private int text_version; // bumped before the listeners of a change run
	private Layout layout;
	private int layout_text_version, layout_width;
	private float layout_text_size;
	private Typeface layout_typeface;
	private boolean include_padding = false;
	private int break_strategy = 0 /*BREAK_STRATEGY_SIMPLE*/;
	private int hyphenation_frequency = 0 /*HYPHENATION_FREQUENCY_NONE*/;
	private int gravity = Gravity.CENTER;
	private boolean allCaps;
	private CharSequence hint;
	private boolean selectable;
	private TransformationMethod transformation;
	OnEditorActionListener editorActionListener;
	// Editor's InputContentType exists: an action listener or IME options were set
	boolean inputContentType;
	// AOSP's mMaximum and mMinimum, in lines or pixels: maxLines and maxHeight share one
	private static final int LINES = 1, PIXELS = 2;
	private int maximum = Integer.MAX_VALUE, maxMode = LINES;
	private int minimum = 0, minMode = LINES;

	public TextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextView(Context context) {
		this(context, null);
	}

	public TextView(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public TextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		float size = native_getTextSize();
		if (size > 0) // a text node: the toolkit's default
			paint.setTextSize(size);

		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.TextView, defStyleAttr, 0);
		try {
			if (a.hasValue(com.android.internal.R.styleable.TextView_hint)) {
				setHint(a.getText(com.android.internal.R.styleable.TextView_hint));
			}

			int ap = a.getResourceId(com.android.internal.R.styleable.TextView_textAppearance, -1);
			if (ap != -1) {
				TypedArray aa = context.obtainStyledAttributes(ap, com.android.internal.R.styleable.TextAppearance);
				if (aa.hasValue(com.android.internal.R.styleable.TextAppearance_textColor)) {
					setTextColor(aa.getColorStateList(com.android.internal.R.styleable.TextAppearance_textColor));
				}
				if (aa.hasValue(com.android.internal.R.styleable.TextAppearance_textSize)) {
					applyTextSize(aa.getDimensionPixelSize(com.android.internal.R.styleable.TextAppearance_textSize, 10));
				}
				if (aa.hasValue(com.android.internal.R.styleable.TextAppearance_textColorLink))
					link_colors = aa.getColorStateList(com.android.internal.R.styleable.TextAppearance_textColorLink);
				aa.recycle();
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_textColorLink))
				link_colors = a.getColorStateList(com.android.internal.R.styleable.TextView_textColorLink);
			if (a.hasValue(com.android.internal.R.styleable.TextView_textColor)) {
				setTextColor(a.getColorStateList(com.android.internal.R.styleable.TextView_textColor));
			}
			if (a.hasValue(com.android.internal.R.styleable.TextView_textSize)) {
				applyTextSize(a.getDimensionPixelSize(com.android.internal.R.styleable.TextView_textSize, 10));
			}

			if (a.hasValue(com.android.internal.R.styleable.TextView_textStyle)) {
				int textStyle = a.getInt(com.android.internal.R.styleable.TextView_textStyle, 0);
				setTypeface(getTypeface(), textStyle);
			}

			if (a.hasValue(com.android.internal.R.styleable.TextView_textAllCaps)) {
				boolean allCaps = a.getBoolean(com.android.internal.R.styleable.TextView_textAllCaps, false);
				setAllCaps(allCaps);
			}
			if (a.getBoolean(com.android.internal.R.styleable.TextView_textIsSelectable, false))
				setTextIsSelectable(true);

			if (a.hasValue(com.android.internal.R.styleable.TextView_minHeight))
				setMinHeight(a.getDimensionPixelSize(com.android.internal.R.styleable.TextView_minHeight, 0));
			if (a.hasValue(com.android.internal.R.styleable.TextView_minLines))
				setMinLines(a.getInt(com.android.internal.R.styleable.TextView_minLines, 0));
			if (a.hasValue(com.android.internal.R.styleable.TextView_maxHeight))
				setMaxHeight(a.getDimensionPixelSize(com.android.internal.R.styleable.TextView_maxHeight, Integer.MAX_VALUE));
			if (a.hasValue(com.android.internal.R.styleable.TextView_maxLines))
				setMaxLines(a.getInt(com.android.internal.R.styleable.TextView_maxLines, Integer.MAX_VALUE));
			if (a.hasValue(com.android.internal.R.styleable.TextView_lines))
				setLines(a.getInt(com.android.internal.R.styleable.TextView_lines, 1));
			// an input type overrides the deprecated singleLine flag
			int inputType = a.getInt(com.android.internal.R.styleable.TextView_inputType, EditorInfo.TYPE_NULL);
			boolean singleLine = inputType != EditorInfo.TYPE_NULL ? !EditText.isMultilineInputType(inputType)
			                                                       : a.getBoolean(com.android.internal.R.styleable.TextView_singleLine, false);
			if (singleLine)
				applySingleLine(true);
		} catch (java.lang.Exception e) {
			System.out.println("exception while inflating TextView:");
			e.printStackTrace();
		}

		if (link_colors == null) // TextAppearance's ?textColorLink, without a textViewStyle
			link_colors = themeColor(context, android.R.attr.textColorLink);
		// AOSP sets the text last, its runs with the colours above
		BufferType[] types = {BufferType.NORMAL, BufferType.SPANNABLE, BufferType.EDITABLE};
		int type = a.getInt(com.android.internal.R.styleable.TextView_bufferType, 0);
		if (a.hasValue(com.android.internal.R.styleable.TextView_text) || type != 0)
			setText(a.getText(com.android.internal.R.styleable.TextView_text), types[type >= 0 && type < types.length ? type : 0]);
		a.recycle();
		haveCustomMeasure = false;
	}

	private static ColorStateList themeColor(Context context, int attr) {
		TypedArray a = context.obtainStyledAttributes(new int[] {attr});
		try {
			return a.getColorStateList(0);
		} finally {
			a.recycle();
		}
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);

	public void setText(int resId) {
		setText(getContext().getResources().getText(resId));
	}

	public void setText(CharSequence text) {
		setText(text, buffer_type);
	}

	/* Copyright (C) 2006 The Android Open Source Project: the buffer types and the ChangeWatcher */
	public void setText(CharSequence text, BufferType type) {
		if (text == null)
			text = "";
		text = atlFiltered(text);
		CharSequence old = this.text;
		int oldlen = old.length();
		sendBeforeTextChanged(old, 0, oldlen, text.length());
		boolean notify = listeners != null && !listeners.isEmpty();
		if (type == BufferType.EDITABLE || notify) {
			Editable t = Editable.Factory.getInstance().newEditable(text);
			atlEditableCreated(t);
			text = t;
			type = BufferType.EDITABLE;
		} else if (type == BufferType.SPANNABLE) {
			text = Spannable.Factory.getInstance().newSpannable(text);
		} else {
			text = TextUtils.stringOrSpannedString(text);
		}
		if (old instanceof Spannable && changeWatcher != null)
			((Spannable)old).removeSpan(changeWatcher);
		buffer_type = type;
		this.text = text;
		text_version++;
		if (text instanceof Spannable) {
			Spannable sp = (Spannable)text;
			for (ChangeWatcher watcher : sp.getSpans(0, sp.length(), ChangeWatcher.class))
				sp.removeSpan(watcher);
			if (changeWatcher == null)
				changeWatcher = new ChangeWatcher();
			sp.setSpan(changeWatcher, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE | (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));
		}
		atlTextReplaced(text);
		sendText();
		if (!isLayoutRequested())
			requestLayout();
		sendOnTextChanged(text, 0, oldlen, text.length());
		if (text instanceof Editable)
			sendAfterTextChanged((Editable)text);
		atlTextChanged();
	}

	// EditText's hooks: its InputFilters, its Editable's filters and selection, its toolkit state
	CharSequence atlFiltered(CharSequence text) {
		return text;
	}

	void atlEditableCreated(Editable editable) {}

	void atlTextReplaced(CharSequence text) {}

	void atlTextChanged() {}

	void atlSpanChanged(Object what) {}

	public void addTextChangedListener(TextWatcher watcher) {
		if (listeners == null)
			listeners = new ArrayList<TextWatcher>();
		listeners.add(watcher);
	}

	public void removeTextChangedListener(TextWatcher watcher) {
		if (listeners != null) {
			int i = listeners.indexOf(watcher);
			if (i >= 0)
				listeners.remove(i);
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
		if (listeners != null) {
			final ArrayList<TextWatcher> list = listeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).beforeTextChanged(text, start, before, after);
			}
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void sendOnTextChanged(CharSequence text, int start, int before, int after) {
		if (listeners != null) {
			final ArrayList<TextWatcher> list = listeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).onTextChanged(text, start, before, after);
			}
		}
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void sendAfterTextChanged(Editable text) {
		if (listeners != null) {
			final ArrayList<TextWatcher> list = listeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).afterTextChanged(text);
			}
		}
	}

	// a change to the view's own Spannable: AOSP's layout reads spans live, so the next frame shows it
	void runsChanged() {
		sendText();
	}

	/* Copyright (C) 2006 The Android Open Source Project: checkForResize without a Layout; an
	 * EditText's toolkit field sizes itself */
	void checkForResize() {
		ViewGroup.LayoutParams params = getLayoutParams();
		if ((params == null || params.width == ViewGroup.LayoutParams.WRAP_CONTENT || params.height == ViewGroup.LayoutParams.WRAP_CONTENT) && !isLayoutRequested())
			requestLayout();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private class ChangeWatcher implements TextWatcher, SpanWatcher {
		public void beforeTextChanged(CharSequence buffer, int start, int before, int after) {
			sendBeforeTextChanged(buffer, start, before, after);
		}

		public void onTextChanged(CharSequence buffer, int start, int before, int after) {
			text_version++;
			sendOnTextChanged(buffer, start, before, after);
		}

		public void afterTextChanged(Editable buffer) {
			sendAfterTextChanged(buffer);
			runsChanged();
			checkForResize();
			atlTextChanged();
		}

		public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
			if (what instanceof CharacterStyle)
				runsChanged();
			if (what instanceof android.text.style.MetricAffectingSpan)
				checkForResize();
			atlSpanChanged(what);
		}

		public void onSpanAdded(Spannable buf, Object what, int s, int e) {
			onSpanChanged(buf, what, -1, s, -1, e);
		}

		public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
			onSpanChanged(buf, what, s, -1, e, -1);
		}
	}

	// slots/ui.h's UI_RUN_* bits (asserted in android_widget_TextView.c), and a run's packing
	private static final int RUN_INTS = 7, RUN_FLOATS = 3;
	private static final int RUN_COLOR = 1 << 0;
	private static final int RUN_BACKGROUND = 1 << 1;
	private static final int RUN_SIZE = 1 << 2;
	private static final int RUN_STYLE = 1 << 3;
	private static final int RUN_FAMILY = 1 << 4;
	private static final int RUN_UNDERLINE = 1 << 5;
	private static final int RUN_STRIKE = 1 << 6;
	private static final int RUN_BASELINE = 1 << 7;
	private static final int RUN_SCALE_X = 1 << 8;

	private int current_text_color = 0xff000000;
	private ColorStateList link_colors;

	// runs are absolute and set where they differ from the view's size, color and typeface
	private void resendStyled() {
		if (text instanceof Spanned)
			sendText();
	}

	// the shown text and, for a Spanned, its CharacterStyles as runs (native_setText's layout); an
	// EditText's toolkit text is its Editable's (EditText.sync)
	void sendText() {
		String shown = shown();
		if (!(text instanceof Spanned) || shown.length() != text.length()) {
			native_setText(shown, null, null);
			return;
		}
		Spanned spanned = (Spanned)text;
		TextPaint base = new TextPaint();
		base.set(paint);
		base.setColor(current_text_color);
		base.linkColor = link_colors != null ? link_colors.getColorForState(getDrawableState(), 0) : 0;
		base.density = getResources().getDisplayMetrics().density;
		ArrayList<int[]> ints = new ArrayList<>();
		ArrayList<float[]> floats = new ArrayList<>();
		int length = spanned.length();
		for (int i = 0, next; i < length; i = next) {
			next = spanned.nextSpanTransition(i, length, CharacterStyle.class);
			CharacterStyle[] styles = spanned.getSpans(i, next, CharacterStyle.class);
			if (styles.length == 0)
				continue;
			TextPaint tp = new TextPaint();
			tp.set(base);
			for (CharacterStyle style : styles) // TextLine's draw state: each span once
				style.updateDrawState(tp);
			int set = 0;
			if (tp.getColor() != base.getColor())
				set |= RUN_COLOR;
			if (tp.bgColor != 0)
				set |= RUN_BACKGROUND;
			if (tp.getTextSize() != base.getTextSize())
				set |= RUN_SIZE;
			Typeface face = tp.getTypeface(), base_face = base.getTypeface();
			int style = (face != null ? face.getStyle() : 0) | (tp.isFakeBoldText() ? Typeface.BOLD : 0) | (tp.getTextSkewX() != 0 ? Typeface.ITALIC : 0);
			int base_style = (base_face != null ? base_face.getStyle() : 0) | (base.isFakeBoldText() ? Typeface.BOLD : 0) | (base.getTextSkewX() != 0 ? Typeface.ITALIC : 0);
			if (style != base_style)
				set |= RUN_STYLE;
			int family = face != null ? face.atlFamily() : 0;
			if (family != (base_face != null ? base_face.atlFamily() : 0))
				set |= RUN_FAMILY;
			if (tp.isUnderlineText() != base.isUnderlineText())
				set |= RUN_UNDERLINE;
			if (tp.isStrikeThruText() != base.isStrikeThruText())
				set |= RUN_STRIKE;
			if (tp.baselineShift != 0)
				set |= RUN_BASELINE;
			if (tp.getTextScaleX() != base.getTextScaleX())
				set |= RUN_SCALE_X;
			if (set == 0)
				continue;
			ints.add(new int[] {i, next, set, tp.getColor(), tp.bgColor, style, family});
			floats.add(new float[] {tp.getTextSize(), tp.baselineShift, tp.getTextScaleX()});
		}
		if (ints.isEmpty()) {
			native_setText(shown, null, null);
			return;
		}
		int[] run_ints = new int[ints.size() * RUN_INTS];
		float[] run_floats = new float[floats.size() * RUN_FLOATS];
		for (int i = 0; i < ints.size(); i++) {
			System.arraycopy(ints.get(i), 0, run_ints, i * RUN_INTS, RUN_INTS);
			System.arraycopy(floats.get(i), 0, run_floats, i * RUN_FLOATS, RUN_FLOATS);
		}
		native_setText(shown, run_ints, run_floats);
	}

	public void setText(char[] text, int start, int len) {
		setText(new String(text, start, len));
	}

	public final void append(CharSequence text) {
		append(text, 0, text.length());
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void append(CharSequence text, int start, int end) {
		if (!(this.text instanceof Editable))
			setText(this.text, BufferType.EDITABLE);
		((Editable)this.text).append(text, start, end);
	}


	private native final void native_setText(String text, int[] run_ints, float[] run_floats);

	public void setTextSize(int unit, float size) {
		applyTextSize(TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics()));
	}
	public void setTextSize(float size) {
		setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
	}
	// the paint first: a subclass's setRawTextSize may not call this one
	private void applyTextSize(float size) {
		paint.setTextSize(size);
		setRawTextSize(size);
		resendStyled();
	}
	// px; package-private so that subclasses whose widget isn't a text node override it
	void setRawTextSize(float size) {
		native_setTextSize(size);
	}
	private native final void native_setTextSize(float size);
	private native final float native_getTextSize();

	public native final void native_setTextColor(int color);
	public void setTextColor(int color) {
		current_text_color = color;
		native_setTextColor(color);
		resendStyled();
	}
	public void setTextColor(ColorStateList colors) {
		if (colors != null) {
			this.colors = colors;
			current_text_color = colors.getDefaultColor();
			native_setTextColor(colors.getDefaultColor()); // TODO: do this properly
			resendStyled();
		}
	}
	/* Copyright (C) 2006 The Android Open Source Project */
	// without fake bold and skew: a family without the style shows the regular face
	public void setTypeface(Typeface tf, int style) {
		setTypeface(style > 0 ? Typeface.create(tf, style) : tf);
	}
	private native final void native_setTextStyle(int style);
	public void setTypeface(Typeface tf) {
		paint.setTypeface(tf);
		native_setTextStyle(tf != null ? tf.getStyle() : Typeface.NORMAL);
		resendStyled();
	}
	public void setLineSpacing(float add, float mult) {}
	public final void setLinksClickable(boolean whether) {}

	public void setInputType(int type) {}
	public void setFilters(InputFilter[] filters) {}
	public void setCursorVisible(boolean visible) {}
	public void setImeOptions(int imeOptions) {}

	public final ColorStateList getTextColors() { return colors; }
	public static ColorStateList getTextColors(Context context, TypedArray attrs) { return new ColorStateList(new int[][] {new int[0]}, new int[1]); }

	public TextPaint getPaint() {
		return paint;
	}

	public void setOnEditorActionListener(TextView.OnEditorActionListener l) {
		editorActionListener = l;
		inputContentType = true;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void onEditorAction(int actionCode) {
		if (inputContentType) {
			if (editorActionListener != null && editorActionListener.onEditorAction(this, actionCode, null))
				return;

			// This is the handling for some default action.
			// Note that for backwards compatibility we don't do this
			// default handling if explicit ime options have not been given,
			// instead turning this into the normal enter key codes that an
			// app may be expecting.
			if (actionCode == EditorInfo.IME_ACTION_NEXT) {
				View v = focusSearch(FOCUS_FORWARD);
				if (v != null) {
					if (!v.requestFocus(FOCUS_FORWARD)) {
						throw new IllegalStateException("focus search returned a view "
						                                + "that wasn't able to take focus!");
					}
				}
				return;

			} else if (actionCode == EditorInfo.IME_ACTION_PREVIOUS) {
				View v = focusSearch(FOCUS_BACKWARD);
				if (v != null) {
					if (!v.requestFocus(FOCUS_BACKWARD)) {
						throw new IllegalStateException("focus search returned a view "
						                                + "that wasn't able to take focus!");
					}
				}
				return;

			} else if (actionCode == EditorInfo.IME_ACTION_DONE) {
				InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
				if (imm != null && imm.isActive(this)) {
					imm.hideSoftInputFromWindow(getWindowToken(), 0);
				}
				return;
			}
		}

		long eventTime = SystemClock.uptimeMillis();
		ATLInput.dispatchKeyFromIme(this, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0,
		                                               KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
		                                               KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE | KeyEvent.FLAG_EDITOR_ACTION));
		ATLInput.dispatchKeyFromIme(this, new KeyEvent(SystemClock.uptimeMillis(), eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0,
		                                               KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
		                                               KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE | KeyEvent.FLAG_EDITOR_ACTION));
	}

	public TransformationMethod getTransformationMethod() {
		return transformation;
	}

	public void setHintTextColor(ColorStateList colorStateList) {}
	public void setHintTextColor(int i) {}
	public void setLinkTextColor(ColorStateList colors) {
		link_colors = colors;
		sendText();
	}

	public final ColorStateList getLinkTextColors() {
		return link_colors;
	}

	public void setSingleLine() {
		setSingleLine(true);
	}
	public void setSelection(int i) {}
	public void setSelection(int i, int j) {}

	public int getSelectionStart() {
		return Selection.getSelectionStart(getText());
	}

	public int getSelectionEnd() {
		return Selection.getSelectionEnd(getText());
	}

	public void setEllipsize(TextUtils.TruncateAt truncateAt) {}

	public void setTextAppearance(Context context, int appearance) {}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setMaxLines(int maxLines) {
		maximum = maxLines;
		maxMode = LINES;
		linesChanged();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setMaxHeight(int maxPixels) {
		maximum = maxPixels;
		maxMode = PIXELS;
		linesChanged();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setMinLines(int minLines) {
		minimum = minLines;
		minMode = LINES;
		linesChanged();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setMinHeight(int minPixels) {
		minimum = minPixels;
		minMode = PIXELS;
		linesChanged();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setLines(int lines) {
		maximum = minimum = lines;
		maxMode = minMode = LINES;
		linesChanged();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setHeight(int pixels) {
		maximum = minimum = pixels;
		maxMode = minMode = PIXELS;
		linesChanged();
	}

	public int getMaxLines() {
		return maxMode == LINES ? maximum : -1;
	}

	public int getMaxHeight() {
		return maxMode == PIXELS ? maximum : -1;
	}

	public int getMinLines() {
		return minMode == LINES ? minimum : -1;
	}

	public int getMinHeight() {
		return minMode == PIXELS ? minimum : -1;
	}

	// the toolkit knows its line height: line counts go to it, pixels apply in onMeasure
	private void linesChanged() {
		native_setLines(minMode == LINES ? minimum : 0, maxMode == LINES && maximum != Integer.MAX_VALUE ? maximum : -1);
		requestLayout();
		invalidate();
	}
	private native void native_setLines(int min, int max);

	/* Copyright (C) 2006 The Android Open Source Project */
	// applySingleLine without the transformation and horizontal scrolling
	void applySingleLine(boolean singleLine) {
		single_line = singleLine;
		if (singleLine)
			setLines(1);
		else
			setMaxLines(Integer.MAX_VALUE);
	}

	// the toolkit's size, within maxHeight and minHeight, then at least the suggested minimum
	// (AOSP's getDesiredHeight order: a background's minimum wins over maxHeight)
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int height = getMeasuredHeight();
		if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
			if (maxMode == PIXELS)
				height = Math.min(height, maximum);
			if (minMode == PIXELS)
				height = Math.max(height, minimum);
		}
		setMeasuredDimension(atlAtLeast(getMeasuredWidth(), getSuggestedMinimumWidth(), widthMeasureSpec),
		                     atlAtLeast(height, getSuggestedMinimumHeight(), heightMeasureSpec));
	}

	public void setMinWidth(int minWidth) {}
	public void setMaxWidth(int maxWidth) {}

	public Typeface getTypeface() {
		return paint.getTypeface();
	}

	public float getTextSize() {
		return paint.getTextSize();
	}

	public int getGravity() {
		return gravity;
	}

	// AOSP's, with no drawable padding: setCompoundDrawablePadding keeps none
	public int getCompoundPaddingTop() {
		return getPaddingTop() + (drawableTop != null ? drawableTop.getBounds().height() : 0);
	}

	public int getCompoundPaddingBottom() {
		return getPaddingBottom() + (drawableBottom != null ? drawableBottom.getBounds().height() : 0);
	}

	public CharSequence getText() {
		return text;
	};

	public void setCompoundDrawablePadding(int pad) {}

	private native void native_setCompoundDrawables(long widget, long left, long top, long right, long bottom);

	// kept from the GC while the native side draws them; their bounds pad the text
	private Drawable drawableLeft = null;
	private Drawable drawableTop = null;
	private Drawable drawableRight = null;
	private Drawable drawableBottom = null;

	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		native_setCompoundDrawables(widget,
		                            left != null ? left.paintable : 0,
		                            top != null ? top.paintable : 0,
		                            right != null ? right.paintable : 0,
		                            bottom != null ? bottom.paintable : 0);
		drawableLeft = left;
		drawableTop = top;
		drawableRight = right;
		drawableBottom = bottom;
	}

	public void setAllCaps(boolean allCaps) {
		this.allCaps = allCaps;
		sendText();
	}

	// AllCapsTransformationMethod's upper case, which getText doesn't see; runs stay when it keeps the length
	private String shown() {
		String string = text.toString();
		return allCaps ? string.toUpperCase(Locale.getDefault()) : string;
	}

	public void setSaveEnabled(boolean enabled) {}

	public final void setAutoLinkMask(int mask) {}

	public void setEditableFactory(Editable.Factory factory) {}

	public KeyListener getKeyListener() { return null; }

	public int getInputType() { return 0; }

	public final void setTransformationMethod(TransformationMethod method) {
		transformation = method;
		transformationChanged();
	}

	// the toolkit shows the text as it is; an EditText's field hides a password's
	void transformationChanged() {}

	public InputFilter[] getFilters() { return new InputFilter[0]; }

	public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end, Drawable bottom) {
		setCompoundDrawables(start, top, end, bottom);
	}

	// AOSP has no layout before the first measure
	public int getLineCount() {
		return getWidth() == 0 && getMeasuredWidth() == 0 ? 0 : getLayout().getLineCount();
	}

	public URLSpan[] getUrls() { return new URLSpan[0]; }

	public void setMovementMethod(MovementMethod method) {}

	public void setTextIsSelectable(boolean selectable) {
		this.selectable = selectable;
		native_setSelectable(selectable);
	}
	private native void native_setSelectable(boolean selectable);

	public MovementMethod getMovementMethod() {
		return new BaseMovementMethod();
	}

	public CharSequence getHint() {
		return hint;
	}

	public int getMinWidth() { return 0; }

	public void setHorizontallyScrolling(boolean whether) {}
	public boolean getHorizontallyScrolling() {
		return false;
	}

	public static interface OnEditorActionListener {
		public abstract boolean onEditorAction(TextView v, int actionId, KeyEvent event);
	}

	public static enum BufferType {
		EDITABLE,
		NORMAL,
		SPANNABLE,
	}

	// inside the compound padding; before layout at the measured width, as AOSP builds it in onMeasure
	public Layout getLayout() {
		int width = Math.max(0, (getWidth() != 0 ? getWidth() : getMeasuredWidth()) - getCompoundPaddingLeft() - getCompoundPaddingRight());
		if (layout == null || layout_text_version != text_version || layout_width != width
		    || layout_text_size != paint.getTextSize() || layout_typeface != paint.getTypeface()) {
			layout = new BoringLayout(getText(), paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, new BoringLayout.Metrics(), false);
			layout_text_version = text_version;
			layout_width = width;
			layout_text_size = paint.getTextSize();
			layout_typeface = paint.getTypeface();
		}
		return layout;
	}

	public int getCurrentTextColor() {
		return current_text_color;
	}

	public void setSingleLine(boolean singleLine) {
		applySingleLine(singleLine);
	}

	public int getCompoundPaddingLeft() {
		return getPaddingLeft() + (drawableLeft != null ? drawableLeft.getBounds().width() : 0);
	}

	public int getCompoundPaddingRight() {
		return getPaddingRight() + (drawableRight != null ? drawableRight.getBounds().width() : 0);
	}

	public void setHint(int resId) {
		setHint(getContext().getResources().getText(resId));
	}

	public float getLetterSpacing() { return 0.f; }

	public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {}

	public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable start, Drawable top, Drawable end, Drawable bottom) {}

	public boolean getLinksClickable() { return true; }

	public boolean isTextSelectable() {
		return selectable;
	}

	public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {}

	public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {}

	public void setHint(CharSequence hint) {
		this.hint = hint;
	}

	public Drawable[] getCompoundDrawablesRelative() {
		return getCompoundDrawables();
	}

	public Drawable[] getCompoundDrawables() {
		return new Drawable[] {drawableLeft, drawableTop, drawableRight, drawableBottom};
	}

	public void setTextAppearance(int dummy) {}

	public int length() {
		return getText().length();
	}

	public void setHighlightColor(int color) {}

	public Editable getEditableText() {
		return new SpannableStringBuilder(getText());
	}

	public int getMaxWidth() { return 1000; }

	public void nullLayouts() {}

	public void setLinkTextColor(int color) {
		setLinkTextColor(ColorStateList.valueOf(color));
	}

	public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	public int getExtendedPaddingTop() { return 0; }

	public void setRawInputType(int type) {}

	public TextUtils.TruncateAt getEllipsize() { return null; }


	public void setSelectAllOnFocus(boolean selectAllOnFocus) {}

	public int getCompoundDrawablePadding() { return 0; }

	public int getPaintFlags() { return 0; }

	public void setPaintFlags(int flags) {}

	/* Copyright (C) 2006 The Android Open Source Project */
	public int getLineHeight() {
		return paint.getFontMetricsInt(null);
	}

	public boolean isAllCaps() { return false; }

	public int getAutoSizeStepGranularity() {
		return -1;
	}

	public void setAutoSizeTextTypeUniformWithPresetSizes(int[] presetSizes, int unit) {}

	public void setCompoundDrawableTintList(ColorStateList tint) {}

	public int getHyphenationFrequency() {
		return hyphenation_frequency;
	}

	public void setHyphenationFrequency(int hyphenationFrequency) {
		hyphenation_frequency = hyphenationFrequency;
	}

	public boolean getIncludeFontPadding() { return include_padding; }

	public void setIncludeFontPadding(boolean includePadding) {
		include_padding = includePadding;
	}

	public float getLineSpacingExtra() { return 0.f; }

	public float getLineSpacingMultiplier() { return 1.f; }

	public TextDirectionHeuristic getTextDirectionHeuristic() {
		return TextDirectionHeuristics.LTR;
	}

	public Bundle getInputExtras(boolean key) {
		return new Bundle();
	}

	public void setError(CharSequence error) {
		System.out.println("ERROR: " + error);
	}

	public int getTotalPaddingLeft() { return 0; }

	public int getTotalPaddingTop() { return 0; }

	public int getTotalPaddingRight() { return 0; }

	public int getTotalPaddingBottom() { return 0; }

	public int getImeOptions() { return 0; }

	public void setShadowLayer(float radius, float dx, float dy, int color) {}

	public int getBreakStrategy() {
		return break_strategy;
	}

	public void setBreakStrategy(int strategy) {
		break_strategy = strategy;
	}

	public void clearComposingText() {}

	public void setKeyListener(KeyListener keyListener) {}

	public int getAutoLinkMask() { return 0; }

	public void setWidth(int width) {}

	public void setFreezesText(boolean freezesText) {}

	public void setLetterSpacing(float letterSpacing) {}

	public void setMarqueeRepeatLimit(int marqueeLimit) {}

	public void setShowSoftInputOnFocus(boolean showSoftInputOnFocus) {}

	public void setCustomInsertionActionModeCallback(ActionMode.Callback actionModeCallback) {}

	public int getJustificationMode() {
		return 0; // JUSTIFICATION_MODE_NONE
	}

	public void setImeActionLabel(CharSequence label, int actionId) {}

	public int getExtendedPaddingBottom() { return 0; }

	public boolean hasSelection() {
		int start = getSelectionStart();
		int end = getSelectionEnd();
		return start >= 0 && end > 0 && start != end;
	}

	@UnsupportedAppUsage /* androidx ACTVAutoSizeHelper seems to love this */
	/* Copyright (C) 2006 The Android Open Source Project */
	private Layout.Alignment getLayoutAlignment() {
		Layout.Alignment alignment;
		switch (getTextAlignment()) {
			case TEXT_ALIGNMENT_GRAVITY:
				switch (gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
					case Gravity.START:
						alignment = Layout.Alignment.ALIGN_NORMAL;
						break;
					case Gravity.END:
						alignment = Layout.Alignment.ALIGN_OPPOSITE;
						break;
					case Gravity.LEFT:
						alignment = Layout.Alignment.ALIGN_LEFT;
						break;
					case Gravity.RIGHT:
						alignment = Layout.Alignment.ALIGN_RIGHT;
						break;
					case Gravity.CENTER_HORIZONTAL:
						alignment = Layout.Alignment.ALIGN_CENTER;
						break;
					default:
						alignment = Layout.Alignment.ALIGN_NORMAL;
						break;
				}
				break;
			case TEXT_ALIGNMENT_TEXT_START:
				alignment = Layout.Alignment.ALIGN_NORMAL;
				break;
			case TEXT_ALIGNMENT_TEXT_END:
				alignment = Layout.Alignment.ALIGN_OPPOSITE;
				break;
			case TEXT_ALIGNMENT_CENTER:
				alignment = Layout.Alignment.ALIGN_CENTER;
				break;
			case TEXT_ALIGNMENT_VIEW_START:
				alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
				break;
			case TEXT_ALIGNMENT_VIEW_END:
				alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
				break;
			case TEXT_ALIGNMENT_INHERIT:
				// This should never happen as we have already resolved the text alignment
				// but better safe than sorry so we just fall through
			default:
				alignment = Layout.Alignment.ALIGN_NORMAL;
				break;
		}
		return alignment;
	}
}
