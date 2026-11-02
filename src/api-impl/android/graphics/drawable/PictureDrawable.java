package android.graphics.drawable;

import android.graphics.Picture;

public class PictureDrawable extends Drawable {
	private final Picture picture;

	public PictureDrawable(Picture picture) {
		this.picture = picture;
	}

	@Override
	public ConstantState getConstantState() {
		return new StubState(() -> new PictureDrawable(picture));
	}
}
