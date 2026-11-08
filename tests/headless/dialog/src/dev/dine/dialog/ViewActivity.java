package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

// an AlertDialog with setView content: a field and a button inside the alert's panel
public class ViewActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		final EditText name = new EditText(this);
		name.setHint("name");
		column.addView(name);
		Button inner = new Button(this);
		inner.setText("Inner");
		inner.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "view inner click");
			}
		});
		column.addView(inner);
		AlertDialog dialog = new AlertDialog.Builder(this)
		                         .setTitle("View test")
		                         .setView(column)
		                         .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			                         public void onClick(DialogInterface d, int which) {
				                         Log.i(TAG, "view ok \"" + name.getText() + "\"");
			                         }
		                         })
		                         .setNegativeButton("Cancel", null)
		                         .create();
		dialog.setCanceledOnTouchOutside(true);
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				Log.i(TAG, "view shown");
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface d) {
				Log.i(TAG, "view dismissed");
				finish();
			}
		});
		dialog.show();
	}
}
