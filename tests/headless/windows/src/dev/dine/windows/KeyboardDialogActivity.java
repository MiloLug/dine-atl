package dev.dine.windows;

import android.app.Activity;
import android.atl.ATLKeyboardDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

/* gtk: the on-screen keyboard's dialog takes Dialog's window calls, though its native window is a
 * layer-shell one of its own, not a dialog of the ui slot. */
public class KeyboardDialogActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ATLKeyboardDialog dialog = new ATLKeyboardDialog(this);
		dialog.setTitle("keyboard");
		dialog.setCanceledOnTouchOutside(true);
		Log.i("windows", "keyboard dialog configured");
		new Handler().post(new Runnable() {
			public void run() {
				finish();
			}
		});
	}
}
