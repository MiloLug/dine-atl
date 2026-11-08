/*
 * Portions Copyright (C) 2008 The Android Open Source Project, from
 * core/java/android/app/AlertDialog.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/* The ui slot draws the alert: a plain one (title, message, buttons, setItems) by itself, any other
 * as a panel around the window's decor view, which holds the Android content (setView, setAdapter,
 * setCustomTitle). String choice lists are the alert's own rows; getListView's ListView then keeps
 * their state undrawn. */
public class AlertDialog extends Dialog implements DialogInterface {
	// ui_alert_check
	private static final int CHECK_NONE = 0;
	private static final int CHECK_SINGLE = 1;
	private static final int CHECK_MULTI = 2;
	// ui_alert_progress
	static final int PROGRESS_NONE = 0;
	static final int PROGRESS_SPINNER = 1;
	static final int PROGRESS_BAR = 2;

	private View view; // setView's, installed at show
	private int spacingLeft, spacingTop, spacingRight, spacingBottom;
	private View customTitle;
	private Drawable icon;

	private CharSequence message;
	private CharSequence[] items;       // the alert's own rows
	private ListAdapter adapter;        // rows Android draws: setAdapter, a ListAdapter choice list
	private OnClickListener itemsListener;
	private OnMultiChoiceClickListener multiListener;
	private AdapterView.OnItemSelectedListener itemSelectedListener;
	private int check = CHECK_NONE;
	private int checkedItem = -1;
	private boolean[] checked;
	private ListView listView; // getListView's
	// positive, negative, neutral: index -1 - DialogInterface.BUTTON_*
	private final CharSequence[] buttonLabels = new CharSequence[3];
	private final OnClickListener[] buttonListeners = new OnClickListener[3];
	private final AlertButton[] buttons = new AlertButton[3]; // getButton's, made on demand

	private native void nativeShowAlert(long ptr, String title, String message, String[] items, int check, boolean[] checked,
	                                    int progress, boolean indeterminate, int value, int max, boolean panel, boolean content,
	                                    boolean light, String[] buttons, boolean[] disabled, boolean cancelable);
	private native void nativeSetButton(long ptr, int which, String label, boolean enabled);
	native void nativeSetProgress(long ptr, int value, int max, boolean indeterminate, boolean relayout);

	public AlertDialog(Context context) {
		this(context, 0);
	}

	public AlertDialog(Context context, int themeResId) {
		super(context, resolveDialogTheme(context, themeResId));
	}

