package android.graphics;

public class BitmapShader extends Shader {
	private final Bitmap bitmap;
	private final int tileX;
	private final int tileY;

	public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
		if (bitmap == null)
			throw new IllegalArgumentException("Bitmap must be non-null");
		this.bitmap = bitmap;
		this.tileX = tileX.nativeInt;
		this.tileY = tileY.nativeInt;
	}

	// the bitmap's pixels as they are at each draw
	@Override
	long createNativeInstance(long nativeMatrix) {
		if (bitmap.isRecycled()) // Android 10 aborts the process here
			throw new RuntimeException("Canvas: trying to use a recycled bitmap " + bitmap);
		return nativeCreate(nativeMatrix, bitmap.getNativeInstance(), tileX, tileY);
	}

	private static native long nativeCreate(long matrix, long bitmap, int tileX, int tileY);
}
