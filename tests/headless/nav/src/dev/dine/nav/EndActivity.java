package dev.dine.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// E: after a tap on "arm" (logged: the click is a message of its own), finishes in its next onStop:
// with Home, while the task is in the back
public class EndActivity extends LoggingActivity {
	private boolean armed;

	public EndActivity() {
		super("E");
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
		if (armed)
			finish();
	}
}
