package android.graphics;

/*
 * A Shader's native is an immutable struct gfx_shader (src/shared/shader.h), made on first use
 * from the subclass's fields; a change of the local matrix makes a new one.
 */
public class Shader {

	public enum TileMode {
		CLAMP(0),
		REPEAT(1),
		MIRROR(2);

		TileMode(int nativeInt) {
			this.nativeInt = nativeInt;
		}

		final int nativeInt;
	}

	private Matrix localMatrix;
	private long nativeInstance;

	public boolean getLocalMatrix(Matrix matrix) {
		if (localMatrix == null)
			return false;
		matrix.set(localMatrix);
		return true;
	}

	public void setLocalMatrix(Matrix matrix) {
		if (matrix == null || matrix.isIdentity()) {
			if (localMatrix == null)
				return;
			localMatrix = null;
		} else if (localMatrix == null) {
			localMatrix = new Matrix(matrix);
		} else if (localMatrix.equals(matrix)) {
			return;
		} else {
			localMatrix.set(matrix);
		}
		discardNativeInstance();
	}

	// 0: none, the paint's color draws
	long createNativeInstance(long nativeMatrix) {
		return 0;
	}

	protected final void discardNativeInstance() {
		if (nativeInstance != 0) {
			nativeUnref(nativeInstance);
			nativeInstance = 0;
		}
	}

	/** @hide */
	public final long getNativeInstance() {
		if (nativeInstance == 0)
			nativeInstance = createNativeInstance(localMatrix != null ? localMatrix.native_instance : 0);
		return nativeInstance;
	}

	protected void copyLocalMatrix(Shader dest) {
		dest.setLocalMatrix(localMatrix);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable {
		try {
			discardNativeInstance();
		} finally {
			super.finalize();
		}
	}

	// Android's checks of a gradient's arrays
	static void checkColors(int[] colors, float[] positions) {
		if (colors.length < 2)
			throw new IllegalArgumentException("needs >= 2 number of colors");
		if (positions != null && colors.length != positions.length)
			throw new IllegalArgumentException("color and position arrays must be of equal length");
	}

	// a ColorLong in sRGB keeps its ARGB in the top 32 bits
	static int[] argb(long[] colors) {
		int[] argb = new int[colors.length];
		for (int i = 0; i < colors.length; i++)
			argb[i] = (int)(colors[i] >>> 32);
		return argb;
	}

	private static native void nativeUnref(long shader);
}
