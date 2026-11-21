package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// U: a tap on "go" starts Q with the extra "finish"
public class TouchTapActivity extends TouchLoggingActivity {
	public TouchTapActivity() {
		super("U");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("go");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startActivity(new Intent(TouchTapActivity.this, TouchEndActivity.class).putExtra("finish", true));
			}
		});
	}
}
