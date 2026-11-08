package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

// a Dialog's window observes the app window's passes while it shows, as AOSP's own traversals
public class WindowStateTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;
	private Dialog dialog;
	private volatile int layouts, predraws, activity_layouts;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog = new Dialog(activity);
				TextView content = new TextView(activity);
				content.setText("state");
				dialog.setContentView(content);
				ViewTreeObserver observer = content.getViewTreeObserver();
				observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					public void onGlobalLayout() {
						layouts++;
					}
				});
				observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
					public boolean onPreDraw() {
						predraws++;
						return true;
					}
				});
				activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					public void onGlobalLayout() {
						activity_layouts++;
					}
				});
				layouts = predraws = 0; // ATL's addOnPreDrawListener calls the listener at once
				dialog.show();
			}
		});
		getInstrumentation().waitForIdleSync();
	}

	@Override
	protected void tearDown() throws Exception {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.dismiss();
			}
		});
		super.tearDown();
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

	public void testShownDialogObservesLayout() throws InterruptedException {
		assertTrue("no global layout in the dialog", waitFor(new Condition() {
			public boolean holds() {
				return layouts > 0;
			}
		}));
	}

	public void testShownDialogObservesDraws() throws InterruptedException {
		assertTrue("no pre-draw in the dialog", waitFor(new Condition() {
			public boolean holds() {
				return predraws > 0;
			}
		}));
	}

	public void testHiddenDialogStopsObserving() throws InterruptedException {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.hide();
			}
		});
		getInstrumentation().waitForIdleSync();
		Thread.sleep(100); // the hide's own pass
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				layouts = predraws = activity_layouts = 0;
				activity.getWindow().getDecorView().requestLayout();
			}
		});
		assertTrue("no pass in the activity", waitFor(new Condition() {
			public boolean holds() {
				return activity_layouts > 0;
			}
		}));
		assertEquals("global layouts while hidden", 0, layouts);
		assertEquals("pre-draws while hidden", 0, predraws);
	}
}
