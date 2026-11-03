package dev.dine.framework.windows;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

public class PlainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));
	}
}
