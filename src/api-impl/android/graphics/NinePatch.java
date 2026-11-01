package android.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* A Bitmap and a chunk in Bitmap.getNinePatchChunk's form (Res_png_9patch, native order). */
public class NinePatch {
	private final Bitmap bitmap;
	private final byte[] chunk;
	private final String name;

	public NinePatch(Bitmap bitmap, byte[] chunk) {
		this(bitmap, chunk, null);
	}

	public NinePatch(Bitmap bitmap, byte[] chunk, String srcName) {
		if (!isNinePatchChunk(chunk))
			throw new RuntimeException("not a nine-patch chunk");
		this.bitmap = bitmap;
		this.chunk = chunk;
		this.name = srcName;
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public String getName() {
		return name;
	}

	/** @hide */
	public byte[] getChunk() {
		return chunk;
	}

	public int getWidth() {
		return bitmap.getWidth();
	}

	public int getHeight() {
		return bitmap.getHeight();
	}

	public static boolean isNinePatchChunk(byte[] chunk) {
		return chunk != null && nativeIsNinePatchChunk(chunk);
	}

	/** @hide BitmapFactory's outPadding: the chunk's, or -1 on each side without one */
	public static void getPadding(byte[] chunk, Rect padding) {
		if (!isNinePatchChunk(chunk)) {
			padding.set(-1, -1, -1, -1);
			return;
		}
		ByteBuffer buffer = ByteBuffer.wrap(chunk).order(ByteOrder.nativeOrder());
		padding.set(buffer.getInt(12), buffer.getInt(20), buffer.getInt(16), buffer.getInt(24)); // stored left, right, top, bottom
	}

	private static native boolean nativeIsNinePatchChunk(byte[] chunk);
}
