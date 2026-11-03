package android.graphics;

// each channel times mul's, over 255, plus add's: a color matrix
public class LightingColorFilter extends ColorFilter {
	private int mMul;
	private int mAdd;

	public LightingColorFilter(int mul, int add) {
		mMul = mul;
		mAdd = add;
	}

	public int getColorMultiply() {
		return mMul;
	}

	public int getColorAdd() {
		return mAdd;
	}

	/** @hide */
	@Override
	public float[] atlMatrix() {
		return new float[] {
		    Color.red(mMul) / 255f, 0, 0, 0, Color.red(mAdd),
		    0, Color.green(mMul) / 255f, 0, 0, Color.green(mAdd),
		    0, 0, Color.blue(mMul) / 255f, 0, Color.blue(mAdd),
		    0, 0, 0, 1, 0,
		};
	}
}
