package android.view.inputmethod;

import android.content.Context;
import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;

/* One keyboard per app (ATLLoadedApp.input_method): the EditText it types into follows Java's
 * focus between EditTexts while it shows; the toolkit's own focus changes come in through
 * atlToolkitFocus. As in AOSP, a show request stands until a hide whether or not the toolkit shows
 * a keyboard (gtk: none, a desktop has its own). */
public class InputMethodManager {

	private EditText served;
	private boolean requested; // AOSP's mInputShown: answers hideSoftInputFromWindow and toggleSoftInput
	private boolean shown;     // the toolkit's keyboard is up: Back hides it first

	// gtk only: an IM context for a view with its own InputConnection, made on its first use
	private long im_context;
	private View activeView = null;

	/** @hide the keyboard of the app the context lives in */
	public static InputMethodManager atlOf(Context context) {
		return context.atl_process_app().input_method;
	}

	private ArrayList<InputMethodInfo> input_method_list = new ArrayList<InputMethodInfo>();

	public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
		boolean hid = requested;
		hide();
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
			requested = true;
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
		if (requested)
			hide();
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

	// Back's: true when the toolkit's keyboard was up and now hides, which ends the request; Java's focus stays
	public boolean atlHideKeyboard() {
		if (!shown)
			return false;
		shown = false;
		requested = false;
		served.atlSetKeyboard(false);
		return true;
	}

	private void hide() {
		requested = false;
		atlHideKeyboard();
	}

	public void atlFocusIn(EditText view) {
		if (requested && served != view) {
			served = view;
			shown = view.atlSetKeyboard(true);
		}
	}

	// after the focus change: the keyboard hides unless another EditText took it
	public void atlFocusOut(final EditText view) {
		view.post(new Runnable() {
			@Override
			public void run() {
				if (served == view && !view.isFocused())
					hide();
			}
		});
	}

	/* the toolkit gave its input target to the field (keyboard: whether one shows, a request only
	 * then: gtk's focus also follows Java's requestFocus) or took it away */
	public void atlToolkitFocus(EditText view, boolean focused, boolean keyboard) {
		if (focused) {
			served = view;
			requested |= keyboard;
			shown = keyboard;
			if (!view.isFocused())
				view.requestFocus();
		} else if (served == view && shown) { // gtk's focus follows Java's: atlFocusOut ends its request
			requested = false;
			shown = false;
		}
	}

	private static native long nativeInit();
	private native boolean nativeShowSoftInput(long im_context, long widget, InputConnection ic, int inputType);
	private native void nativeHideSoftInput(long im_context);
}
