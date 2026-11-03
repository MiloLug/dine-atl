package android.graphics;

public class ColorMatrixColorFilter extends ColorFilter {
	private final ColorMatrix mMatrix = new ColorMatrix();

	public ColorMatrixColorFilter(ColorMatrix matrix) {
		mMatrix.set(matrix);
	}

	public ColorMatrixColorFilter(float[] array) {
		if (array.length < 20) {
			throw new ArrayIndexOutOfBoundsException();
		}
		mMatrix.set(array);
	}

	public void getColorMatrix(ColorMatrix colorMatrix) {
		colorMatrix.set(mMatrix);
	}

	/** @hide */
	public void setColorMatrix(ColorMatrix matrix) {
		if (matrix == null)
			mMatrix.reset();
		else
			mMatrix.set(matrix);
	}

	/** @hide */
	@Override
	public float[] atlMatrix() {
		return mMatrix.getArray().clone();
	}
}
