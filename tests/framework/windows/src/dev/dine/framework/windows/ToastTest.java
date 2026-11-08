package dev.dine.framework.windows;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

// AOSP's Toast: one at a time from the app's queue, makeText's message view, the Looper rule
public class ToastTest extends android.test.InstrumentationTestCase {
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

	private static boolean waitFor(Condition condition, int ms) throws InterruptedException {
		for (int i = 0; i < ms / 20 && !condition.holds(); i++)
			Thread.sleep(20);
		return condition.holds();
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
		getInstrumentation().waitForIdleSync();
	}

	private Toast viewToast(final View view) {
		final Toast[] toast = new Toast[1];
		onMain(new Runnable() {
			public void run() {
				toast[0] = new Toast(activity);
				toast[0].setView(view);
				toast[0].setDuration(Toast.LENGTH_SHORT);
			}
		});
		return toast[0];
	}

	private static Condition attached(final View view, final boolean attached) {
		return new Condition() {
			public boolean holds() {
				return view.isAttachedToWindow() == attached;
			}
		};
	}

	public void testOneAtATime() throws InterruptedException {
		final View first = new View(activity), second = new View(activity);
		final Toast one = viewToast(first), two = viewToast(second);
		onMain(new Runnable() {
			public void run() {
				one.show();
				two.show();
			}
		});
		assertTrue("the first shows", waitFor(attached(first, true), 500));
		assertFalse("the second waits", second.isAttachedToWindow());
		assertTrue("the second after the first's 2 s", waitFor(attached(second, true), 3000));
		assertFalse("the first went", first.isAttachedToWindow());
		onMain(new Runnable() {
			public void run() {
				two.cancel();
			}
		});
		assertTrue("canceled", waitFor(attached(second, false), 500));
	}

	public void testCancelShowsTheNext() throws InterruptedException {
		final View first = new View(activity), second = new View(activity);
		final Toast one = viewToast(first), two = viewToast(second);
		onMain(new Runnable() {
			public void run() {
				one.show();
				two.show();
			}
		});
		assertTrue(waitFor(attached(first, true), 500));
		onMain(new Runnable() {
			public void run() {
				one.cancel();
			}
		});
		assertTrue("the next at once", waitFor(attached(second, true), 500));
		onMain(new Runnable() {
			public void run() {
				two.cancel();
			}
		});
	}

	public void testMakeTextHasItsMessageView() {
		onMain(new Runnable() {
			public void run() {
				Toast toast = Toast.makeText(activity, "hello", Toast.LENGTH_LONG);
				TextView message = (TextView)toast.getView().findViewById(android.R.id.message);
				assertEquals("hello", String.valueOf(message.getText()));
				toast.setText("again");
				assertEquals("again", String.valueOf(message.getText()));
				assertEquals(Toast.LENGTH_LONG, toast.getDuration());
			}
		});
	}

	public void testSetTextNeedsMakeText() {
		final boolean[] threw = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				Toast toast = new Toast(activity);
				try {
					toast.setText("text");
				} catch (RuntimeException e) {
					threw[0] = true;
				}
				try {
					toast.show();
				} catch (RuntimeException e) {
					threw[1] = true;
				}
			}
		});
		assertTrue("setText without makeText", threw[0]);
		assertTrue("show without a view", threw[1]);
	}

	public void testNoLooperThrows() throws InterruptedException {
		final boolean[] threw = new boolean[1];
		Thread thread = new Thread() {
			public void run() {
				try {
					Toast.makeText(activity, "no looper", Toast.LENGTH_SHORT);
				} catch (RuntimeException e) {
					threw[0] = true;
				}
			}
		};
		thread.start();
		thread.join();
		assertTrue(threw[0]);
	}
}
