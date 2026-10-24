package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/* An alert that changes its buttons in onShow: "Yes" disabled, "Later" hidden, and "No" replaced by
 * a listener that keeps the alert open and brings "Yes" back as "Sure". Its dismissal finishes the
 * only activity, so ATL exits by itself. */
public class KeepActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		DialogInterface.OnClickListener logClick = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				Log.i(TAG, "keep onClick " + which);
			}
		};
		final AlertDialog dialog = new AlertDialog.Builder(this)
		                               .setTitle("Keep test")
		                               .setMessage("Keep it open?")
		                               .setPositiveButton("Yes", logClick)
		                               .setNegativeButton("No", logClick)
		                               .setNeutralButton("Later", logClick)
		                               .setOnDismissListener(new DialogInterface.OnDismissListener() {
			                               public void onDismiss(DialogInterface d) {
				                               Log.i(TAG, "keep done");
				                               finish();
			                               }
		                               })
		                               .create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				final Button yes = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
				yes.setEnabled(false);
				dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.GONE);
				dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						Log.i(TAG, "keep no kept");
						yes.setEnabled(true);
						yes.setText("Sure");
					}
				});
			}
		});
		dialog.show();
	}
}
