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

	// the pixels as they are when a paint first takes it; later ones too, as they change in place
	@Override
	long createNativeInstance(long nativeMatrix) {
		return nativeCreate(nativeMatrix, bitmap.getTexture(), tileX, tileY);
	}

	private static native long nativeCreate(long matrix, long bitmap, int tileX, int tileY);
}
