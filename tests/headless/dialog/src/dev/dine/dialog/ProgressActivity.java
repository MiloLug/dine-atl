package dev.dine.dialog;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.FrameLayout;

// ProgressDialog.show's spinner, then a bar that moves and changes its message, then dismissed
public class ProgressActivity extends Activity {
	private static final String TAG = "dialog";
	private final Handler handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
		final ProgressDialog spinner = ProgressDialog.show(this, "Wait", "Spinning", true, true);
		Log.i(TAG, "progress spinner shown " + spinner.isShowing());
		handler.postDelayed(new Runnable() {
			public void run() {
				spinner.dismiss();
				bar();
			}
		}, 300);
	}

	private void bar() {
		final ProgressDialog bar = new ProgressDialog(this);
		bar.setTitle("Loading");
		bar.setMessage("Please wait");
		bar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		bar.setMax(10);
		bar.show();
		Log.i(TAG, "progress bar shown");
		handler.postDelayed(new Runnable() {
			public void run() {
				bar.setProgress(5);
				bar.setMessage("Almost");
				Log.i(TAG, "progress " + bar.getProgress() + "/" + bar.getMax());
				handler.postDelayed(new Runnable() {
					public void run() {
						bar.dismiss();
						Log.i(TAG, "progress done");
						finish();
					}
				}, 300);
			}
		}, 300);
	}
}
