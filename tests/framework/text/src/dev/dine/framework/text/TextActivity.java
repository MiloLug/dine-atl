package dev.dine.framework.text;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

public class TextActivity extends Activity {
	FrameLayout root;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		root = new FrameLayout(this);
		setContentView(root);
	}
}
