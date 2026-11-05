package dev.dine.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

// covers StackActivity and its dialog until Back
public class CoverActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("cover");
		setContentView(text);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("stack", "stack cover resumed");
	}
}
