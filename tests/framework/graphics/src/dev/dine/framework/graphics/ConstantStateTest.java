package dev.dine.framework.graphics;

import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ColorStateListDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.PictureDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.Gravity;
import junit.framework.TestCase;

// Resources caches a drawable by its constant state, and apps copy one with newDrawable()
public class ConstantStateTest extends TestCase {
	private static Drawable copy(Drawable drawable) {
		Drawable.ConstantState state = drawable.getConstantState();
		assertNotNull(drawable.getClass().getSimpleName() + " has no constant state", state);
		Drawable copy = state.newDrawable();
		assertEquals(drawable.getClass(), copy.getClass());
		return copy;
	}

	public void testWrappersCopyTheirDrawable() {
		InsetDrawable inset = (InsetDrawable)copy(new InsetDrawable(new ColorDrawable(Color.RED), 2));
		assertEquals(Color.RED, ((ColorDrawable)inset.getDrawable()).getColor());

		ClipDrawable clip = new ClipDrawable(new ColorDrawable(Color.RED), Gravity.LEFT, ClipDrawable.HORIZONTAL);
		copy(clip);
		Drawable.ConstantState shared = clip.getConstantState();
		assertNotSame("mutate() gives the drawable a state of its own", shared, clip.mutate().getConstantState());
	}

	public void testOtherDrawablesHaveStates() {
		copy(new AnimatedVectorDrawable());
		copy(new ColorStateListDrawable());
		copy(new PictureDrawable(new Picture()));
		copy(new ScaleDrawable());
	}
}
