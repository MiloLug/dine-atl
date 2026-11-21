package dev.dine.framework.basics;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

@SuppressWarnings("deprecation")
public class WidgetsTest extends android.test.InstrumentationTestCase {
	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
	}

	public void testProgressBarKeepsIndeterminate() {
		final Context context = getInstrumentation().getTargetContext();
		final boolean[] state = new boolean[2];
		onMain(new Runnable() {
			public void run() {
				ProgressBar bar = new ProgressBar(context);
				bar.setIndeterminate(false);
				state[0] = bar.isIndeterminate();
				bar.setIndeterminate(true);
				state[1] = bar.isIndeterminate();
			}
		});
		assertFalse(state[0]);
		assertTrue(state[1]);
	}

	public void testImageViewTakesADrawable() {
		final Context context = getInstrumentation().getTargetContext();
		final Object[] drawable = new Object[1];
		onMain(new Runnable() {
			public void run() {
				ImageView image = new ImageView(context);
				ColorDrawable color = new ColorDrawable(0xFF00FF00);
				image.setImageDrawable(color);
				image.setScaleType(ImageView.ScaleType.CENTER_CROP);
				image.setVisibility(View.INVISIBLE);
				drawable[0] = image.getDrawable();
			}
		});
		assertTrue(drawable[0] instanceof ColorDrawable);
	}

	// AOSP's: a color filter and a tint each reach the drawable, setColorFilter(int) as SRC_ATOP
	public void testImageViewKeepsFilterAndTint() {
		final Context context = getInstrumentation().getTargetContext();
		final Object[] got = new Object[4];
		onMain(new Runnable() {
			public void run() {
				ImageView image = new ImageView(context);
				image.setImageBitmap(android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888));
				image.setColorFilter(0xffff0000);
				android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(0xff00ff00);
				image.setImageTintList(tint);
				got[0] = image.getColorFilter();
				got[1] = image.getDrawable().getColorFilter();
				got[2] = image.getImageTintList() == tint;
				image.clearColorFilter();
				got[3] = image.getDrawable().getColorFilter();
			}
		});
		android.graphics.PorterDuffColorFilter filter = (android.graphics.PorterDuffColorFilter)got[0];
		assertEquals(0xffff0000, filter.getColor());
		assertEquals(android.graphics.PorterDuff.Mode.SRC_ATOP, filter.getMode());
		assertSame(got[0], got[1]);
		assertEquals(Boolean.TRUE, got[2]);
		assertNull(got[3]);
	}

	// a wrap_content ImageView that gets its image after its first layout takes the image's size
	public void testImageViewResizesToItsImage() {
		final Context context = getInstrumentation().getTargetContext();
		final WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(new android.content.Intent(context, WindowActivity.class));
		final ImageView[] image = new ImageView[1];
		onMain(new Runnable() {
			public void run() {
				image[0] = new ImageView(context);
				activity.root.addView(image[0], new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
			}
		});
		settled(image[0]);
		final boolean[] asked = new boolean[1];
		onMain(new Runnable() {
			public void run() {
				image[0].setImageBitmap(android.graphics.Bitmap.createBitmap(100, 80, android.graphics.Bitmap.Config.ARGB_8888));
				asked[0] = image[0].isLayoutRequested();
			}
		});
		assertTrue("setImageBitmap asks for a layout", asked[0]);
		settled(image[0]);
		assertEquals(100, image[0].getWidth());
		assertEquals(80, image[0].getHeight());
	}

	private void settled(View view) {
		getInstrumentation().waitForIdleSync();
		for (int i = 0; i < 100 && view.isLayoutRequested(); i++)
			android.os.SystemClock.sleep(20);
	}

	private static class StatefulDrawable extends ColorDrawable {
		StatefulDrawable() {
			super(0xFF00FF00);
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		protected boolean onStateChange(int[] state) {
			return true;
		}
	}

	// as View.setBackgroundDrawable does: a stateful drawable starts in the view's state
	public void testImageViewGivesAStatefulDrawableItsState() {
		final Context context = getInstrumentation().getTargetContext();
		final Object[] got = new Object[2];
		onMain(new Runnable() {
			public void run() {
				ImageView image = new ImageView(context);
				StatefulDrawable drawable = new StatefulDrawable();
				image.setImageDrawable(drawable);
				got[0] = java.util.Arrays.toString(drawable.getState());
				got[1] = java.util.Arrays.toString(image.getDrawableState());
			}
		});
		assertEquals(got[1], got[0]);
		assertFalse("an empty state tells nothing", got[1].equals("[]"));
	}
}
