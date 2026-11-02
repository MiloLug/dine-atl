package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;

public class CreateActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		throw new IllegalStateException("crash in onCreate");
	}
}
