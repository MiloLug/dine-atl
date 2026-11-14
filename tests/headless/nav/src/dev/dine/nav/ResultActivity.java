package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/* R: a tap on "ask" starts S for a result, from inside the tap's dispatch (MainActivity's
 * startActivity is posted); logs the result as "nav: R result <request> <code> <answer>". */
public class ResultActivity extends LoggingActivity {
	public ResultActivity() {
		super("R");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((TextView)content).setText("ask");
		content.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startActivityForResult(new Intent(ResultActivity.this, AnswerActivity.class), 7);
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i("nav", "nav: R result " + requestCode + " " + resultCode + " " + (data != null ? data.getStringExtra("answer") : null));
	}
}
