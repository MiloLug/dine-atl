package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

/* An alert whose message the app reaches through AOSP's id, android.R.id.message: it reads "Old",
 * then sets "New", which the alert shows; "OK" ends it and the activity. The alert is made after
 * the activity's start, so it shows at once. */
public class MessageActivity extends Activity {
	private static final String TAG = "dialog";

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
		final AlertDialog dialog = new AlertDialog.Builder(this)
		                               .setTitle("Message test")
		                               .setMessage("Old")
		                               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			                               public void onClick(DialogInterface d, int which) {
				                               Log.i(TAG, "message onClick " + which);
				                               finish();
			                               }
		                               })
		                               .create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				TextView message = (TextView)dialog.findViewById(android.R.id.message);
				Log.i(TAG, "message reads " + message.getText());
				message.setText("New");
			}
		});
		dialog.show();
	}
}
