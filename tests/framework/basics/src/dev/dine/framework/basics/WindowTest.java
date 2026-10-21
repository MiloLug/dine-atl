package dev.dine.framework.basics;

import android.content.Intent;
import android.widget.TextView;

// android.test is what ATL's runner runs; javac 8 warns on a deprecated import despite the annotation
@SuppressWarnings("deprecation")
public class WindowTest extends android.test.InstrumentationTestCase {
	public void testContentViewOnMainThread() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		assertNotNull(activity);
		final TextView[] text = new TextView[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				text[0] = new TextView(activity);
				activity.root.addView(text[0]);
			}
		});
		assertSame(activity.root, text[0].getParent());
	}
}
