package dev.dine.framework.input;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.FrameLayout;

// counts Back instead of finishing, so a test keeps its activity
public class InputActivity extends Activity {
	FrameLayout root;
	int backs;
	int keyDowns;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		root = new FrameLayout(this);
		setContentView(root);
	}

	@Override
	public void onBackPressed() {
		backs++;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode != KeyEvent.KEYCODE_BACK)
			keyDowns++;
		return super.onKeyDown(keyCode, event);
	}
}
