package dev.dine.nav;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;

/* A: starts B on its first onResume, so Back has an activity to finish before the task root; its
 * third onStop (Home) shows a list in the back. "Odd": a valid pair among lone surrogates and U+0000. */
public class MainActivity extends LoggingActivity {
	private static boolean startedSecond;
	private static int stops;

	public MainActivity() {
		super("A");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!startedSecond) {
			startedSecond = true;
			startActivity(new Intent(this, SecondActivity.class));
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (++stops == 3)
			showList();
	}

	private void showList() {
		new AlertDialog.Builder(this)
		    .setTitle("Nav list")
		    .setItems(new CharSequence[] {"Close", "Odd \uD83D\u0000😀\uDC00\uD83D"}, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
				    Log.i("nav", "nav: list " + which);
			    }
		    })
		    .setOnCancelListener(new DialogInterface.OnCancelListener() {
			    public void onCancel(DialogInterface dialog) {
				    Log.i("nav", "nav: list canceled");
			    }
		    })
		    .show();
	}
}
