package dev.dine.framework.windows;

import android.content.Intent;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

// a window's LayoutParams on every build: Android's defaults, and the setters keep what they set
@SuppressWarnings("deprecation")
public class WindowAttributesTest extends android.test.InstrumentationTestCase {
	public void testDefaults() {
		WindowManager.LayoutParams params = new WindowManager.LayoutParams();
		assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width);
		assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, params.type);
		assertEquals(1f, params.alpha);
		assertEquals(1f, params.dimAmount);
		assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, params.screenBrightness);
	}

	public void testSettersKeepTheirValues() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		final PlainActivity activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
		final Throwable[] thrown = new Throwable[1];
		final Window[] windows = new Window[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				try {
					// an unshown Dialog's: gtk sizes the activity's top-level window from setLayout
					Window window = new android.app.Dialog(activity).getWindow();
					windows[0] = window;
					WindowManager.LayoutParams params = window.getAttributes();
					params.screenBrightness = 0.5f;
					window.setAttributes(params);
					window.setLayout(300, 200);
					window.setGravity(Gravity.BOTTOM);
					window.setDimAmount(0.25f);
				} catch (Throwable t) {
					thrown[0] = t;
				}
			}
		});
		assertNull(String.valueOf(thrown[0]), thrown[0]);
		WindowManager.LayoutParams params = windows[0].getAttributes();
		assertEquals(0.5f, params.screenBrightness);
		assertEquals(300, params.width);
		assertEquals(200, params.height);
		assertEquals(Gravity.BOTTOM, params.gravity);
		assertEquals(0.25f, params.dimAmount);
	}
}
