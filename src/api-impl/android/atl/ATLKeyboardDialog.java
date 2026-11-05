package android.atl;

import android.app.Dialog;
import android.content.Context;
import android.view.WindowManager;

// gtk's on-screen keyboard: its own layer-shell window, as Android's input method window is one
public class ATLKeyboardDialog extends Dialog {
	@Override
	protected native long nativeInit();
	private native void nativePresent(long window);
	private native void nativeHide(long window);

	public ATLKeyboardDialog(Context context) {
		super(context);
		getWindow().setType(WindowManager.LayoutParams.TYPE_INPUT_METHOD);
	}

	@Override
	protected boolean atlNativeAlert() {
		return true;
	}

	@Override
	protected void showNative() {
		nativePresent(nativePtr);
	}

	@Override
	protected void closeNative() {
		nativeHide(nativePtr);
	}

	// the keyboard's window lives as long as the process
	@Override
	protected void destroyNative() {}
}
