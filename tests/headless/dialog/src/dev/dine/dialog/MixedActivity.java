package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

/* An alert with a message and items shows the message only, as AOSP 10's AlertController; "OK" opens
 * one with a custom title and items, whose panel makes room for the rows and keeps them after a late
 * setMessage (AOSP sets the list up once, in onCreate); "Blue" opens a list alert whose late message
 * doesn't show either; "Red" ends it. */
public class MixedActivity extends Activity {
	private static final String TAG = "dialog";
	private static final String[] COLORS = {"Red", "Blue"};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		new Handler().post(new Runnable() {
			public void run() {
				showMessage();
			}
		});
	}

	private void showMessage() {
		new AlertDialog.Builder(this)
		    .setTitle("Mixed")
		    .setMessage("Pick one")
		    .setItems(COLORS, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface d, int which) {
				    Log.i(TAG, "mixed item " + which);
			    }
		    })
		    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface d, int which) {
				    Log.i(TAG, "mixed ok");
				    showCustomTitle();
			    }
		    })
		    .show();
	}

	private void showCustomTitle() {
		TextView title = new TextView(this);
		title.setText("Custom");
		AlertDialog dialog = new AlertDialog.Builder(this)
		                         .setCustomTitle(title)
		                         .setItems(COLORS, new DialogInterface.OnClickListener() {
			                         public void onClick(DialogInterface d, int which) {
				                         Log.i(TAG, "mixed custom item " + which);
				                         showListed();
			                         }
		                         })
		                         .show();
		dialog.setMessage("Late");
	}

	private void showListed() {
		AlertDialog dialog = new AlertDialog.Builder(this)
		                         .setTitle("Listed")
		                         .setItems(COLORS, new DialogInterface.OnClickListener() {
			                         public void onClick(DialogInterface d, int which) {
				                         Log.i(TAG, "mixed listed item " + which);
				                         finish();
			                         }
		                         })
		                         .show();
		dialog.setMessage("Late");
	}
}
