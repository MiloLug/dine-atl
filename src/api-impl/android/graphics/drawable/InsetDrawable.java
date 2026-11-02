package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Rect;

// the insets are neither kept nor applied
public class InsetDrawable extends DrawableWrapper {

	public InsetDrawable(Drawable drawable, int insetLeft, int insetTop, int insetRight, int insetBottom) {
		this(new InsetState(null, null), null);
		setDrawable(drawable);
	}

	public InsetDrawable(Drawable drawable, int inset) {
		this(drawable, inset, inset, inset, inset);
	}

	InsetDrawable() {
		this(new InsetState(null, null), null);
	}

	private InsetDrawable(InsetState state, Resources res) {
		super(state, res);
	}

	public boolean getPadding(Rect padding) { return false; }

	@Override
	DrawableWrapperState mutateConstantState() {
		return new InsetState((InsetState)super.mutateConstantState(), null);
	}

	static final class InsetState extends DrawableWrapper.DrawableWrapperState {

		InsetState(InsetState orig, Resources res) {
			super(orig, res);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return new InsetDrawable(this, res);
		}
	}
}
