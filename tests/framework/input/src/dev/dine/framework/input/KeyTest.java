package dev.dine.framework.input;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

// the key chain: dialog or activity, the window's focused views, then Activity/Dialog onKeyDown and
// onKeyUp; ATL's host entry adds ESC's fallback to Back and Tab/arrow focus navigation
@SuppressWarnings("deprecation")
public class KeyTest extends android.test.InstrumentationTestCase {
	private InputActivity activity;
	private View decor;
	private View first, second;
	private int clicks;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), InputActivity.class);
		activity = (InputActivity)getInstrumentation().startActivitySync(intent);
		onMain(new Runnable() {
			public void run() {
				decor = activity.getWindow().getDecorView();
				activity.root.removeAllViews();
				first = button();
				second = button();
				activity.root.addView(first, new FrameLayout.LayoutParams(100, 50));
				FrameLayout.LayoutParams below = new FrameLayout.LayoutParams(100, 50);
				below.topMargin = 60;
				activity.root.addView(second, below);
			}
		});
		Input.settle(getInstrumentation(), 50); // a layout pass
	}

	private View button() {
		View button = new View(activity);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				clicks++;
			}
		});
		return button;
	}

	private void onMain(Runnable runnable) {
		Input.onMain(getInstrumentation(), runnable);
	}

	private void press(final int keyCode, final boolean throughHost) {
		onMain(new Runnable() {
			public void run() {
				for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
					KeyEvent event = new KeyEvent(action, keyCode);
					if (throughHost)
						Input.key(decor, event);
					else
						activity.dispatchKeyEvent(event);
				}
			}
		});
	}

	private void touchTheWindow() {
		onMain(new Runnable() {
			public void run() {
				Input.hostTouch(decor, Input.touch(MotionEvent.ACTION_DOWN, 500, 500));
				Input.hostTouch(decor, Input.touch(MotionEvent.ACTION_UP, 500, 500));
			}
		});
	}

	public void testBackReachesOnBackPressed() {
		press(KeyEvent.KEYCODE_BACK, false);
		assertEquals(1, activity.backs);
	}

	public void testKeyListenerOfTheFocusedViewGoesFirst() {
		final int[] heard = new int[1];
		onMain(new Runnable() {
			public void run() {
				first.setFocusableInTouchMode(true);
				first.setOnKeyListener(new View.OnKeyListener() {
					public boolean onKey(View v, int keyCode, KeyEvent event) {
						heard[0]++;
						return keyCode == KeyEvent.KEYCODE_A;
					}
				});
				first.requestFocus();
			}
		});
		int downs = activity.keyDowns;
		press(KeyEvent.KEYCODE_A, false);
		assertEquals(2, heard[0]);
		assertEquals("the activity doesn't get a consumed key", downs, activity.keyDowns);
	}

	public void testEnterClicksTheFocusedView() {
		onMain(new Runnable() {
			public void run() {
				second.setFocusableInTouchMode(true);
				second.requestFocus();
			}
		});
		press(KeyEvent.KEYCODE_ENTER, false);
		assertEquals(1, clicks);
	}

	public void testDialogTakesBackAndCancels() {
		final Dialog[] dialog = new Dialog[1];
		final int[] cancels = new int[1];
		onMain(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity);
				dialog[0].setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface d) {
						cancels[0]++;
					}
				});
				dialog[0].show();
			}
		});
		press(KeyEvent.KEYCODE_BACK, true);
		Input.settle(getInstrumentation(), 50);
		assertEquals(1, cancels[0]);
		assertFalse(dialog[0].isShowing());
		assertEquals("the activity under the dialog keeps Back", 0, activity.backs);
	}

	public void testEscapeFallsBackToBack() {
		press(KeyEvent.KEYCODE_ESCAPE, true);
		assertEquals(1, activity.backs);
	}

	public void testTabLeavesTouchModeThenMovesFocus() {
		touchTheWindow();
		onMain(new Runnable() {
			public void run() {
				first.setFocusable(true);
				second.setFocusable(true);
			}
		});
		assertTrue(first.isInTouchMode());
		press(KeyEvent.KEYCODE_TAB, true);
		assertFalse(first.isInTouchMode());
		assertTrue("the first Tab only restores focus", first.isFocused());
		press(KeyEvent.KEYCODE_TAB, true);
		assertTrue(second.isFocused());
		touchTheWindow();
		assertTrue(second.isInTouchMode());
		assertFalse("a touch drops focus that touch mode doesn't allow", second.isFocused());
	}
}
