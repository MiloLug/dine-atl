package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// F: each tap on "next" starts an X that finishes in its next callback: onCreate, onStart, onResume
public class FinishRootActivity extends LoggingActivity {
	private static final String[] AT = {"create", "start", "resume"};
	private int next;

	public FinishRootActivity() {
		super("F");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("next");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startActivity(new Intent(FinishRootActivity.this, FinishingActivity.class).putExtra("at", AT[next++]));
			}
		});
	}
}