	/* Copyright (C) 2008 The Android Open Source Project: a resource id as is, else the theme's
	 * alertDialogTheme; without the THEME_* constants */
	static int resolveDialogTheme(Context context, int themeResId) {
		if ((themeResId >>> 24) >= 1)
			return themeResId;
		TypedValue outValue = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.alertDialogTheme, outValue, true);
		return outValue.resourceId;
	}

	@Override
	public void setTitle(CharSequence title) {
		super.setTitle(title);
		changed();
	}

	public void setCustomTitle(View customTitleView) {
		customTitle = customTitleView;
	}

	public void setMessage(CharSequence message) {
		this.message = message;
		changed();
	}

	public void setIcon(int resId) {
		icon = resId != 0 ? getContext().getDrawable(resId) : null;
	}

	public void setIcon(Drawable icon) {
		this.icon = icon;
	}

	public void setIconAttribute(int attrId) {
		TypedValue out = new TypedValue();
		getContext().getTheme().resolveAttribute(attrId, out, true);
		setIcon(out.resourceId);
	}

	public void setInverseBackground(boolean useInverseBackground) {}

	public void setButton(int whichButton, CharSequence text, OnClickListener listener) {
		buttonLabels[-1 - whichButton] = text;
		buttonListeners[-1 - whichButton] = listener;
		AlertButton button = buttons[-1 - whichButton];
		if (button != null) {
			button.setText(text);
			button.setVisibility(text != null ? View.VISIBLE : View.GONE);
		}
	}

	/* AOSP's AlertController buttons: views outside any window whose own click listener is the
	 * dialog's listener, then dismiss; an app's setOnClickListener replaces that. The ui backend
	 * draws the alert from their label, visibility and enabled state. */
	public Button getButton(int whichButton) {
		int i = -1 - whichButton;
		if (i < 0 || i >= buttons.length)
			return null;
		if (buttons[i] == null)
			buttons[i] = new AlertButton(this, whichButton);
		return buttons[i];
	}

	private static class AlertButton extends Button {
		private final AlertDialog dialog;
		private final int which;
		private boolean bound; // Button's constructor sets state before which is known

		AlertButton(final AlertDialog dialog, final int which) {
			super(dialog.getContext());
			this.dialog = dialog;
			this.which = which;
			CharSequence label = dialog.buttonLabels[-1 - which];
			setText(label);
			setVisibility(label != null ? View.VISIBLE : View.GONE);
			setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					dialog.click(which);
				}
			});
			bound = true;
		}

		boolean shown() {
			return getVisibility() == View.VISIBLE;
		}

		@Override
		public void setText(CharSequence text, TextView.BufferType type) {
			super.setText(text, type);
			changed();
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			changed();
		}

		@Override
		public void setVisibility(int visibility) {
			super.setVisibility(visibility);
			changed();
		}

		private void changed() {
			if (bound && dialog.drawn())
				dialog.nativeSetButton(dialog.nativePtr, which, dialog.label(-1 - which), isEnabled());
		}
	}

	private String label(int i) {
		AlertButton button = buttons[i];
		if (button == null)
			return string(buttonLabels[i]);
		return button.shown() ? button.getText().toString() : null;
	}

	private void click(int which) {
		OnClickListener listener = buttonListeners[-1 - which];
		if (listener != null)
			listener.onClick(this, which);
		dismiss();
	}

	public void setView(View view) {
		this.view = view;
	}

	public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight, int viewSpacingBottom) {
		this.view = view;
		spacingLeft = viewSpacingLeft;
		spacingTop = viewSpacingTop;
		spacingRight = viewSpacingRight;
		spacingBottom = viewSpacingBottom;
	}

	/* The list of the alert's rows, or the one Android draws (setAdapter); null without rows. */
	public ListView getListView() {
		if (listView == null && items != null) {
			int layout = check == CHECK_MULTI ? android.R.layout.simple_list_item_multiple_choice : android.R.layout.simple_list_item_single_choice;
			listView = new ListView(getContext());
			listView.setAdapter(new ArrayAdapter<CharSequence>(getContext(), layout, items));
			listView.setChoiceMode(check == CHECK_MULTI ? ListView.CHOICE_MODE_MULTIPLE : check == CHECK_SINGLE ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
			for (int i = 0; i < items.length; i++) {
				if (isChecked(i))
					listView.setItemChecked(i, true);
			}
		}
		return listView;
	}

	private boolean isChecked(int item) {
		return check == CHECK_SINGLE ? item == checkedItem : check == CHECK_MULTI && checked != null && item < checked.length && checked[item];
	}

	void setItems(CharSequence[] items, OnClickListener listener) {
		this.items = items;
		this.itemsListener = listener;
	}

	void setSingleChoiceItems(CharSequence[] items, int checkedItem, OnClickListener listener) {
		setItems(items, listener);
		check = CHECK_SINGLE;
		this.checkedItem = checkedItem;
	}

	void setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, OnMultiChoiceClickListener listener) {
		this.items = items;
		multiListener = listener;
		check = CHECK_MULTI;
		checked = new boolean[items.length];
		if (checkedItems != null)
			System.arraycopy(checkedItems, 0, checked, 0, Math.min(checkedItems.length, items.length));
	}

	void setAdapter(ListAdapter adapter, int checkedItem, boolean choice, OnClickListener listener) {
		this.adapter = adapter;
		itemsListener = listener;
		check = choice ? CHECK_SINGLE : CHECK_NONE;
		this.checkedItem = checkedItem;
	}

	// ProgressDialog's: its native progress
	int atlProgressStyle() {
		return PROGRESS_NONE;
	}

	boolean atlIndeterminate() {
		return false;
	}

	int atlProgressValue() {
		return 0;
	}

	int atlProgressMax() {
		return 0;
	}

	// a panel around Android content, or one the plain system alert can't show (checks, progress)
	private boolean panel() {
		return content() || check != CHECK_NONE || atlProgressStyle() != PROGRESS_NONE;
	}

	private boolean content() {
		return view != null || customTitle != null || adapter != null;
	}

	// AOSP installs the content in the first onCreate only: a later setView doesn't show
	private Boolean firstPanel; // the first show's panel(), null before it

	@Override
	protected boolean atlHasRoot() {
		return firstPanel != null ? firstPanel : panel();
	}

	@Override
	protected long atlChrome() {
		return nativePtr;
	}

	/* Copyright (C) 2008 The Android Open Source Project: AlertController installs the content in
	 * onCreate; the panel's chrome takes the place of the theme's window background */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		firstPanel = panel();
		if (!content())
			return;
		LinearLayout content = new LinearLayout(getContext());
		content.setOrientation(LinearLayout.VERTICAL);
		if (customTitle != null) {
			content.addView(customTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			if (message != null) { // under the custom title, as AlertController's
				TextView shown = new TextView(getContext());
				shown.setText(message);
				content.addView(shown, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			}
		}
		if (adapter != null)
			content.addView(adapterList(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (view != null) {
			FrameLayout spaced = new FrameLayout(getContext());
			spaced.setPadding(spacingLeft, spacingTop, spacingRight, spacingBottom);
			spaced.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			content.addView(spaced, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		setContentView(content);
		View decor = getWindow().getDecorView();
		decor.setBackground(null);
		decor.setPadding(0, 0, 0, 0);
	}

	private ListView adapterList() {
		final ListView list = listView = new ListView(getContext());
		list.setAdapter(adapter);
		if (check == CHECK_SINGLE) {
			list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			if (checkedItem >= 0)
				list.setItemChecked(checkedItem, true);
		}
		if (itemSelectedListener != null)
			list.setOnItemSelectedListener(itemSelectedListener);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				if (itemsListener != null)
					itemsListener.onClick(AlertDialog.this, position);
				if (check == CHECK_NONE)
					dismiss();
			}
		});
		return list;
	}

	@Override
	protected boolean atlNativeAlert() {
		return true;
	}

	// the theme the content draws in: the panel takes its lightness
	private boolean light() {
		TypedArray a = getContext().obtainStyledAttributes(new int[] {com.android.internal.R.attr.isLightTheme});
		boolean light = a.getBoolean(0, false);
		a.recycle();
		return light;
	}

	// a drawn alert shows its new title or message
	private void changed() {
		if (drawn())
			showNative();
	}

	@Override
	protected void showNative() {
		String[] labels = new String[buttons.length];
		boolean[] disabled = new boolean[buttons.length];
		for (int i = 0; i < buttons.length; i++) {
			labels[i] = label(i);
			disabled[i] = buttons[i] != null && !buttons[i].isEnabled();
		}
		boolean[] rows = null;
		if (items != null && check != CHECK_NONE) {
			rows = new boolean[items.length];
			for (int i = 0; i < items.length; i++)
				rows[i] = isChecked(i);
		}
		boolean custom = customTitle != null; // its view has the title and message
		nativeShowAlert(nativePtr, custom ? null : string(title), custom ? null : string(message), strings(items), items != null ? check : CHECK_NONE, rows,
		                atlProgressStyle(), atlIndeterminate(), atlProgressValue(), atlProgressMax(), atlHasRoot(), content(), light(),
		                labels, disabled, cancelable);
	}

	@Override
	boolean atlDialogResult(int which) {
		if (!drawn() || which == RESULT_CANCEL)
			return super.atlDialogResult(which);
		if (which >= 0 && check == CHECK_SINGLE) {
			checkedItem = which;
			if (listView != null)
				listView.setItemChecked(which, true);
			if (itemsListener != null)
				itemsListener.onClick(this, which);
		} else if (which >= 0 && check == CHECK_MULTI) {
			checked[which] = !checked[which];
			if (listView != null)
				listView.setItemChecked(which, checked[which]);
			if (multiListener != null)
				multiListener.onClick(this, which, checked[which]);
		} else if (which >= 0) {
			if (itemsListener != null)
				itemsListener.onClick(this, which);
			dismiss();
		} else {
			AlertButton button = buttons[-1 - which];
			if (button == null)
				click(which);
			else if (button.shown() && button.isEnabled()) // else a result from before the change
				button.performClick();
		}
		return drawn();
	}

	private static String string(CharSequence text) {
		return text != null ? text.toString() : null;
	}

	private static String[] strings(CharSequence[] texts) {
		if (texts == null)
			return null;
		String[] strings = new String[texts.length];
		for (int i = 0; i < texts.length; i++)
			strings[i] = string(texts[i]);
		return strings;
	}

	// a Cursor's rows, read once: the alert's own rows
	private static CharSequence[] labels(Cursor cursor, String labelColumn) {
		int column = cursor.getColumnIndexOrThrow(labelColumn);
		CharSequence[] labels = new CharSequence[cursor.getCount()];
		for (int i = 0; cursor.moveToPosition(i) && i < labels.length; i++)
			labels[i] = cursor.getString(column);
		return labels;
	}

	public static class Builder {
		private AlertDialog dialog;

		public Builder(Context context) {
			dialog = new AlertDialog(context);
		}

		public Builder(Context context, int themeResId) {
			dialog = new AlertDialog(context, themeResId);
		}

		public Context getContext() {
			return dialog.getContext();
		}

		public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
			return setPositiveButton(dialog.getContext().getText(textId), listener);
		}

		public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
			dialog.setButton(DialogInterface.BUTTON_POSITIVE, text, listener);
			return this;
		}

		public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
			dialog.setButton(DialogInterface.BUTTON_NEGATIVE, text, listener);
			return this;
		}

		public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
			return setNegativeButton(dialog.getContext().getText(textId), listener);
		}

		public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
			dialog.setButton(DialogInterface.BUTTON_NEUTRAL, text, listener);
			return this;
		}

		public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
			return setNeutralButton(dialog.getContext().getText(textId), listener);
		}

		public Builder setCancelable(boolean cancelable) {
			dialog.setCancelable(cancelable);
			return this;
		}

		public Builder setIcon(int iconId) {
			dialog.setIcon(iconId);
			return this;
		}

		public Builder setIcon(Drawable icon) {
			dialog.setIcon(icon);
			return this;
		}

		public Builder setIconAttribute(int attrId) {
			dialog.setIconAttribute(attrId);
			return this;
		}

		public Builder setTitle(CharSequence title) {
			dialog.setTitle(title);
			return this;
		}

		public Builder setTitle(int title) {
			return setTitle(dialog.getContext().getText(title));
		}

		public Builder setCustomTitle(View customTitleView) {
			dialog.setCustomTitle(customTitleView);
			return this;
		}

		public Builder setMessage(CharSequence message) {
			dialog.setMessage(message);
			return this;
		}

		public Builder setMessage(int message) {
			return setMessage(dialog.getContext().getText(message));
		}

		public Builder setView(View view) {
			dialog.setView(view);
			return this;
		}

		public Builder setView(int layoutResId) {
			return setView(LayoutInflater.from(dialog.getContext()).inflate(layoutResId, null));
		}

		public Builder setInverseBackground(boolean useInverseBackground) {
			return this;
		}

		public Builder setItems(CharSequence[] items, final DialogInterface.OnClickListener listener) {
			dialog.setItems(items, listener);
			return this;
		}

		public Builder setItems(int itemsId, final DialogInterface.OnClickListener listener) {
			return setItems(dialog.getContext().getResources().getTextArray(itemsId), listener);
		}

		public Builder setAdapter(ListAdapter adapter, DialogInterface.OnClickListener listener) {
			dialog.setAdapter(adapter, -1, false, listener);
			return this;
		}

		public Builder setCursor(Cursor cursor, DialogInterface.OnClickListener listener, String labelColumn) {
			return setItems(labels(cursor, labelColumn), listener);
		}

		public Builder setSingleChoiceItems(int itemsId, int checkedItem, DialogInterface.OnClickListener listener) {
			return setSingleChoiceItems(dialog.getContext().getResources().getTextArray(itemsId), checkedItem, listener);
		}

		public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
			dialog.setSingleChoiceItems(items, checkedItem, listener);
			return this;
		}

		public Builder setSingleChoiceItems(Cursor cursor, int checkedItem, String labelColumn, DialogInterface.OnClickListener listener) {
			return setSingleChoiceItems(labels(cursor, labelColumn), checkedItem, listener);
		}

		public Builder setSingleChoiceItems(ListAdapter adapter, int checkedItem, DialogInterface.OnClickListener listener) {
			dialog.setAdapter(adapter, checkedItem, true, listener);
			return this;
		}

		public Builder setMultiChoiceItems(int itemsId, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
			return setMultiChoiceItems(dialog.getContext().getResources().getTextArray(itemsId), checkedItems, listener);
		}

		public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
			dialog.setMultiChoiceItems(items, checkedItems, listener);
			return this;
		}

		public Builder setMultiChoiceItems(Cursor cursor, String isCheckedColumn, String labelColumn, DialogInterface.OnMultiChoiceClickListener listener) {
			CharSequence[] labels = labels(cursor, labelColumn);
			boolean[] checks = new boolean[labels.length];
			int column = cursor.getColumnIndexOrThrow(isCheckedColumn);
			for (int i = 0; cursor.moveToPosition(i) && i < checks.length; i++)
				checks[i] = cursor.getInt(column) == 1;
			return setMultiChoiceItems(labels, checks, listener);
		}

		public Builder setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
			dialog.itemSelectedListener = listener;
			return this;
		}

		public Builder setOnCancelListener(OnCancelListener onCancelListener) {
			dialog.setOnCancelListener(onCancelListener);
			return this;
		}

		public Builder setOnDismissListener(OnDismissListener onDismissListener) {
			dialog.setOnDismissListener(onDismissListener);
			return this;
		}

		public Builder setOnKeyListener(OnKeyListener onKeyListener) {
			dialog.setOnKeyListener(onKeyListener);
			return this;
		}

		public AlertDialog create() {
			return dialog;
		}

		public AlertDialog show() {
			dialog.show();
			return dialog;
		}
	}
}
