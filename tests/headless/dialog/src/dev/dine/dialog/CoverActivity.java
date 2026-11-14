package dev.dine.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/* Covers StackActivity and its dialog until Back. Started first, its button puts a StackActivity
 * over it: when that one finishes, its leaked dialog must not stay over this activity. */
public class CoverActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Button cover = new Button(this);
		cover.setText("cover");
		cover.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startActivity(new Intent(CoverActivity.this, StackActivity.class));
			}
		});
		setContentView(cover);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("stack", "stack cover resumed");
	}
}
