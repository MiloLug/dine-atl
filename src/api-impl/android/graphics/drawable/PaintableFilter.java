package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

/* A bitmap or nine-patch drawable's color filter and tint, on its native drawable as AOSP's draws
 * put them on the paint: an explicit color filter wins over the tint. The gfx slot takes a
 * PorterDuff filter only; any other ColorFilter draws unfiltered. */
final class PaintableFilter {
	ColorFilter colorFilter;
	ColorStateList tint;
	PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;

	boolean isStateful() {
		return tint != null && tint.isStateful();
	}

	void apply(Drawable drawable) {
		if (drawable.paintable == 0)
			return;
		if (colorFilter != null) {
			Drawable.native_setColorFilter(drawable.paintable, colorFilter.atlMode(), colorFilter.atlColor(), colorFilter.atlMatrix());
		} else if (tint != null && tintMode != null) {
			Drawable.native_setColorFilter(drawable.paintable, tintMode.nativeInt, tint.getColorForState(drawable.getState(), tint.getDefaultColor()), null);
		} else {
			Drawable.native_setColorFilter(drawable.paintable, -1, 0, null);
		}
	}
}
