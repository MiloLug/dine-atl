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
}
