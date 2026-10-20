package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/* An alert with two buttons; its positive button opens a list, whose dismissal finishes the only
 * activity, so ATL exits by itself; so does the alert's dismissal after a cancel. The list opens
 * while the alert's close is still pending. */
public class MainActivity extends Activity {
	private static final String TAG = "dialog";

	private boolean canceled;

	private final DialogInterface.OnClickListener logClick = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			Log.i(TAG, "dialog onClick " + which);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		new AlertDialog.Builder(this)
		    .setTitle("Dialog test")
		    .setMessage("Do you like dialogs?")
		    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
				    logClick.onClick(dialog, which);
				    showList();
			    }
		    })
		    .setNegativeButton("No", logClick)
		    .setOnCancelListener(new DialogInterface.OnCancelListener() {
			    public void onCancel(DialogInterface dialog) {
				    Log.i(TAG, "dialog canceled");
				    canceled = true;
			    }
		    })
		    .setOnDismissListener(new DialogInterface.OnDismissListener() {
			    public void onDismiss(DialogInterface dialog) {
				    if (canceled)
					    finish();
			    }
		    })
		    .show();
	}

	private void showList() {
		new AlertDialog.Builder(this)
		    .setTitle("Pick a color")
		    .setItems(new CharSequence[] {"Red", "Green", "Blue"}, logClick)
		    .setOnDismissListener(new DialogInterface.OnDismissListener() {
			    public void onDismiss(DialogInterface dialog) {
				    Log.i(TAG, "dialog done");
				    finish();
			    }
		    })
		    .show();
	}
}
