package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;

public class AlertDialog extends Dialog implements DialogInterface {

	private CharSequence message;
	private CharSequence[] items;
	private OnClickListener itemsListener;
	// positive, negative, neutral: index -1 - DialogInterface.BUTTON_*
	private final CharSequence[] buttonLabels = new CharSequence[3];
	private final OnClickListener[] buttonListeners = new OnClickListener[3];

	private native void nativeShowAlert(long ptr, String title, String message, String[] items, String[] buttons, boolean cancelable);

	public AlertDialog(Context context) {
		this(context, 0);
	}

	public AlertDialog(Context context, int themeResId) {
		super(context, themeResId);
	}

	public void setMessage(CharSequence message) {
		System.out.println("AlertDialog setMessage called with: '" + message + "'");
		this.message = message;
	}

	public void setButton(int whichButton, CharSequence text, OnClickListener listener) {
		buttonLabels[-1 - whichButton] = text;
		buttonListeners[-1 - whichButton] = listener;
	}

	// the ui backend draws the buttons: there are no Button views
	public Button getButton(int whichButton) {
		return null;
	}

	public void setView(View view) {
		setContentView(view);
	}

	void setItems(CharSequence[] items, OnClickListener listener) {
		this.items = items;
		this.itemsListener = listener;
	}

	@Override
	void showNative() {
		nativeShowAlert(nativePtr, string(title), string(message), strings(items), strings(buttonLabels), cancelable);
	}

	@Override
	void atlDialogResult(int which) {
		if (!isShowing() || which == RESULT_CANCEL) {
			super.atlDialogResult(which);
			return;
		}
		OnClickListener listener = which >= 0 ? itemsListener : buttonListeners[-1 - which];
		if (listener != null)
			listener.onClick(this, which);
		dismiss();
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

	public static class Builder {
		private AlertDialog dialog;

		public Builder(Context context) {
			System.out.println("making an AlertDialog$Builder as we speak, my word!");
			dialog = new AlertDialog(context);
		}

		public Builder(Context context, int themeResId) {
			dialog = new AlertDialog(context, themeResId);
		}

		public AlertDialog.Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
			return setPositiveButton(dialog.getContext().getText(textId), listener);
		}

		public AlertDialog.Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
			System.out.println("AlertDialog.Builder setPositiveButton called with text: '" + text + "'");
			dialog.setButton(DialogInterface.BUTTON_POSITIVE, text, listener);
			return this;
		}

		public AlertDialog.Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
			System.out.println("AlertDialog.Builder setNegativeButton called with text: '" + text + "'");
			dialog.setButton(DialogInterface.BUTTON_NEGATIVE, text, listener);
			return this;
		}

		public AlertDialog.Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
			return setNegativeButton(dialog.getContext().getText(textId), listener);
		}

		public AlertDialog.Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
			System.out.println("AlertDialog.Builder setNeutralButton called with text: '" + text + "'");
			dialog.setButton(DialogInterface.BUTTON_NEUTRAL, text, listener);
			return this;
		}

		public AlertDialog.Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
			return setNeutralButton(dialog.getContext().getText(textId), listener);
		}

		public AlertDialog.Builder setCancelable(boolean cancelable) {
			dialog.setCancelable(cancelable);
			return this;
		}

		public AlertDialog.Builder setIcon(int iconId) {
			return this;
		}

		public AlertDialog.Builder setTitle(CharSequence title) {
			System.out.println("AlertDialog.Builder setTitle called with: '" + title + "'");
			dialog.setTitle(title);
			return this;
		}

		public AlertDialog.Builder setTitle(int title) {
			return setTitle(dialog.getContext().getText(title));
		}

		public AlertDialog.Builder setMessage(CharSequence message) {
			System.out.println("AlertDialog.Builder setMessage called with: '" + message + "'");
			dialog.setMessage(message);
			return this;
		}

		public AlertDialog.Builder setMessage(int message) {
			return setMessage(dialog.getContext().getText(message));
		}

		public AlertDialog.Builder setView(View view) {
			return this;
		}

		public AlertDialog.Builder setItems(CharSequence[] items, final DialogInterface.OnClickListener listener) {
			dialog.setItems(items, listener);
			return this;
		}

		public AlertDialog.Builder setItems(int itemsId, final DialogInterface.OnClickListener listener) {
			return setItems(dialog.getContext().getResources().getTextArray(itemsId), listener);
		}

		public Builder setOnCancelListener(OnCancelListener onCancelListener) {
			dialog.setOnCancelListener(onCancelListener);
			return this;
		}

		public Builder setOnDismissListener(OnDismissListener onDismissListener) {
			dialog.setOnDismissListener(onDismissListener);
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
