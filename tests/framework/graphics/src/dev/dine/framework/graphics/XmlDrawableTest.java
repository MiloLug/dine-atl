package dev.dine.framework.graphics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedRotateDrawable;
import android.graphics.drawable.AnimatedStateListDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RotateDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;

public class XmlDrawableTest extends android.test.InstrumentationTestCase {
	private Resources resources() {
		return getInstrumentation().getTargetContext().getResources();
	}

	public void testLevelList() {
		Drawable drawable = resources().getDrawable(R.drawable.levels);
		assertTrue(drawable instanceof LevelListDrawable);
		assertEquals(resources().getColor(R.color.idle), ((ColorDrawable)drawable.getCurrent()).getColor());
	}

	public void testAnimationList() {
		Drawable drawable = resources().getDrawable(R.drawable.frames);
		assertTrue(drawable instanceof AnimationDrawable);
		AnimationDrawable frames = (AnimationDrawable)drawable;
		assertEquals(2, frames.getNumberOfFrames());
		assertEquals(100, frames.getDuration(0));
		assertEquals(200, frames.getDuration(1));
		assertTrue(frames.isOneShot());
		assertEquals(resources().getColor(R.color.pressed), ((ColorDrawable)frames.getFrame(1)).getColor());
	}

	private int idle() {
		return resources().getColor(R.color.idle);
	}

	private static Bitmap draw(Drawable drawable, int size) {
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, size, size);
		drawable.draw(new Canvas(bitmap));
		return bitmap;
	}

	public void testColor() {
		Drawable drawable = resources().getDrawable(R.drawable.solid);
		assertTrue(drawable instanceof ColorDrawable);
		assertEquals(0xff00ff00, ((ColorDrawable)drawable).getColor());
	}

	public void testInsetPadsAndDrawsInside() {
		Drawable drawable = resources().getDrawable(R.drawable.inset);
		assertTrue(drawable instanceof InsetDrawable);
		Bitmap bitmap = draw(drawable, 10);
		Rect padding = new Rect();
		assertTrue(drawable.getPadding(padding));
		assertEquals(new Rect(2, 2, 2, 2), padding);
		assertEquals(0, bitmap.getPixel(1, 1));
		assertEquals(idle(), bitmap.getPixel(5, 5));
	}

	public void testClipFollowsTheLevel() {
		Drawable drawable = resources().getDrawable(R.drawable.clipped);
		assertTrue(drawable instanceof ClipDrawable);
		assertEquals(0, draw(drawable, 10).getPixel(2, 5));
		drawable.setLevel(5000);
		Bitmap half = draw(drawable, 10);
		assertEquals(idle(), half.getPixel(2, 5));
		assertEquals(0, half.getPixel(7, 5));
	}

	public void testScaleFollowsTheLevel() {
		Drawable drawable = resources().getDrawable(R.drawable.scaled);
		assertTrue(drawable instanceof ScaleDrawable);
		drawable.setLevel(5000);
		Bitmap scaled = draw(drawable, 20);
		assertEquals(idle(), scaled.getPixel(2, 10));
		assertEquals(0, scaled.getPixel(17, 10)); // 75% of the width
	}

	public void testRotateTurnsItsDrawable() {
		Drawable drawable = resources().getDrawable(R.drawable.rotated);
		assertTrue(drawable instanceof RotateDrawable);
		assertEquals(45f, ((RotateDrawable)drawable).getFromDegrees());
		drawable.setLevel(5000);
		Bitmap turned = draw(drawable, 20);
		assertEquals(idle(), turned.getPixel(10, 10));
		assertEquals(0, turned.getPixel(1, 1) >>> 24);
	}

	public void testAnimatedKinds() {
		Drawable selector = resources().getDrawable(R.drawable.animated_selector);
		assertTrue(selector instanceof AnimatedStateListDrawable);
		assertEquals(idle(), ((ColorDrawable)selector.getCurrent()).getColor());
		assertTrue(resources().getDrawable(R.drawable.spinner) instanceof AnimatedRotateDrawable);
		Drawable fade = resources().getDrawable(R.drawable.fade);
		assertTrue(fade instanceof TransitionDrawable);
		assertEquals(2, ((TransitionDrawable)fade).getNumberOfLayers());
	}
}
