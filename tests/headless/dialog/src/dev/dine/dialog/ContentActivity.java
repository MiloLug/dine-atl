package dev.dine.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

// a Dialog with a content view: its button closes it, then the second one goes on a touch outside
public class ContentActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
		show(false);
	}

	private void show(final boolean last) {
		final Dialog dialog = new Dialog(this);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		TextView text = new TextView(this);
		text.setText("content text");
		column.addView(text);
		Button close = new Button(this);
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "content click");
				dialog.dismiss();
			}
		});
		column.addView(close);
		dialog.setContentView(column);
		dialog.setCanceledOnTouchOutside(true);
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				Log.i(TAG, "content shown");
			}
		});
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface d) {
				Log.i(TAG, "content canceled");
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface d) {
				Log.i(TAG, "content dismissed");
				if (last)
					finish();
				else
					show(true);
			}
		});
		dialog.show();
	}
}
