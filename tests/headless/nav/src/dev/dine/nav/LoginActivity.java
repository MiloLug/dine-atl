package dev.dine.nav;

import android.content.Intent;
import android.os.Bundle;

// L: starts S for a result from its own onCreate, as a screen that needs a login first does
public class LoginActivity extends LoggingActivity {
	public LoginActivity() {
		super("L");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivityForResult(new Intent(this, AnswerActivity.class), 7);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		log("result " + requestCode + " " + resultCode + " " + (data != null ? data.getStringExtra("answer") : null));
	}
}
