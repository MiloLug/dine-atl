package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/* An alert with three buttons that change while it is drawn: "No" keeps it open, disables "Yes" and
 * hides "Later"; before that "Later" closes it. Its dismissal finishes the only activity, so ATL
 * exits by itself. */
public class LateActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		// after the activity's start, so the alert shows at once
		new Handler().post(new Runnable() {
			public void run() {
				show();
			}
		});
	}

	private void show() {
		DialogInterface.OnClickListener logClick = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				Log.i(TAG, "late onClick " + which);
			}
		};
		final AlertDialog dialog = new AlertDialog.Builder(this)
		                               .setTitle("Late test")
		                               .setMessage("Change it later?")
		                               .setPositiveButton("Yes", logClick)
		                               .setNegativeButton("No", logClick)
		                               .setNeutralButton("Later", logClick)
		                               .setOnDismissListener(new DialogInterface.OnDismissListener() {
			                               public void onDismiss(DialogInterface d) {
				                               Log.i(TAG, "late done");
				                               finish();
			                               }
		                               })
		                               .create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				final Button yes = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
				final Button later = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
				dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						Log.i(TAG, "late no kept");
						yes.setEnabled(false);
						later.setVisibility(View.GONE);
					}
				});
			}
		});
		dialog.show();
	}
}
