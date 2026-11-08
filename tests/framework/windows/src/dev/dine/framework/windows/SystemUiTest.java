package dev.dine.framework.windows;

import android.atl.ATLTestHooks;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import java.util.Arrays;

// the shown activity's bar flags reach its app's frame; its views hear of a change
public class SystemUiTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;
	private volatile int heard = -1;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	@Override
	protected void tearDown() throws Exception {
		activity.finish();
		getInstrumentation().waitForIdleSync();
		super.tearDown();
	}

	// fullscreen, cover top, hide navigation, immersive, after the flags and the cutout mode
	private String frame(final int flags, final int cutout) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				WindowManager.LayoutParams params = activity.getWindow().getAttributes();
				params.layoutInDisplayCutoutMode = cutout;
				activity.getWindow().setAttributes(params);
				activity.getWindow().getDecorView().setSystemUiVisibility(flags);
			}
		});
		getInstrumentation().waitForIdleSync();
		return Arrays.toString(ATLTestHooks.frame(activity));
	}

	public void testFlags() {
		assertEquals("[false, true, true, true]", frame(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
		                                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT));
		assertEquals("[false, false, false, false]", frame(0, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT));
	}

	// the user's choice: a window without the status bar takes the notch when its mode asks
	public void testCoverTopByCutoutMode() {
		assertEquals("[true, false, false, false]", frame(View.SYSTEM_UI_FLAG_FULLSCREEN, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT));
		assertEquals("[true, true, false, false]", frame(View.SYSTEM_UI_FLAG_FULLSCREEN, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES));
		assertEquals("[false, false, false, false]", frame(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER));
	}

	public void testWindowFlagsAndListener() {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				View content = activity.findViewById(android.R.id.content);
				content.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
					public void onSystemUiVisibilityChange(int visibility) {
						heard = visibility;
					}
				});
				activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
			}
		});
		getInstrumentation().runOnMainSync(new Runnable() { // after the posted dispatch (ATL's waitForIdleSync waits for nothing)
			public void run() {}
		});
		assertEquals(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE, heard);
		assertEquals(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE, activity.findViewById(android.R.id.content).getWindowSystemUiVisibility());
	}

	// what shows where the window's root doesn't reach: its background's color, else the theme's
	public void testBackground() {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff123456));
			}
		});
		assertEquals(0xff123456, ATLTestHooks.frameBackground(activity));
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				activity.getWindow().setBackgroundDrawable(null);
			}
		});
		android.content.res.TypedArray a = activity.obtainStyledAttributes(new int[] {android.R.attr.colorBackground});
		int theme = a.getColor(0, 0);
		a.recycle();
		assertEquals(theme, ATLTestHooks.frameBackground(activity));
	}

	public void testInsetsController() {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				activity.getWindow().getInsetsController().setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
				activity.getWindow().getInsetsController().hide(android.view.WindowInsets.Type.navigationBars());
			}
		});
		getInstrumentation().waitForIdleSync();
		assertEquals("[false, false, true, true]", Arrays.toString(ATLTestHooks.frame(activity)));
	}
}
