package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/* H: the task's only activity; a tap on "go" finishes it and then starts E, so the stack is empty
 * between the two. */
public class HandoverActivity extends LoggingActivity {
	public HandoverActivity() {
		super("H");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("go");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
				startActivity(new Intent(HandoverActivity.this, EndActivity.class));
			}
		});
	}
}
