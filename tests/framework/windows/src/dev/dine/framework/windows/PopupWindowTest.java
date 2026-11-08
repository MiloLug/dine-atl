package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

// AOSP's PopupWindow over WindowManager.addView: placed at its anchor, flipped above it without
// room below, its background around its content
public class PopupWindowTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;
	private Button anchor;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	private interface Condition {
		boolean holds();
	}

	// the window's layout pass runs after idle on records: poll as CTS's PollingCheck does
	private static boolean waitFor(Condition condition) throws InterruptedException {
		for (int i = 0; i < 100 && !condition.holds(); i++)
			Thread.sleep(20);
		return condition.holds();
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
		getInstrumentation().waitForIdleSync();
	}

	// an anchor of 100x40 at left, top in the activity
	private void anchorAt(final int left, final int top) throws InterruptedException {
		onMain(new Runnable() {
			public void run() {
				FrameLayout content = new FrameLayout(activity);
				anchor = new Button(activity);
				anchor.setText("anchor");
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(100, 40, Gravity.LEFT | Gravity.TOP);
				params.leftMargin = left;
				params.topMargin = top;
				content.addView(anchor, params);
				activity.setContentView(content);
			}
		});
		assertTrue("anchor laid out", at(anchor, left, top));
	}

	private static boolean at(final View view, final int x, final int y) throws InterruptedException {
		return waitFor(new Condition() {
			public boolean holds() {
				int[] location = new int[2];
				view.getLocationOnScreen(location);
				return view.getWidth() > 0 && location[0] == x && location[1] == y;
			}
		});
	}

	private PopupWindow shown(final View content, final int width, final int height) {
		final PopupWindow[] popup = new PopupWindow[1];
		onMain(new Runnable() {
			public void run() {
				popup[0] = new PopupWindow(content, width, height);
				popup[0].showAsDropDown(anchor);
			}
		});
		return popup[0];
	}

	private void dismiss(final PopupWindow popup) {
		onMain(new Runnable() {
			public void run() {
				popup.dismiss();
			}
		});
	}

	public void testBelowItsAnchor() throws InterruptedException {
		anchorAt(50, 50);
		TextView content = new TextView(activity);
		content.setText("below");
		PopupWindow popup = shown(content, 120, 60);
		assertTrue("showing", popup.isShowing());
		assertTrue("at the anchor's bottom left", at(content, 50, 90));
		assertFalse(popup.isAboveAnchor());
		dismiss(popup);
	}

	public void testAboveItsAnchorWithoutRoomBelow() throws InterruptedException {
		int height = activity.getResources().getDisplayMetrics().heightPixels;
		anchorAt(50, height - 60);
		TextView content = new TextView(activity);
		content.setText("above");
		PopupWindow popup = shown(content, 120, 100);
		assertTrue("above the anchor", at(content, 50, height - 60 - 100));
		assertTrue(popup.isAboveAnchor());
		dismiss(popup);
	}

	// PopupBackgroundView holds the background: the content keeps its own padding
	public void testBackgroundAroundTheContent() throws InterruptedException {
		anchorAt(50, 50);
		final TextView content = new TextView(activity);
		content.setText("padded");
		content.setPadding(5, 5, 5, 5);
		final PopupWindow[] popup = new PopupWindow[1];
		onMain(new Runnable() {
			public void run() {
				popup[0] = new PopupWindow(content, 120, 60);
				popup[0].setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.RED), 10));
				popup[0].showAsDropDown(anchor);
			}
		});
		assertTrue("inside the background's padding", at(content, 60, 100));
		assertEquals(5, content.getPaddingLeft());
		assertNull(content.getBackground());
		dismiss(popup[0]);
	}

	public void testFocusablePopupClosesOnBack() throws InterruptedException {
		anchorAt(50, 50);
		final TextView content = new TextView(activity);
		content.setText("back");
		final PopupWindow[] popup = new PopupWindow[1];
		final boolean[] dismissed = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				popup[0] = new PopupWindow(content, 120, 60, true);
				popup[0].setOnDismissListener(new PopupWindow.OnDismissListener() {
					public void onDismiss() {
						dismissed[0] = true;
					}
				});
				popup[0].showAsDropDown(anchor);
			}
		});
		assertTrue("laid out", at(content, 50, 90));
		onMain(new Runnable() {
			public void run() {
				View root = content.getRootView();
				long now = SystemClock.uptimeMillis();
				root.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0));
				root.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0));
			}
		});
		assertFalse("closed", popup[0].isShowing());
		assertTrue("its dismiss listener", dismissed[0]);
	}

	// a popup of a dialog's view is attached to the dialog's window, and goes with it
	public void testPopupGoesWithItsDialog() throws InterruptedException {
		final Dialog[] dialog = new Dialog[1];
		final Button inner = new Button(activity);
		inner.setText("inner");
		onMain(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity);
				dialog[0].setContentView(inner);
				dialog[0].show();
			}
		});
		assertTrue("dialog laid out", waitFor(new Condition() {
			public boolean holds() {
				return inner.getWidth() > 0;
			}
		}));
		final TextView content = new TextView(activity);
		content.setText("in dialog");
		final PopupWindow[] popup = new PopupWindow[1];
		onMain(new Runnable() {
			public void run() {
				popup[0] = new PopupWindow(content, 120, 60);
				popup[0].showAsDropDown(inner);
			}
		});
		final int[] location = new int[2];
		inner.getLocationOnScreen(location);
		assertTrue("below the dialog's button", at(content, location[0], location[1] + inner.getHeight()));
		onMain(new Runnable() {
			public void run() {
				dialog[0].dismiss();
			}
		});
		assertTrue("hidden with its dialog", waitFor(new Condition() {
			public boolean holds() {
				return content.getWindowVisibility() != View.VISIBLE;
			}
		}));
		dismiss(popup[0]);
	}
}
