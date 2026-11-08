package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

// WindowManager.addView windows in the app's registry, with AOSP's token rules
public class WindowManagerTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;

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

	private static WindowManager.LayoutParams panel(int x, int y) {
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(100, 50, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
		                                                                   0, android.graphics.PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.LEFT | Gravity.TOP;
		params.x = x;
		params.y = y;
		return params;
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
		getInstrumentation().waitForIdleSync();
	}

	private boolean at(final View view, final int x, final int y) throws InterruptedException {
		return waitFor(new Condition() {
			public boolean holds() {
				int[] location = new int[2];
				view.getLocationOnScreen(location);
				return location[0] == x && location[1] == y && view.getWidth() == 100 && view.getHeight() == 50;
			}
		});
	}

	public void testPanelOverTheActivityAtItsPlace() throws InterruptedException {
		final View view = new View(activity);
		onMain(new Runnable() {
			public void run() {
				activity.getWindowManager().addView(view, panel(30, 40));
			}
		});
		assertTrue("attached", view.isAttachedToWindow());
		assertTrue("at 30,40 at 100x50", at(view, 30, 40));
		assertSame("the activity's window is its application window", activity.getWindow().getDecorView().getWindowToken(),
		           view.getApplicationWindowToken());
		onMain(new Runnable() {
			public void run() {
				activity.getWindowManager().updateViewLayout(view, panel(60, 40));
			}
		});
		assertTrue("moved to 60,40", at(view, 60, 40));
		onMain(new Runnable() {
			public void run() {
				activity.getWindowManager().removeView(view);
			}
		});
		assertFalse("detached", view.isAttachedToWindow());
	}

	public void testRemovingAViewNotAddedThrows() {
		final RuntimeException[] thrown = new RuntimeException[1];
		onMain(new Runnable() {
			public void run() {
				try {
					activity.getWindowManager().removeView(new View(activity));
				} catch (IllegalArgumentException e) {
					thrown[0] = e;
				}
			}
		});
		assertNotNull("IllegalArgumentException", thrown[0]);
	}

	public void testAppWindowNeedsAnActivityToken() {
		final RuntimeException[] thrown = new RuntimeException[1];
		onMain(new Runnable() {
			public void run() {
				WindowManager manager = (WindowManager)activity.getApplicationContext().getSystemService("window");
				try {
					manager.addView(new View(activity), new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION));
				} catch (WindowManager.BadTokenException e) {
					thrown[0] = e;
				}
			}
		});
		assertNotNull("BadTokenException", thrown[0]);
	}

	// AOSP attaches a sub-panel to any live window, one of no activity too
	public void testPanelOfAWindowWithoutAnOwner() {
		final View toast = new View(activity), panel = new View(activity);
		final RuntimeException[] thrown = new RuntimeException[1];
		onMain(new Runnable() {
			public void run() {
				WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_TOAST);
				params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
				activity.getWindowManager().addView(toast, params);
				WindowManager.LayoutParams sub = panel(0, 0);
				sub.token = toast.getWindowToken();
				try {
					activity.getWindowManager().addView(panel, sub);
				} catch (RuntimeException e) {
					thrown[0] = e;
				}
			}
		});
		assertNull("no exception", thrown[0]);
		assertTrue(panel.isAttachedToWindow());
		onMain(new Runnable() {
			public void run() {
				activity.getWindowManager().removeView(panel);
				activity.getWindowManager().removeView(toast);
			}
		});
	}

	public void testWindowTokenStaysTheWindows() {
		final Object[] tokens = new Object[3];
		onMain(new Runnable() {
			public void run() {
				View content = activity.getWindow().getDecorView();
				tokens[0] = content.getWindowToken();
				tokens[1] = content.getWindowToken();
				tokens[2] = new View(activity).getWindowToken();
			}
		});
		assertNotNull("an attached view has a token", tokens[0]);
		assertSame("the same token", tokens[0], tokens[1]);
		assertNull("a detached view has none", tokens[2]);
	}

	// a panel attached to a dialog's window: placed in its frame, gone when the dialog goes
	public void testDialogPanelGoesWithTheDialog() throws InterruptedException {
		final Dialog[] dialog = new Dialog[1];
		final TextView content = new TextView(activity);
		final View view = new View(activity);
		onMain(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity);
				content.setText("dialog");
				dialog[0].setContentView(content);
				dialog[0].show();
			}
		});
		assertTrue("dialog laid out", waitFor(new Condition() {
			public boolean holds() {
				return content.getWidth() > 0;
			}
		}));
		final int[] origin = new int[2];
		onMain(new Runnable() {
			public void run() {
				dialog[0].getWindow().getDecorView().getLocationOnScreen(origin);
				WindowManager.LayoutParams params = panel(10, 5);
				params.token = content.getWindowToken();
				activity.getWindowManager().addView(view, params);
			}
		});
		assertTrue("in the dialog's frame", at(view, origin[0] + 10, origin[1] + 5));
		assertEquals(dialog[0].getWindow().getDecorView().getWindowToken(), view.getApplicationWindowToken());
		onMain(new Runnable() {
			public void run() {
				dialog[0].dismiss();
			}
		});
		assertTrue("hidden with its dialog", waitFor(new Condition() {
			public boolean holds() {
				return view.getWindowVisibility() != View.VISIBLE;
			}
		}));
		onMain(new Runnable() {
			public void run() {
				activity.getWindowManager().removeView(view);
			}
		});
	}
}
