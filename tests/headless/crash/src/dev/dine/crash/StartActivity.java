package dev.dine.crash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

// the crash comes through Activity.startActivity, a native with a Java caller
public class StartActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivity(new Intent(this, CreateActivity.class));
	}
}
