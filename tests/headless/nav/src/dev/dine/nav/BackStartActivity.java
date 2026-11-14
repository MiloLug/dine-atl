package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// T: after a tap on "arm" (logged), starts E from its next onStop: with Home, while the task is in
// the back
public class BackStartActivity extends LoggingActivity {
	private boolean armed;

	public BackStartActivity() {
		super("T");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("arm");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				armed = true;
				log("armed");
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (armed) {
			armed = false;
			startActivity(new Intent(this, EndActivity.class));
		}
	}
}
