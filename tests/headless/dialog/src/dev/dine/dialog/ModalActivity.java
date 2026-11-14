package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/* Dialogs that can't be canceled: an alert that only its buttons close, "Done" for good, "More"
 * for a Dialog with a content view that only its own button closes. Each end finishes the only
 * activity, so ATL exits by itself. */
public class ModalActivity extends Activity {
	private static final String TAG = "dialog";

	private final DialogInterface.OnCancelListener logCancel = new DialogInterface.OnCancelListener() {
		public void onCancel(DialogInterface dialog) {
			Log.i(TAG, "modal canceled");
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		new AlertDialog.Builder(this)
		    .setTitle("Modal test")
		    .setCancelable(false)
		    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
				    Log.i(TAG, "modal done");
				    finish();
			    }
		    })
		    .setNegativeButton("More", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
				    content();
			    }
		    })
		    .setOnCancelListener(logCancel)
		    .show();
	}

	private void content() {
		final Dialog dialog = new Dialog(this);
		Button close = new Button(this);
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "modal content click");
				dialog.dismiss();
			}
		});
		dialog.setContentView(close);
		dialog.setCancelable(false);
		dialog.setOnCancelListener(logCancel);
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface d) {
				Log.i(TAG, "modal content dismissed");
				finish();
			}
		});
		dialog.show();
		Log.i(TAG, "modal content shown");
	}
}
