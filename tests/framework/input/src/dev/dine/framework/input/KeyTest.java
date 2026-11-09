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

	/* A shown soft keyboard takes the first Back and the request ends with it; with an exposed hard
	 * keyboard none shows, Back reaches the app and the request stands, as on Android. */
	public void testBackAfterShowSoftInput() {
		final android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
		final android.widget.EditText[] edit = new android.widget.EditText[1];
		onMain(new Runnable() {
			public void run() {
				edit[0] = new android.widget.EditText(activity);
				activity.root.addView(edit[0], new FrameLayout.LayoutParams(100, 50));
				edit[0].requestFocus();
				imm.showSoftInput(edit[0], 0);
			}
		});
		press(KeyEvent.KEYCODE_BACK, true);
		final boolean[] hid = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				hid[0] = imm.hideSoftInputFromWindow(edit[0].getWindowToken(), 0);
			}
		});
		boolean hard = activity.getResources().getConfiguration().hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO;
		assertEquals("backs", hard ? 1 : 0, activity.backs);
		assertEquals("a request after Back", hard, hid[0]);
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

	// Android 10's isConfirmKey takes the keypad's Enter and Space too
	public void testNumpadEnterAndSpaceClickTheFocusedView() {
		onMain(new Runnable() {
			public void run() {
				second.setFocusableInTouchMode(true);
				second.requestFocus();
			}
		});
		press(KeyEvent.KEYCODE_NUMPAD_ENTER, false);
		press(KeyEvent.KEYCODE_SPACE, false);
		assertEquals(2, clicks);
	}

	// Generic.kcm: NUMPAD_ENTER no view takes goes again as ENTER
	public void testNumpadEnterFallsBackToEnter() {
		final java.util.List<String> keys = new java.util.ArrayList<>();
		onMain(new Runnable() {
			public void run() {
				View plain = new View(activity);
				plain.setFocusableInTouchMode(true);
				plain.setOnKeyListener(new View.OnKeyListener() {
					public boolean onKey(View v, int keyCode, KeyEvent event) {
						keys.add(event.getAction() + ":" + keyCode + ((event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0 ? " fallback" : ""));
						return keyCode == KeyEvent.KEYCODE_ENTER;
					}
				});
				activity.root.addView(plain, new FrameLayout.LayoutParams(10, 10));
				plain.requestFocus();
			}
		});
		press(KeyEvent.KEYCODE_NUMPAD_ENTER, true);
		assertEquals(java.util.Arrays.asList("0:160", "0:66 fallback", "1:160", "1:66 fallback"), keys);
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
