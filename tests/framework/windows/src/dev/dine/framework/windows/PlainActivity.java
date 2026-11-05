package dev.dine.framework.windows;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

public class PlainActivity extends Activity {
	Object kept; // what a test has the activity keep, as an app keeps its dialog in a field
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
	}
}
