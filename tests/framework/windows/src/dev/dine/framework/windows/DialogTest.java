package dev.dine.framework.windows;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// a Dialog's window as AOSP's dialog theme and window manager make it
@SuppressWarnings("deprecation")
public class DialogTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	// themeResId 0: the activity theme's dialogTheme; outside touches as the theme says
	private Dialog shown(final int themeResId) {
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity, themeResId);
				TextView content = new TextView(activity); // wraps: a plain View fills an AT_MOST width
				content.setText("dialog");
				dialog[0].setContentView(content);
				dialog[0].show();
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog[0];
	}

	private void dismiss(final Dialog dialog) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.dismiss();
			}
		});
	}

	private Dialog shown() {
		return shown(0);
	}

	public void testFloatingCenteredDimmed() {
		Dialog dialog = shown();
		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		assertEquals(Gravity.CENTER, params.gravity);
		assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width);
		assertTrue("dim behind", (params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0);
		dismiss(dialog);
	}

	// the dialog theme's windowCloseOnTouchOutside (config_closeDialogWhenTouchOutside)
	public void testTouchOutsideCancels() {
		final Dialog dialog = shown();
		final boolean[] showing = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				long now = SystemClock.uptimeMillis();
				MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, -100, -100, 0);
				MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, -100, -100, 0);
				dialog.dispatchTouchEvent(down);
				dialog.dispatchTouchEvent(up);
				showing[0] = dialog.isShowing();
			}
		});
		assertFalse("still showing after a touch outside", showing[0]);
	}

	// Theme.DeviceDefault.Dialog.MinWidth: a WRAP_CONTENT window at least windowMinWidthMajor/Minor wide
	public void testMinWidthTheme() throws InterruptedException {
		Dialog dialog = shown(android.R.style.Theme_DeviceDefault_Dialog_MinWidth);
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		int screen = activity.getWindow().getDecorView().getWidth();
		assertTrue("width " + decor.getWidth() + " of " + screen, decor.getWidth() >= screen / 2);
		dismiss(dialog);
	}

	// the window's layout pass runs after idle on records: poll as CTS's PollingCheck does
	private static void waitForLayout(View view) throws InterruptedException {
		for (int i = 0; i < 250 && view.getWidth() == 0; i++)
			Thread.sleep(20);
	}

	public void testWindowIsCentered() throws InterruptedException {
		Dialog dialog = shown();
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		View screen = activity.getWindow().getDecorView();
		int[] location = new int[2];
		decor.getLocationOnScreen(location);
		assertTrue("laid out " + decor.getWidth() + "x" + decor.getHeight(), decor.getWidth() > 0 && decor.getHeight() > 0);
		assertEquals((screen.getWidth() - decor.getWidth()) / 2, location[0]);
		assertEquals((screen.getHeight() - decor.getHeight()) / 2, location[1]);
		dismiss(dialog);
	}

	// the window's visibility as its views hear it
	static class Seen extends TextView {
		final List<Integer> visibility = new ArrayList<>();
		final List<Boolean> on_main = new ArrayList<>();

		Seen(android.content.Context context) {
			super(context);
		}

		// View's own only shows the scroll bars
		protected void onWindowVisibilityChanged(int visibility) {
			this.visibility.add(visibility);
			on_main.add(Looper.myLooper() == Looper.getMainLooper());
		}
	}

	private Dialog shownWith(final View content, final int gravity, final int x, final int y, final int width) {
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity);
				dialog[0].setContentView(content);
				WindowManager.LayoutParams params = dialog[0].getWindow().getAttributes();
				params.gravity = gravity;
				params.x = x;
				params.y = y;
				params.width = width;
				dialog[0].getWindow().setAttributes(params);
				dialog[0].show();
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog[0];
	}

	private TextView text(String text) {
		TextView view = new TextView(activity);
		view.setText(text);
		return view;
	}

	public void testGravityBottomLeftWithOffsets() throws InterruptedException {
		Dialog dialog = shownWith(text("bottom"), Gravity.BOTTOM | Gravity.LEFT, 10, 20, WindowManager.LayoutParams.WRAP_CONTENT);
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		View screen = activity.getWindow().getDecorView();
		int[] location = new int[2];
		decor.getLocationOnScreen(location);
		assertEquals(10, location[0]);
		assertEquals(screen.getHeight() - decor.getHeight() - 20, location[1]);
		dismiss(dialog);
	}

	public void testMatchParentWidth() throws InterruptedException {
		Dialog dialog = shownWith(text("wide"), Gravity.CENTER, 0, 0, WindowManager.LayoutParams.MATCH_PARENT);
		View decor = dialog.getWindow().getDecorView();
		waitForLayout(decor);
		assertEquals(activity.getWindow().getDecorView().getWidth(), decor.getWidth());
		dismiss(dialog);
	}

	// AOSP hides an activity's windows while it is stopped, and shows them again
	public void testHiddenUnderTheNextActivity() {
		final Seen seen = new Seen(activity);
		seen.setText("seen");
		Dialog dialog = shownWith(seen, Gravity.CENTER, 0, 0, WindowManager.LayoutParams.WRAP_CONTENT);
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		final PlainActivity next = (PlainActivity)getInstrumentation().startActivitySync(intent);
		getInstrumentation().waitForIdleSync();
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				got[0] = seen.visibility.isEmpty() ? null : seen.visibility.get(seen.visibility.size() - 1);
				next.finish();
			}
		});
		getInstrumentation().waitForIdleSync();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				got[1] = seen.visibility.isEmpty() ? null : seen.visibility.get(seen.visibility.size() - 1);
			}
		});
		assertEquals(View.GONE, got[0]);
		assertEquals(View.VISIBLE, got[1]);
		dismiss(dialog);
	}

	// the window goes with its activity (WindowLeaked): the dismiss throws as AOSP's removeView does,
	// and the Dialog is done by then, as dismissDialog's finally leaves it
	public void testLeakedWhenTheActivityFinishes() throws InterruptedException {
		final Dialog dialog = shown();
		final boolean[] dismissed = new boolean[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
					public void onDismiss(android.content.DialogInterface d) {
						dismissed[0] = true;
					}
				});
				activity.finish();
			}
		});
		for (int i = 0; i < 250 && !activity.isDestroyed(); i++)
			Thread.sleep(20);
		final Object[] got = new Object[4];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				got[0] = dialog.isShowing();
				try {
					dialog.dismiss();
				} catch (IllegalArgumentException e) {
					got[1] = e;
				}
				got[2] = dialog.isShowing();
				try {
					dialog.dismiss();
				} catch (IllegalArgumentException e) {
					got[3] = e;
				}
			}
		});
		for (int i = 0; i < 100 && !dismissed[0]; i++)
			Thread.sleep(20);
		assertEquals(true, got[0]);
		assertNotNull("dismiss of a leaked window", got[1]);
		assertEquals("showing after the throw", false, got[2]);
		assertNull("a second dismiss returns", got[3]);
		assertTrue("the dismiss listener ran", dismissed[0]);
	}

	// no token: an Application context's Dialog
	public void testNoActivityNoWindow() {
		final Object[] got = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Dialog dialog = new Dialog(getInstrumentation().getTargetContext());
				dialog.setContentView(text("tokenless"));
				try {
					dialog.show();
				} catch (RuntimeException e) {
					got[0] = e.getClass().getName();
				}
			}
		});
		assertEquals("android.view.WindowManager$BadTokenException", got[0]);
	}

	// AlertController installs a set view at show, so the window takes features until then
	public void testFeatureAfterSetView() {
		final Object[] got = new Object[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity).create();
				dialog.setView(new View(activity));
				try {
					got[0] = dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
				} catch (RuntimeException e) {
					got[0] = e;
				}
			}
		});
		assertEquals(true, got[0]);
	}

	private static void collect() throws InterruptedException {
		for (int i = 0; i < 4; i++) {
			Runtime.getRuntime().gc();
			System.runFinalization();
			Thread.sleep(20);
		}
	}

	// the window manager holds a shown window: an app needs no reference to its dialog
	public void testUnreferencedDialogStaysShown() throws InterruptedException {
		final java.lang.ref.WeakReference<?>[] ref = new java.lang.ref.WeakReference<?>[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Dialog dialog = new Dialog(activity);
				dialog.setContentView(text("unreferenced"));
				dialog.show();
				ref[0] = new java.lang.ref.WeakReference<Dialog>(dialog);
			}
		});
		collect();
		final Dialog dialog = (Dialog)ref[0].get();
		assertNotNull("collected while shown", dialog);
		dismiss(dialog);
	}

	// a finished activity that kept its dialog goes once nothing else holds them
	public void testFinishedOwnerIsCollected() throws InterruptedException {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		final PlainActivity[] owner = {(PlainActivity)getInstrumentation().startActivitySync(intent)};
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Dialog dialog = new Dialog(owner[0]);
				dialog.setContentView(new TextView(owner[0]));
				owner[0].kept = dialog;
				dialog.show();
				dialog.dismiss();
				owner[0].finish();
			}
		});
		getInstrumentation().waitForIdleSync();
		for (int i = 0; i < 250 && !owner[0].isDestroyed(); i++)
			Thread.sleep(20);
		java.lang.ref.WeakReference<PlainActivity> ref = new java.lang.ref.WeakReference<>(owner[0]);
		owner[0] = null;
		for (int i = 0; i < 20 && ref.get() != null; i++) { // a toolkit frees some of a window in posted parts
			getInstrumentation().waitForIdleSync();
			collect();
		}
		assertNull("the finished activity is still held", ref.get());
	}

	// an overlay is the app's own window: it outlives the activity whose context made it
	public void testOverlayOutlivesItsActivity() throws InterruptedException {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		final PlainActivity second = (PlainActivity)getInstrumentation().startActivitySync(intent);
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(second);
				dialog[0].setContentView(new TextView(second));
				dialog[0].getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
				dialog[0].show();
				second.finish();
			}
		});
		for (int i = 0; i < 250 && !second.isDestroyed(); i++)
			Thread.sleep(20);
		getInstrumentation().waitForIdleSync();
		final Object[] got = new Object[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				got[0] = dialog[0].getWindow().getDecorView().getWindowVisibility();
				try {
					dialog[0].dismiss();
				} catch (IllegalArgumentException e) {
					got[1] = e;
				}
			}
		});
		assertEquals(View.VISIBLE, got[0]);
		assertNull(got[1]);
	}

	private int dp(int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
	}

	private static String padding(View view) {
		return view.getPaddingLeft() + "," + view.getPaddingTop() + "," + view.getPaddingRight() + "," + view.getPaddingBottom();
	}

	// dialog_background_material insets its card 16dp: the window pads its content by as much
	public void testCardPadsTheContent() throws InterruptedException {
		Dialog dialog = shown();
		View decor = dialog.getWindow().getDecorView();
		View content = ((ViewGroup)decor).getChildAt(0);
		for (int i = 0; i < 250 && content.getWidth() == 0; i++)
			Thread.sleep(20);
		int inset = dp(16);
		assertEquals(inset + "," + inset + "," + inset + "," + inset, padding(decor));
		assertEquals(inset, content.getLeft());
		assertEquals(inset, content.getTop());
		assertEquals(decor.getWidth() - inset, content.getRight());
		dismiss(dialog);
	}

	// DecorView's: the window's padding is its background's
	public void testColorBackgroundDropsThePadding() {
		final Dialog dialog = shown();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			}
		});
		assertEquals("0,0,0,0", padding(dialog.getWindow().getDecorView()));
		dismiss(dialog);
	}

	// the theme's background goes on once: one taken off stays off
	public void testRemovedBackgroundStaysOff() {
		final Dialog dialog = shown();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.getWindow().setBackgroundDrawable(null);
				dialog.setContentView(new TextView(activity));
			}
		});
		View decor = dialog.getWindow().getDecorView();
		assertNull(decor.getBackground());
		assertEquals("0,0,0,0", padding(decor));
		dismiss(dialog);
	}

	private void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
		for (int i = 0; i < 100 && !condition.call(); i++)
			Thread.sleep(20);
	}

	// AOSP's dismiss() posts to the dialog's thread from another one: the registry and the views run on the main looper
	public void testDismissFromAnotherThreadRunsOnTheMainLooper() throws Exception {
		final Seen seen = new Seen(activity);
		seen.setText("seen");
		final Dialog dialog = shownWith(seen, Gravity.CENTER, 0, 0, WindowManager.LayoutParams.WRAP_CONTENT);
		dialog.dismiss(); // this thread is the instrumentation's
		waitFor(new java.util.concurrent.Callable<Boolean>() {
			public Boolean call() {
				return seen.visibility.size() > 1;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertEquals(View.GONE, (int)seen.visibility.get(seen.visibility.size() - 1));
		assertEquals("the views heard it on the main looper", Boolean.TRUE, seen.on_main.get(seen.on_main.size() - 1));
		assertFalse(dialog.isShowing());
	}

	// hide() is the window manager's too, here: the registry is the main thread's
	public void testHideFromAnotherThreadRunsOnTheMainLooper() throws Exception {
		final Seen seen = new Seen(activity);
		seen.setText("seen");
		final Dialog dialog = shownWith(seen, Gravity.CENTER, 0, 0, WindowManager.LayoutParams.WRAP_CONTENT);
		dialog.hide();
		waitFor(new java.util.concurrent.Callable<Boolean>() {
			public Boolean call() {
				return seen.visibility.size() > 1;
			}
		});
		getInstrumentation().waitForIdleSync();
		assertEquals(View.GONE, (int)seen.visibility.get(seen.visibility.size() - 1));
		assertEquals("the views heard it on the main looper", Boolean.TRUE, seen.on_main.get(seen.on_main.size() - 1));
		dismiss(dialog);
	}

	// AOSP's Dialog.cancel() sends the cancel message, then dismisses: the listener runs after the removal
	public void testCancelListenerRunsAfterTheRemoval() throws Exception {
		final List<String> got = new ArrayList<>();
		final Dialog[] dialog = new Dialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = new Dialog(activity);
				dialog[0].setContentView(text("cancel"));
				dialog[0].setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
					public void onCancel(android.content.DialogInterface d) {
						got.add("cancel showing=" + ((Dialog)d).isShowing());
					}
				});
				dialog[0].setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
					public void onDismiss(android.content.DialogInterface d) {
						got.add("dismiss");
					}
				});
				dialog[0].show();
			}
		});
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0].cancel();
				got.add("returned");
			}
		});
		waitFor(new java.util.concurrent.Callable<Boolean>() {
			public Boolean call() {
				return got.size() >= 3;
			}
		});
		assertEquals("[returned, cancel showing=false, dismiss]", got.toString());
	}

	// AOSP's hide() sets the decor GONE, which isShowing() reads
	public void testHideMakesTheDecorGone() {
		final Dialog dialog = shown();
		final Object[] got = new Object[4];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.hide();
				got[0] = dialog.getWindow().getDecorView().getVisibility();
				got[1] = dialog.isShowing();
				dialog.show();
				got[2] = dialog.isShowing();
				got[3] = dialog.getWindow().getDecorView().getVisibility();
			}
		});
		assertEquals(View.GONE, got[0]);
		assertEquals("showing while hidden", false, got[1]);
		assertEquals(true, got[2]);
		assertEquals(View.VISIBLE, got[3]);
		dismiss(dialog);
	}

	// a hide and a show leave the window where it was added: only a new add goes on top
	public void testHideThenShowKeepsItsPlace() {
		final Dialog first = shown();
		final Dialog second = shown();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				first.hide();
				first.show();
			}
		});
		getInstrumentation().waitForIdleSync();
		assertSame("the newer dialog still takes the keys", second.getWindow(), android.view.Window.atlKeyWindow(activity));
		dismiss(first);
		dismiss(second);
	}

	// the window's owner is the dialog, whatever callback AppCompat puts in front of it
	static class Probe extends Dialog {
		final List<Boolean> visible = new ArrayList<>();

		Probe(android.content.Context context) {
			super(context);
		}

		@Override
		public void atlWindowVisible(boolean visible) {
			this.visible.add(visible);
			super.atlWindowVisible(visible);
		}
	}

	public void testReplacedCallbackStillShowsAndHidesTheDialog() {
		final Probe[] probe = new Probe[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				probe[0] = new Probe(activity);
				probe[0].setContentView(text("probe"));
				probe[0].getWindow().setCallback(new CallbackWrapper(probe[0]));
				probe[0].show();
			}
		});
		getInstrumentation().waitForIdleSync();
		dismiss(probe[0]);
		assertEquals("[true, false]", probe[0].visible.toString());
	}

	// PhoneWindow.generateLayout: a non-floating window is laid out in the whole screen, a floating one in the content frame
	public void testLayoutFlagsFollowTheTheme() {
		final int mask = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		final int[] flags = new int[3];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				flags[0] = new Dialog(activity).getWindow().getAttributes().flags & mask;
				Dialog plain = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
				flags[1] = plain.getWindow().getAttributes().flags & mask;
				plain.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
				flags[2] = plain.getWindow().getAttributes().flags & mask;
			}
		});
		assertEquals("floating", 0, flags[0]);
		assertEquals("not floating", mask, flags[1]);
		assertEquals("one the app cleared stays cleared", WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR, flags[2]);
	}

	// AOSP's show() calls onStart() after onCreate(), dismissDialog() onStop() in its finally
	static class Lifecycle extends Dialog {
		final List<String> calls = new ArrayList<>();

		Lifecycle(android.content.Context context) {
			super(context);
		}

		@Override
		protected void onCreate(android.os.Bundle state) {
			calls.add("create");
		}

		protected void onStart() {
			calls.add("start");
		}

		protected void onStop() {
			calls.add("stop " + isShowing());
		}
	}

	public void testOnStartAndOnStopFollowShowAndDismiss() {
		final Lifecycle[] dialog = new Lifecycle[1];
		onMain(new Runnable() {
			public void run() {
				dialog[0] = new Lifecycle(activity);
				dialog[0].setContentView(text("lifecycle"));
				dialog[0].show();
				dialog[0].hide();
				dialog[0].show(); // a showing dialog: no new start
				dialog[0].dismiss();
				dialog[0].show(); // created already: a start only
				dialog[0].dismiss();
			}
		});
		assertEquals("[create, start, stop false, start, stop false]", dialog[0].calls.toString());
	}

	// a dialog that went with its activity is stopped as well: the throw doesn't skip onStop
	public void testOnStopRunsForALeakedDialog() throws InterruptedException {
		final Lifecycle[] dialog = new Lifecycle[1];
		onMain(new Runnable() {
			public void run() {
				dialog[0] = new Lifecycle(activity);
				dialog[0].setContentView(text("leaked"));
				dialog[0].show();
				activity.finish();
			}
		});
		for (int i = 0; i < 250 && !activity.isDestroyed(); i++)
			Thread.sleep(20);
		onMain(new Runnable() {
			public void run() {
				try {
					dialog[0].dismiss();
				} catch (IllegalArgumentException e) {
				}
			}
		});
		assertEquals("[create, start, stop false]", dialog[0].calls.toString());
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
		getInstrumentation().waitForIdleSync();
	}
}
