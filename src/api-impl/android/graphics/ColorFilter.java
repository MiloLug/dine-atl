package android.graphics;

/* What the gfx slot draws (struct gfx_color_filter): a PorterDuff mode and color, or a 4x5 color
 * matrix. None of either: no filter. */
public class ColorFilter {
	/** @hide PorterDuff.Mode's nativeInt, -1: none */
	public int atlMode() {
		return -1;
	}

	/** @hide */
	public int atlColor() {
		return 0;
	}

	/** @hide ColorMatrix's array, null: none */
	public float[] atlMatrix() {
		return null;
	}
}
