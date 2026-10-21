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
}
