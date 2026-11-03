package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/* A list of 100 rows scrolled by 600 px once laid out: the first row shown is logged. It stays, for
 * a scroll by hand on the phone. */
public class ListsActivity extends Activity {
	private static final String TAG = "widgets";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String[] items = new String[100];
		for (int i = 0; i < items.length; i++)
			items[i] = "item " + i;
		final ListView list = new ListView(this);
		list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
		setContentView(list);
		list.post(new Runnable() {
			public void run() {
				list.scrollListBy(600);
				Log.i(TAG, "lists first " + list.getFirstVisiblePosition() + " of " + list.getChildCount() + " shown");
			}
		});
	}
}
