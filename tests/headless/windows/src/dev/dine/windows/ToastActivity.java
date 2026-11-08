package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/* Two text toasts, then a setView one, then a long one, in turn; Home hides the one up, and a toast
 * made while the task is in the back never shows. */
public class ToastActivity extends Activity {
	private static final String TAG = "windows";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("toasts");
		setContentView(text);
		Toast.makeText(this, "hello", Toast.LENGTH_SHORT).show();
		Toast.makeText(this, "world", Toast.LENGTH_SHORT).show();
		TextView custom = new TextView(this);
		custom.setText("custom");
		custom.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			public void onViewAttachedToWindow(View view) {
				Log.i(TAG, "toast custom attached");
			}

			public void onViewDetachedFromWindow(View view) {
				Log.i(TAG, "toast custom detached");
			}
		});
		Toast toast = new Toast(this);
		toast.setView(custom);
		toast.show();
		Toast.makeText(this, "last", Toast.LENGTH_LONG).show();
		Log.i(TAG, "toast activity created");
	}

	@Override
	protected void onStop() {
		super.onStop();
		Toast.makeText(this, "background", Toast.LENGTH_SHORT).show();
		new Handler().postDelayed(new Runnable() {
			public void run() {
				Log.i(TAG, "toast background done");
			}
		}, 2500);
	}
}
