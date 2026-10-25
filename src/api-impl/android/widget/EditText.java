/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/widget/EditText.java and TextView.java at android-security-10.0.0_r75,
 * Apache License 2.0; see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;

/* The Editable is the truth: the toolkit's field applies the user's edits and reports them
 * (atlEdited), Java answers with a snapshot when its state differs. Fields have no initializers:
 * TextView's constructor calls setText and setHint before they would run. */
public class EditText extends TextView {
	private static final int CHANGE_WATCHER_PRIORITY = 100;
	private static final Spanned EMPTY_SPANNED = new SpannedString("");

	// ui_keyboard and ui_return_key
	private static final int KEYBOARD_TEXT = 0, KEYBOARD_NUMBER = 1, KEYBOARD_DECIMAL = 2, KEYBOARD_PHONE = 3, KEYBOARD_EMAIL = 4, KEYBOARD_URL = 5;
	private static final int RETURN_DEFAULT = 0, RETURN_DONE = 1, RETURN_GO = 2, RETURN_SEARCH = 3, RETURN_NEXT = 4, RETURN_SEND = 5;

	private ArrayList<TextWatcher> listeners;
	private ChangeWatcher changeWatcher;
	private InputFilter[] filters;
	private KeyListener keyListener;
	private int inputType;
	private int imeOptions;
	private boolean showSoftInputOnFocus;
	private boolean enterDown;

	// what the toolkit shows as far as Java knows: its last report, or the last snapshot; null: a
	// new field's empty text
	private String toolkitText;
	private int toolkitStart, toolkitEnd;
	private int ackSeq;
	private boolean applyingReport;
	private boolean syncPosted;

	public EditText(Context context) {
		this(context, null);
	}

