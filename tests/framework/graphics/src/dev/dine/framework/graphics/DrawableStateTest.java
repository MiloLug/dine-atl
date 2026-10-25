package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/* A drawable resource loaded twice is two drawables: Resources makes the second from the first's
 * constant state. */
public class DrawableStateTest extends android.test.InstrumentationTestCase {
	private Resources resources() {
		return getInstrumentation().getTargetContext().getResources();
	}

	private Drawable[] loadTwice(final int id) {
		final Drawable[] loads = new Drawable[2];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				loads[0] = resources().getDrawable(id);
				loads[1] = resources().getDrawable(id);
			}
		});
		return loads;
	}

	private static int color(Drawable selector) {
		return ((ColorDrawable)selector.getCurrent()).getColor();
	}

	public void testSelectorLoadsSelectApart() {
		final Drawable[] loads = loadTwice(R.drawable.selector);
		assertNotSame(loads[0], loads[1]);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				loads[0].setState(new int[] {android.R.attr.state_pressed});
				loads[1].setState(new int[0]);
			}
		});
		assertEquals(0xff00c000, color(loads[0]));
		assertEquals(0xff808080, color(loads[1]));
	}

	// each copy's children are its own: a change to one's child stays there
	public void testSelectorChildrenAreOwn() {
		final Drawable[] loads = loadTwice(R.drawable.selector);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				loads[0].setState(new int[0]);
				loads[1].setState(new int[0]);
				((ColorDrawable)loads[0].getCurrent()).setColor(0xff0000ff);
			}
		});
		assertNotSame(loads[0].getCurrent(), loads[1].getCurrent());
		assertEquals(0xff808080, color(loads[1]));
	}

	// the cache's path for the containers ATL inflates no XML for
	public void testAnimationCopyKeepsItsFrames() {
		final AnimationDrawable[] copy = new AnimationDrawable[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				AnimationDrawable animation = new AnimationDrawable();
				animation.addFrame(new ColorDrawable(0xff808080), 100);
				animation.addFrame(new ColorDrawable(0xff00c000), 100);
				copy[0] = (AnimationDrawable)animation.getConstantState().newDrawable();
			}
		});
		assertEquals(2, copy[0].getNumberOfFrames());
		assertEquals(0xff808080, ((ColorDrawable)copy[0].getFrame(0)).getColor());
		assertEquals(0xff00c000, ((ColorDrawable)copy[0].getFrame(1)).getColor());
	}

	// a child without a constant state can't be copied: the mutated layer keeps it
	public void testLayerWithAStatelessChildMutates() {
		final Drawable child = new Drawable() {
			@Override
			public void draw(Canvas canvas) {}
		};
		final LayerDrawable[] layer = new LayerDrawable[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				layer[0] = new LayerDrawable(new Drawable[] {child});
				assertSame(layer[0], layer[0].mutate());
			}
		});
		assertSame(child, layer[0].getDrawable(0));
	}
}
