package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

public class LayoutActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CreateActivity.logCheckJni();
		setContentView(new FrameLayout(this) {
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				throw new IllegalStateException("crash in onLayout");
			}
		});
	}
}
