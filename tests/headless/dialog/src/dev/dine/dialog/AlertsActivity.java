package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/* Alerts over each other, as the registry orders them. "First" is up from the start: "Show second"
 * (a button whose listener the app replaced, so the alert stays) shows "Second" over it; "Close
 * first", a replaced button of the second, dismisses the first from under it; "Later" changes the
 * first's message after 1.5 s and dismisses it after 6 s, with the second up; "Cover" shows a
 * Dialog with a content view over the first, and "Uncover" closes it again; "Done" ends the
 * second and the activity. The alerts are made after the activity's start, so they show at once. */
public class AlertsActivity extends Activity {
	private static final String TAG = "dialog";
	private AlertDialog first, second;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		new Handler().post(new Runnable() {
			public void run() {
				show();
			}
		});
	}

	private void show() {
		first = new AlertDialog.Builder(this)
		            .setTitle("First")
		            .setMessage("the first alert")
		            .setPositiveButton("Show second", null)
		            .setNegativeButton("Cover", null)
		            .create();
		first.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				first.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						showSecond();
					}
				});
				first.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						cover();
					}
				});
				Log.i(TAG, "alerts first shown");
			}
		});
		first.show();
	}

	private void showSecond() {
		second = new AlertDialog.Builder(this)
		             .setTitle("Second")
		             .setMessage("the second alert")
		             .setPositiveButton("Close first", null)
		             .setNeutralButton("Later", null)
		             .setNegativeButton("Done", new DialogInterface.OnClickListener() {
			             public void onClick(DialogInterface d, int which) {
				             Log.i(TAG, "alerts done");
				             finish();
			             }
		             })
		             .create();
		second.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				second.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						later();
					}
				});
				second.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						first.dismiss();
						Log.i(TAG, "alerts first dismissed");
					}
				});
				Log.i(TAG, "alerts second shown");
			}
		});
		second.show();
	}

	/* With the second one up again (a tap on any alert's button dismisses it first), not from a button of
	 * it: the first changes its message, then is dismissed, as a timer or another thread would. */
	private void later() {
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			public void run() {
				first.setMessage("the first alert, rebuilt");
				Log.i(TAG, "alerts first rebuilt");
			}
		}, 1500);
		handler.postDelayed(new Runnable() {
			public void run() {
				first.dismiss();
				Log.i(TAG, "alerts first dismissed later");
			}
		}, 6000); // the smoke step that waits for it starts about 2.5 s after the tap: the line must land after its mark
	}

	private void cover() {
		final Dialog content = new Dialog(this);
		Button uncover = new Button(this);
		uncover.setText("Uncover");
		uncover.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				content.dismiss();
				Log.i(TAG, "alerts cover closed");
			}
		});
		content.setContentView(uncover);
		content.show();
		Log.i(TAG, "alerts cover shown");
	}
}
