package android.view.inputmethod;

import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;

/* One keyboard per process: the EditText it types into follows Java's focus between EditTexts
 * while it shows; the toolkit's own focus changes come in through atlToolkitFocus. */
public class InputMethodManager {

	private static EditText served;
	private static boolean shown;

	// gtk only: an IM context for a view with its own InputConnection, made on its first use
	private static long im_context;
	private static View activeView = null;

	private ArrayList<InputMethodInfo> input_method_list = new ArrayList<InputMethodInfo>();

	public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
		boolean hid = atlHideKeyboard();
		if (activeView != null) {
			nativeHideSoftInput(im_context);
			activeView = null;
			hid = true;
		}
		return hid;
	}

	public boolean showSoftInput(View view, int flags) {
		if (view instanceof EditText) {
			served = (EditText)view;
			shown = served.atlSetKeyboard(true);
			return true;
		}
		if (im_context == 0)
			im_context = nativeInit();
		if (view == activeView) {
			return nativeShowSoftInput(im_context, view.widget, null, 0);
		}
		EditorInfo outAttrs = new EditorInfo();
		InputConnection ic = view.onCreateInputConnection(outAttrs);
		if (ic != null) {
			activeView = view;
			return nativeShowSoftInput(im_context, view.widget, ic, outAttrs.inputType);
		}
		return false;
	}

	public void toggleSoftInput(int showFlags, int hideFlags) {
		if (shown)
			atlHideKeyboard();
		else if (served != null && served.isFocused())
			showSoftInput(served, showFlags);
	}

	public boolean isFullscreenMode() { return false; }

	public boolean isActive(View view) {
		return (view == served && view.isFocused()) || activeView == view;
	}

	public boolean isActive() {
		return (served != null && served.isFocused()) || activeView != null;
	}

	public List<InputMethodInfo> getEnabledInputMethodList() {
		return input_method_list;
	}

	public List<InputMethodInfo> getInputMethodList() {
		return input_method_list;
	}

	public void restartInput(View view) {
		if (view instanceof EditText)
			((EditText)view).atlSendConfig();
	}

	public void updateSelection(View view, int selStart, int selEnd, int candidatesStart, int candidatesEnd) {}

	public InputMethodSubtype getCurrentInputMethodSubtype() {
		return new InputMethodSubtype();
	}

	// true when a keyboard was shown and now hides; Java's focus stays
	public static boolean atlHideKeyboard() {
		if (!shown)
			return false;
		shown = false;
		served.atlSetKeyboard(false);
		return true;
	}

	public static void atlFocusIn(EditText view) {
		if (shown && served != view) {
			served = view;
			shown = view.atlSetKeyboard(true);
		}
	}

	// after the focus change: the keyboard hides unless another EditText took it
	public static void atlFocusOut(final EditText view) {
		view.post(new Runnable() {
			@Override
			public void run() {
				if (served == view && !view.isFocused())
					atlHideKeyboard();
			}
		});
	}

	// the toolkit gave its input target to the field (keyboard: whether one shows) or took it away
	public static void atlToolkitFocus(EditText view, boolean focused, boolean keyboard) {
		if (focused) {
			served = view;
			shown = keyboard;
			if (!view.isFocused())
				view.requestFocus();
		} else if (served == view) {
			shown = false;
		}
	}

	private static native long nativeInit();
	private native boolean nativeShowSoftInput(long im_context, long widget, InputConnection ic, int inputType);
	private native void nativeHideSoftInput(long im_context);
}
