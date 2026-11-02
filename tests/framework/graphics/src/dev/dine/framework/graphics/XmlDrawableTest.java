package dev.dine.framework.graphics;

import android.content.res.Resources;
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
}
