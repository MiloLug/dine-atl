package dev.dine.framework.controls;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

public class ControlsActivity extends Activity {
	FrameLayout root;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		root = new FrameLayout(this);
		setContentView(root);
	}
}
