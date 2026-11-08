package dev.dine.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;

/* A single choice list (Blue opens a plain alert over it), then a multi choice list, then an
 * adapter's list Android draws; a pick or Skip there finishes. */
public class ChoiceActivity extends Activity {
	private static final String TAG = "dialog";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
		single();
	}

	private void single() {
		final AlertDialog[] dialog = new AlertDialog[1];
		dialog[0] = new AlertDialog.Builder(this)
		                .setTitle("Pick one")
		                .setSingleChoiceItems(new CharSequence[] {"Red", "Green", "Blue"}, 0, new DialogInterface.OnClickListener() {
			                public void onClick(DialogInterface d, int which) {
				                Log.i(TAG, "choice single " + which);
				                if (which == 2)
					                new AlertDialog.Builder(ChoiceActivity.this).setTitle("Blue is taken").setPositiveButton("Okay", null).show();
			                }
		                })
		                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			                public void onClick(DialogInterface d, int which) {
				                Log.i(TAG, "choice single ok " + dialog[0].getListView().getCheckedItemPosition());
				                multi();
			                }
		                })
		                .create();
		// the list exists before the rows answer: their checks reach it
		dialog[0].setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				Log.i(TAG, "choice single shown " + dialog[0].getListView().getCheckedItemPosition());
			}
		});
		dialog[0].show();
	}

	private void multi() {
		final AlertDialog[] dialog = new AlertDialog[1];
		dialog[0] = new AlertDialog.Builder(this)
		                .setTitle("Pick some")
		                .setMultiChoiceItems(new CharSequence[] {"Salt", "Pepper", "Oil"}, new boolean[] {false, true, false},
		                                     new DialogInterface.OnMultiChoiceClickListener() {
			                                     public void onClick(DialogInterface d, int which, boolean isChecked) {
				                                     Log.i(TAG, "choice multi " + which + " " + isChecked);
			                                     }
		                                     })
		                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			                public void onClick(DialogInterface d, int which) {
				                SparseBooleanArray checked = dialog[0].getListView().getCheckedItemPositions();
				                StringBuilder picked = new StringBuilder();
				                for (int i = 0; i < 3; i++) {
					                if (checked.get(i))
						                picked.append(picked.length() > 0 ? "," : "").append(i);
				                }
				                Log.i(TAG, "choice multi ok " + picked);
				                adapter();
			                }
		                })
		                .show();
	}

	private void adapter() {
		ArrayAdapter<String> fruits = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {"Apple", "Pear"});
		AlertDialog dialog = new AlertDialog.Builder(this)
		                         .setTitle("Pick a fruit")
		                         .setAdapter(fruits, new DialogInterface.OnClickListener() {
			                         public void onClick(DialogInterface d, int which) {
				                         Log.i(TAG, "choice adapter " + which);
			                         }
		                         })
		                         .setNegativeButton("Skip", null)
		                         .create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				Log.i(TAG, "choice adapter shown");
			}
		});
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface d) {
				Log.i(TAG, "choice done");
				finish();
			}
		});
		dialog.show();
	}
}
