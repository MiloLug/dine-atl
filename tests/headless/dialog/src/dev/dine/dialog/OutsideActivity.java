package dev.dine.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/* A Dialog with a content view that a touch outside it closes, from inside its own ACTION_UP, over
 * an activity whose view logs the touches it gets after that: "outside behind <action> ids <the
 * pointers' ids>". The gtk agent's touch commands feed it. */
public class OutsideActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView behind = new TextView(this);
		behind.setText("behind");
		behind.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				StringBuilder ids = new StringBuilder();
				for (int i = 0; i < event.getPointerCount(); i++)
					ids.append(i > 0 ? "," : "").append(event.getPointerId(i));
				Log.i(TAG, "outside behind " + MotionEvent.actionToString(event.getAction()) + " ids " + ids);
				return true;
			}
		});
		FrameLayout content = new FrameLayout(this);
		content.addView(behind, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		setContentView(content);

		Dialog dialog = new Dialog(this);
		TextView text = new TextView(this);
		text.setText("outside text");
		dialog.setContentView(text);
		dialog.setCanceledOnTouchOutside(true);
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface d) {
				Log.i(TAG, "outside dismissed");
			}
		});
		dialog.show();
		Log.i(TAG, "outside shown");
	}
}
