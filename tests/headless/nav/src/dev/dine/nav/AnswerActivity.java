package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// S: a tap on "answer" sets ResultActivity's result and finishes
public class AnswerActivity extends LoggingActivity {
	public AnswerActivity() {
		super("S");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("answer");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_OK, new Intent().putExtra("answer", "yes"));
				finish();
			}
		});
	}
}
