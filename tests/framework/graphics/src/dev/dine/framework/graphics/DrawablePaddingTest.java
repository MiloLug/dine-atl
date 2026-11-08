package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.StateSet;

// what drawables report as padding and minimum size, which views take from their background
public class DrawablePaddingTest extends android.test.InstrumentationTestCase {
	private Resources resources() {
		return getInstrumentation().getTargetContext().getResources();
	}

	private static Rect padding(Drawable drawable, boolean padded) {
		Rect padding = new Rect(9, 9, 9, 9);
		assertEquals("padded", padded, drawable.getPadding(padding));
		return padding;
	}

	public void testNoPaddingIsZero() {
		assertEquals(new Rect(), padding(new ColorDrawable(0xff000000), false));
	}

	// a layer without padding adds none, whatever the layer before it had
	public void testNestedLayersSumTheirPadding() {
		LayerDrawable layers = (LayerDrawable)resources().getDrawable(R.drawable.layers_nest);
		assertEquals(LayerDrawable.PADDING_MODE_NEST, layers.getPaddingMode());
		assertEquals(new Rect(1, 3, 5, 1), padding(layers, true));
		layers.setBounds(0, 0, 20, 20);
		assertEquals(new Rect(1, 3, 15, 19), layers.findDrawableByLayerId(R.id.inner).getBounds());
	}

	public void testStackedLayersTakeTheLargest() {
		LayerDrawable layers = (LayerDrawable)resources().getDrawable(R.drawable.layers_stack);
		assertEquals(LayerDrawable.PADDING_MODE_STACK, layers.getPaddingMode());
		assertEquals(new Rect(2, 3, 5, 2), padding(layers, true));
		layers.setBounds(0, 0, 20, 20);
		assertEquals(new Rect(0, 0, 20, 20), layers.findDrawableByLayerId(R.id.inner).getBounds());
	}

	// mutate copies the state: the mode stays
	public void testMutatedLayersKeepTheirMode() {
		LayerDrawable layers = (LayerDrawable)resources().getDrawable(R.drawable.layers_stack).mutate();
		assertEquals(LayerDrawable.PADDING_MODE_STACK, layers.getPaddingMode());
		assertEquals(new Rect(2, 3, 5, 2), padding(layers, true));
	}

	public void testRippleLayersStack() {
		assertEquals(new Rect(2, 3, 5, 2), padding(resources().getDrawable(R.drawable.ripple_padded), true));
	}

	public void testLayerListPaddingWins() {
		assertEquals(new Rect(7, 3, 5, 1), padding(resources().getDrawable(R.drawable.layers_left), true));
	}

	public void testUnpaddedLayersReportNone() {
		Drawable layers = resources().getDrawable(R.drawable.layers_none);
		assertEquals(new Rect(), padding(layers, false));
		assertEquals(-1, layers.getIntrinsicWidth());
		assertEquals(0, layers.getMinimumWidth());
	}

	public void testSelectorTakesTheLargest() {
		Drawable selector = resources().getDrawable(R.drawable.selector_padded);
		assertEquals(new Rect(2, 2, 2, 3), padding(selector, true));
		selector.setState(new int[] {android.R.attr.state_pressed});
		assertEquals(new Rect(2, 2, 2, 3), padding(selector, true));
	}

	// a second load and a copy keep the flag
	public void testVariableSelectorFollowsItsChild() {
		for (int i = 0; i < 2; i++) {
			Drawable selector = resources().getDrawable(R.drawable.selector_variable);
			if (i == 1)
				selector = selector.getConstantState().newDrawable();
			selector.setState(StateSet.WILD_CARD);
			assertEquals(new Rect(2, 2, 2, 2), padding(selector, true));
			selector.setState(new int[] {android.R.attr.state_pressed});
			assertEquals(new Rect(1, 2, 1, 3), padding(selector, true));
		}
	}

	public void testMinimumSizeIsTheIntrinsicOne() {
		Drawable photo = resources().getDrawable(R.drawable.photo);
		assertEquals(photo.getIntrinsicWidth(), photo.getMinimumWidth());
		assertEquals(photo.getIntrinsicHeight(), photo.getMinimumHeight());
		assertEquals(0, new ColorDrawable(0xff000000).getMinimumWidth());
	}
}