	public EditText(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EditText(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public EditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		showSoftInputOnFocus = true;
		byDefault(context, attrs, defStyleAttr, defStyleRes);
		readEditorAttributes(context, attrs, defStyleAttr);
		getText();
		atlSendConfig();
	}

	/* Android's editTextStyle sets clickable, focusable and focusableInTouchMode, each overridden by
	 * the app's own attribute; ATL's EditText doesn't take that style */
	private void byDefault(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.View, defStyle, defStyleRes);
		if (!a.hasValue(com.android.internal.R.styleable.View_clickable))
			setClickable(true);
		if (!a.hasValue(com.android.internal.R.styleable.View_focusableInTouchMode))
			setFocusableInTouchMode(true);
		else if (!a.hasValue(com.android.internal.R.styleable.View_focusable))
			setFocusable(FOCUSABLE);
		a.recycle();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void readEditorAttributes(Context context, AttributeSet attrs, int defStyleAttr) {
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.TextView, defStyleAttr, 0);
		int type = a.getInt(com.android.internal.R.styleable.TextView_inputType, EditorInfo.TYPE_NULL);
		boolean singleLine = a.getBoolean(com.android.internal.R.styleable.TextView_singleLine, false);
		boolean password = a.getBoolean(com.android.internal.R.styleable.TextView_password, false);
		int maxLength = a.getInt(com.android.internal.R.styleable.TextView_maxLength, -1);
		CharSequence digits = a.getText(com.android.internal.R.styleable.TextView_digits);
		if (a.hasValue(com.android.internal.R.styleable.TextView_imeOptions))
			setImeOptions(a.getInt(com.android.internal.R.styleable.TextView_imeOptions, EditorInfo.IME_NULL));
		a.recycle();

		if (type != EditorInfo.TYPE_NULL) {
			inputType = type;
			// If set, the input type overrides what was set using the deprecated singleLine flag.
			singleLine = !isMultilineInputType(type);
		} else {
			inputType = EditorInfo.TYPE_CLASS_TEXT;
		}
		// Editor.adjustInputType
		if ((inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT && password)
			inputType = (inputType & ~EditorInfo.TYPE_MASK_VARIATION) | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
		if (password || isPasswordInputType(inputType))
			setTransformationMethod(PasswordTransformationMethod.getInstance());
		if (maxLength >= 0)
			setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
		setKeyListenerOnly(digits != null ? DigitsKeyListener.getInstance(digits.toString()) : keyListenerFor(inputType));
		setInputTypeSingleLine(singleLine);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	private native void native_setConfig(int keyboard, int returnKey, int autocap, boolean secure, boolean multiline, boolean autocorrect, String hint);
	private native void native_setState(String text, int start, int end, int ack);
	private native boolean native_setKeyboard(boolean shown);

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public Editable getText() {
		CharSequence text = super.getText();
		if (text instanceof Editable)
			return (Editable)text;
		setText(text, BufferType.EDITABLE);
		return (Editable)super.getText();
	}

	@Override
	public Editable getEditableText() {
		return getText();
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public void setText(CharSequence text, BufferType type) {
		if (text == null)
			text = "";
		InputFilter[] filters = getFilters();
		for (int i = 0; i < filters.length; i++) {
			CharSequence out = filters[i].filter(text, 0, text.length(), EMPTY_SPANNED, 0, 0);
			if (out != null)
				text = out;
		}

		CharSequence old = super.getText();
		int oldlen = old.length();
		sendBeforeTextChanged(old, 0, oldlen, text.length());

		Editable t = Editable.Factory.getInstance().newEditable(text);
		t.setFilters(editableFilters());
		// ATL: the old Editable stops reporting, so an app's stale reference doesn't drive this view
		if (old instanceof Spannable && changeWatcher != null)
			((Spannable)old).removeSpan(changeWatcher);
		super.setText(t, BufferType.EDITABLE);

		final ChangeWatcher[] watchers = t.getSpans(0, t.length(), ChangeWatcher.class);
		for (int i = 0; i < watchers.length; i++)
			t.removeSpan(watchers[i]);
		if (changeWatcher == null)
			changeWatcher = new ChangeWatcher();
		t.setSpan(changeWatcher, 0, t.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE | (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));
		// ArrowKeyMovementMethod.initialize
		Selection.setSelection(t, 0);

		sendOnTextChanged(t, 0, oldlen, t.length());
		sendAfterTextChanged(t);
		changed();
	}

	public void setSelection(int start, int stop) {
		Selection.setSelection(getText(), start, stop);
	}

	public void setSelection(int index) {
		Selection.setSelection(getText(), index);
	}

	public void selectAll() {
		Selection.selectAll(getText());
	}

	public void extendSelection(int index) {
		Selection.extendSelection(getText(), index);
	}

	@Override
	public void append(CharSequence text, int start, int end) {
		getText().append(text, start, end);
	}

	@Override
	public void addTextChangedListener(TextWatcher watcher) {
		if (listeners == null)
			listeners = new ArrayList<TextWatcher>();
		listeners.add(watcher);
	}

	@Override
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

	private class ChangeWatcher implements TextWatcher, SpanWatcher {
		public void beforeTextChanged(CharSequence buffer, int start, int before, int after) {
			sendBeforeTextChanged(buffer, start, before, after);
		}

		public void onTextChanged(CharSequence buffer, int start, int before, int after) {
			sendOnTextChanged(buffer, start, before, after);
		}

		public void afterTextChanged(Editable buffer) {
			sendAfterTextChanged(buffer);
			changed();
		}

		public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
			if (what == Selection.SELECTION_START || what == Selection.SELECTION_END)
				changed();
		}

		public void onSpanAdded(Spannable buf, Object what, int s, int e) {
			onSpanChanged(buf, what, -1, s, -1, e);
		}

		public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
			onSpanChanged(buf, what, s, -1, e, -1);
		}
	}

	@Override
	public void setFilters(InputFilter[] filters) {
		if (filters == null)
			throw new IllegalArgumentException();
		this.filters = filters;
		CharSequence text = super.getText();
		if (text instanceof Editable)
			((Editable)text).setFilters(editableFilters());
	}

	@Override
	public InputFilter[] getFilters() {
		return filters != null ? filters : new InputFilter[0];
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	// TextView.setFilters(Editable, InputFilter[]): the key listener filters after the app's own
	// filters, on the Editable only, so setText doesn't run it
	private InputFilter[] editableFilters() {
		InputFilter[] filters = getFilters();
		if (!(keyListener instanceof InputFilter))
			return filters;
		InputFilter[] all = new InputFilter[filters.length + 1];
		System.arraycopy(filters, 0, all, 0, filters.length);
		all[filters.length] = (InputFilter)keyListener;
		return all;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	// TextView.setInputType's choice; text fields get none (no TextKeyListener)
	private static KeyListener keyListenerFor(int type) {
		switch (type & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
			return DigitsKeyListener.getInstance(null, (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0, (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
		case EditorInfo.TYPE_CLASS_PHONE:
			return DialerKeyListener.getInstance();
		default:
			return null;
		}
	}

	private void setKeyListenerOnly(KeyListener input) {
		keyListener = input;
		CharSequence text = super.getText();
		if (text instanceof Editable)
			((Editable)text).setFilters(editableFilters());
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public void setKeyListener(KeyListener input) {
		boolean singleLine = !isMultilineInputType(inputType);
		setKeyListenerOnly(input);
		if (input != null) {
			try {
				inputType = input.getInputType();
			} catch (IncompatibleClassChangeError e) {
				inputType = EditorInfo.TYPE_CLASS_TEXT;
			}
			setInputTypeSingleLine(singleLine);
		} else {
			inputType = EditorInfo.TYPE_NULL;
		}
		atlSendConfig();
	}

	@Override
	public KeyListener getKeyListener() {
		return keyListener;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	static boolean isMultilineInputType(int type) {
		return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
		    == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	static boolean isPasswordInputType(int inputType) {
		final int variation = inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
		return variation == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
		    || variation == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
		    || variation == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private void setInputTypeSingleLine(boolean singleLine) {
		if ((inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			if (singleLine) {
				inputType &= ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
			} else {
				inputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
			}
		}
	}

	@Override
	public void setInputType(int type) {
		boolean wasPassword = isPasswordInputType(inputType);
		boolean wasMultiline = isMultilineInputType(inputType);
		inputType = type;
		setKeyListenerOnly(keyListenerFor(type));
		if (isMultilineInputType(type) != wasMultiline)
			applySingleLine(!isMultilineInputType(type));
		if (isPasswordInputType(type))
			setTransformationMethod(PasswordTransformationMethod.getInstance());
		else if (wasPassword && getTransformationMethod() == PasswordTransformationMethod.getInstance())
			setTransformationMethod(null);
		atlSendConfig();
	}

	@Override
	public void setRawInputType(int type) {
		inputType = type;
		atlSendConfig();
	}

	@Override
	public int getInputType() {
		return inputType;
	}

	@Override
	public void setSingleLine() {
		setSingleLine(true);
	}

	@Override
	public void setSingleLine(boolean singleLine) {
		setInputTypeSingleLine(singleLine);
		applySingleLine(singleLine);
		atlSendConfig();
	}

	@Override
	public void setImeOptions(int imeOptions) {
		this.imeOptions = imeOptions;
		inputContentType = true;
		atlSendConfig();
	}

	@Override
	public int getImeOptions() {
		return imeOptions;
	}

	@Override
	public void setHint(CharSequence hint) {
		super.setHint(hint);
		atlSendConfig();
	}

	@Override
	void transformationChanged() {
		atlSendConfig();
	}

	@Override
	public void setShowSoftInputOnFocus(boolean show) {
		showSoftInputOnFocus = show;
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return inputType != EditorInfo.TYPE_NULL;
	}

	// TextView.onCreateInputConnection's default action for a field without one
	private int imeAction() {
		int action = imeOptions & EditorInfo.IME_MASK_ACTION;
		if (action == EditorInfo.IME_ACTION_UNSPECIFIED)
			action = focusSearch(FOCUS_DOWN) != null ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE;
		return action;
	}

	public void atlSendConfig() {
		int type = inputType;
		int klass = type & EditorInfo.TYPE_MASK_CLASS;
		int variation = type & EditorInfo.TYPE_MASK_VARIATION;
		boolean text = klass == EditorInfo.TYPE_CLASS_TEXT;
		boolean multiline = isMultilineInputType(type);
		boolean secure = isPasswordInputType(type) || getTransformationMethod() instanceof PasswordTransformationMethod;

		int keyboard = KEYBOARD_TEXT;
		if (klass == EditorInfo.TYPE_CLASS_NUMBER)
			keyboard = (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0 ? KEYBOARD_DECIMAL : KEYBOARD_NUMBER;
		else if (klass == EditorInfo.TYPE_CLASS_PHONE)
			keyboard = KEYBOARD_PHONE;
		else if (text && (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
			keyboard = KEYBOARD_EMAIL;
		else if (text && variation == EditorInfo.TYPE_TEXT_VARIATION_URI)
			keyboard = KEYBOARD_URL;

		int returnKey = RETURN_DEFAULT;
		if (!multiline && (imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
			switch (imeAction()) {
				case EditorInfo.IME_ACTION_GO: returnKey = RETURN_GO; break;
				case EditorInfo.IME_ACTION_SEARCH: returnKey = RETURN_SEARCH; break;
				case EditorInfo.IME_ACTION_SEND: returnKey = RETURN_SEND; break;
				case EditorInfo.IME_ACTION_NEXT: returnKey = RETURN_NEXT; break;
				case EditorInfo.IME_ACTION_DONE: returnKey = RETURN_DONE; break;
			}
		}

		// ui_edit_config's autocap: none, words, sentences, characters
		int autocap = 0;
		if (text && (type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
			autocap = 3;
		else if (text && (type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
			autocap = 2;
		else if (text && (type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0)
			autocap = 1;

		// Android's keyboards correct plain text unless the field says otherwise
		boolean autocorrect = text && !secure && (type & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
		                   && variation != EditorInfo.TYPE_TEXT_VARIATION_URI && keyboard != KEYBOARD_EMAIL
		                   && variation != EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
		                   && variation != EditorInfo.TYPE_TEXT_VARIATION_FILTER;

		CharSequence hint = getHint();
		native_setConfig(keyboard, returnKey, autocap, secure, multiline, autocorrect, hint != null ? hint.toString() : null);
	}

	// the field becomes or stops being the keyboard's; false: no keyboard shows on this toolkit
	public boolean atlSetKeyboard(boolean shown) {
		if (shown)
			atlSendConfig();
		return native_setKeyboard(shown);
	}

	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		if (gainFocus)
			InputMethodManager.atlFocusIn(this);
		else
			InputMethodManager.atlFocusOut(this);
	}

	// Editor.onTouchUpEvent: a tap on the focused field brings the keyboard
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean handled = super.onTouchEvent(event);
		if (event.getActionMasked() == MotionEvent.ACTION_UP && isFocused() && isEnabled() && showSoftInputOnFocus && onCheckIsTextEditor()) {
			InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(this, 0);
		}
		return handled;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	private boolean shouldAdvanceFocusOnEnter() {
		if (!isMultilineInputType(inputType))
			return true;
		if ((inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			int variation = inputType & EditorInfo.TYPE_MASK_VARIATION;
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
			    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
				return true;
			}
		}
		return false;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (isEnabled() && keyCode == KeyEvent.KEYCODE_ENTER && event.hasNoModifiers()) {
			// When mInputContentType is set, we know that we are
			// running in a "modern" cupcake environment, so don't need
			// to worry about the application trying to capture
			// enter key events.
			if (inputContentType && editorActionListener != null
			    && editorActionListener.onEditorAction(this, EditorInfo.IME_NULL, event)) {
				enterDown = true;
				// We are consuming the enter key for them.
				return true;
			}

			// If our editor should move focus when enter is pressed, or
			// this is a generated event from an IME action button, then
			// don't let it be inserted into the text.
			if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0 || shouldAdvanceFocusOnEnter()) {
				if (hasOnClickListeners())
					return super.onKeyDown(keyCode, event);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (isEnabled() && keyCode == KeyEvent.KEYCODE_ENTER && event.hasNoModifiers()) {
			if (inputContentType && editorActionListener != null && enterDown) {
				enterDown = false;
				if (editorActionListener.onEditorAction(this, EditorInfo.IME_NULL, event)) {
					return true;
				}
			}

			if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0 || shouldAdvanceFocusOnEnter()) {
				/*
				 * If there is a click listener, just call through to
				 * super, which will invoke it.
				 *
				 * If there isn't a click listener, try to advance focus,
				 * but still call through to super, which will reset the
				 * pressed state and longpress state.  (It will also
				 * call performClick(), but that won't do anything in
				 * this case.)
				 */
				if (!hasOnClickListeners()) {
					View v = focusSearch(FOCUS_DOWN);

					if (v != null) {
						if (!v.requestFocus(FOCUS_DOWN)) {
							throw new IllegalStateException("focus search returned a view "
							                                + "that wasn't able to take focus!");
						}

						/*
						 * Return true because we handled the key; super
						 * will return false because there was no click
						 * listener.
						 */
						super.onKeyUp(keyCode, event);
						return true;
					} else if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
						// No target for next focus, but make sure the IME
						// if this came from it.
						InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
						if (imm != null && imm.isActive(this)) {
							imm.hideSoftInputFromWindow(getWindowToken(), 0);
						}
					}
				}
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	// atl_upcall_edit_action: the toolkit's return key on a single-line field, as an IME's
	// performEditorAction
	public void atlEditorAction() {
		if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
			onEditorAction(imeAction());
			return;
		}
		long now = android.os.SystemClock.uptimeMillis();
		for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP})
			android.view.ATLInput.dispatchKeyFromIme(this, new KeyEvent(now, now, action, KeyEvent.KEYCODE_ENTER, 0, 0, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
			                                                            KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
	}

	// atl_upcall_edit_focus
	public void atlToolkitFocus(boolean focused, boolean keyboard) {
		InputMethodManager.atlToolkitFocus(this, focused, keyboard);
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	/* atl_upcall_edit_report. The change from prev, found around prev's selection, replaces Java's own
	 * selection widened alike: on a base Java has moved on from, it lands at the cursor (commitText). */
	public void atlEdited(String prev, int prevStart, int prevEnd, String text, int start, int end, int seq) {
		Editable editable = getText();
		ackSeq = seq;
		toolkitText = text;
		toolkitStart = start;
		toolkitEnd = end;

		int prevLen = prev.length(), newLen = text.length();
		int ps = clamp(Math.min(prevStart, prevEnd), 0, prevLen);
		int pe = clamp(Math.max(prevStart, prevEnd), ps, prevLen);
		int p = 0;
		while (p < ps && p < newLen && prev.charAt(p) == text.charAt(p))
			p++;
		if (p > 0 && Character.isHighSurrogate(text.charAt(p - 1)))
			p--;
		int s = 0;
		int suffixMax = Math.min(prevLen - pe, newLen - p);
		while (s < suffixMax && prev.charAt(prevLen - 1 - s) == text.charAt(newLen - 1 - s))
			s++;
		if (s > 0 && Character.isLowSurrogate(text.charAt(newLen - s)))
			s--;

		boolean textChanged = prevLen - s > p || newLen - s > p;
		applyingReport = true;
		try {
			int length = editable.length();
			if (textChanged) {
				int js = Selection.getSelectionStart(editable), je = Selection.getSelectionEnd(editable);
				if (js < 0 || je < 0)
					js = je = length;
				int before = ps - p, after = (prevLen - s) - pe;
				int st = clamp(Math.min(js, je) - before, 0, length);
				int en = clamp(Math.max(js, je) + after, st, length);
				editable.replace(st, en, text, p, newLen - s);
				int insertedEnd = st + editable.length() - (length - (en - st));
				Selection.setSelection(editable, mapped(start, p, newLen - s, st, insertedEnd, editable.length()),
				                       mapped(end, p, newLen - s, st, insertedEnd, editable.length()));
			} else {
				Selection.setSelection(editable, clamp(start, 0, length), clamp(end, 0, length));
			}
		} finally {
			applyingReport = false;
		}
		if (textChanged)
			requestLayout();
		sync();
	}

	// a toolkit position after its change [from, to) to Java's, whose change is [st, insertedEnd)
	private static int mapped(int position, int from, int to, int st, int insertedEnd, int length) {
		if (position <= from)
			return clamp(st - (from - position), 0, length);
		if (position >= to)
			return clamp(insertedEnd + (position - to), 0, length);
		return Math.min(st + (position - from), insertedEnd);
	}

	// the Editable or its selection changed: one snapshot per message
	private void changed() {
		if (applyingReport || syncPosted)
			return;
		syncPosted = true;
		post(new Runnable() {
			@Override
			public void run() {
				sync();
			}
		});
	}

	private void sync() {
		syncPosted = false;
		Editable editable = getText();
		String text = editable.toString();
		int start = Selection.getSelectionStart(editable), end = Selection.getSelectionEnd(editable);
		if (start < 0 || end < 0)
			start = end = text.length();
		if (text.equals(toolkitText != null ? toolkitText : "") && start == toolkitStart && end == toolkitEnd)
			return;
		toolkitText = text;
		toolkitStart = start;
		toolkitEnd = end;
		native_setState(text, start, end, ackSeq);
	}
}
