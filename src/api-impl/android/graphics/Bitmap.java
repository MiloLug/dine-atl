/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * graphics/java/android/graphics/Bitmap.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.graphics;

import android.os.Parcelable;
import android.util.DisplayMetrics;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;

/*
 * native_bitmap is a gfx-slot bitmap, made with the Bitmap, whose pixels change in place: a
 * Canvas(Bitmap) draws into them at once.
 */
public final class Bitmap implements Parcelable {

	public enum Config {
		RGB_565(2, 3, /*ANDROID_BITMAP_FORMAT_RGB_565*/ 4),
		ARGB_8888(4, 5, /**ANDROID_BITMAP_FORMAT_RGBA_8888*/ 1),
		ARGB_4444(2, 4, /*ANDROID_BITMAP_FORMAT_RGBA_4444*/ 7),
		ALPHA_8(1, 1, /*ANDROID_BITMAP_FORMAT_A_8*/ 8),
		RGBA_F16(8, 6, /*ANDROID_BITMAP_FORMAT_RGBA_F16*/ 9),
		HARDWARE(4, 7, /*ANDROID_BITMAP_FORMAT_RGBA_8888*/ 1);

		private int bytes_per_pixel;
		final int nativeInt; // AOSP's, the gfx slot's enum gfx_bitmap_config; libandroid reads it
		int android_memory_format; // used by native function AndroidBitmap_getInfo()

		private Config(int bytes_per_pixel, int nativeInt, int android_memory_format) {
			this.bytes_per_pixel = bytes_per_pixel;
			this.nativeInt = nativeInt;
			this.android_memory_format = android_memory_format;
		}
	}

	public enum CompressFormat {
		JPEG,
		PNG,
		WEBP,
		WEBP_LOSSY,
		WEBP_LOSSLESS,
	}

	private int width;
	private int height;
	private int stride;
	private long native_bitmap;
	private Config config = Config.ARGB_8888;
	private boolean hasAlpha = true;
	long bytes = 0; // the locked pixels of AndroidBitmap_lockPixels()
	private boolean recycled = false;
	boolean mutable = true;
	private byte[] ninePatchChunk; // BitmapFactory's, from a PNG's npTc

	Bitmap(long native_bitmap) {
		this(native_bitmap, native_get_width(native_bitmap), native_get_height(native_bitmap), Config.ARGB_8888);
	}

	private Bitmap(long native_bitmap, int width, int height, Config config) {
		this.native_bitmap = native_bitmap;
		this.config = config;
		this.width = width;
		this.height = height;
		this.stride = width * config.bytes_per_pixel; // Skia's minRowBytes
		this.hasAlpha = config != Config.RGB_565;
	}

	/* Copyright (C) 2006 The Android Open Source Project: the checks; Android 10 makes ARGB_4444 as
	 * ARGB_8888 (Bitmap_creator) */
	private static Bitmap make(int width, int height, Config config) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("width and height must be > 0");
		if (config == Config.ARGB_4444)
			config = Config.ARGB_8888;
		return new Bitmap(native_new(width, height, config.nativeInt), width, height, config);
	}

	public static Bitmap createBitmap(int width, int height, Config config) {
		return make(width, height, config);
	}

	public static Bitmap createBitmap(DisplayMetrics metrics, int width, int height, Config config) {
		return make(width, height, config);
	}

	public static Bitmap createBitmap(DisplayMetrics metrics, int width, int height, Config config, boolean hasAlpha, ColorSpace colorSpace) {
		Bitmap bitmap = make(width, height, config);
		bitmap.hasAlpha = hasAlpha;
		return bitmap;
	}

	public static Bitmap createBitmap(Bitmap src, int x, int y, int width, int height) {
		Bitmap dest = make(width, height, src.getConfig());
		new Canvas(dest).drawBitmap(src, new Rect(x, y, x + width, y + height), new Rect(0, 0, width, height), null);
		return dest;
	}

	public static Bitmap createBitmap(Bitmap src, int x, int y, int width, int height, Matrix matrix, boolean filter) {
		Bitmap dest = make(width, height, src.getConfig());
		Canvas canvas = new Canvas(dest);
		canvas.concat(matrix);
		canvas.drawBitmap(src, new Rect(x, y, x + width, y + height), new Rect(0, 0, width, height), null);
		return dest;
	}

	public static Bitmap createBitmap(int[] colors, int width, int height, Config config) {
		return createBitmap(colors, 0, width, width, height, config);
	}

	public static Bitmap createBitmap(int[] colors, int offset, int stride, int width, int height, Config config) {
		Bitmap bitmap = createBitmap(width, height, config);
		bitmap.setPixels(colors, offset, stride, 0, 0, width, height);
		bitmap.mutable = false;
		return bitmap;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public static Bitmap createBitmap(Bitmap src) {
		return src.isMutable() ? src.copy(src.getConfig(), true) : src;
	}

	public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
		Bitmap dest = make(dstWidth, dstHeight, src.getConfig());
		new Canvas(dest).drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new Rect(0, 0, dstWidth, dstHeight), null);
		return dest;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public Config getConfig() {
		return config;
	}

	/** @hide the gfx-slot bitmap */
	public long getNativeInstance() {
		return native_bitmap;
	}

	public void eraseColor(int color) {
		checkRecycled("Can't erase a recycled bitmap");
		if (!isMutable())
			throw new IllegalStateException("cannot erase immutable bitmaps");
		native_erase(native_bitmap, color);
	}

	/* Copyright (C) 2006 The Android Open Source Project: checkRecycled, checkPixelAccess,
	 * checkPixelsAccess */
	private void checkRecycled(String errorMessage) {
		if (recycled)
			throw new IllegalStateException(errorMessage);
	}

	private void checkPixelAccess(int x, int y) {
		if (x < 0)
			throw new IllegalArgumentException("x must be >= 0");
		if (y < 0)
			throw new IllegalArgumentException("y must be >= 0");
		if (x >= getWidth())
			throw new IllegalArgumentException("x must be < bitmap.width()");
		if (y >= getHeight())
			throw new IllegalArgumentException("y must be < bitmap.height()");
	}

	private void checkPixelsAccess(int x, int y, int width, int height, int offset, int stride, int pixels[]) {
		if (x < 0)
			throw new IllegalArgumentException("x must be >= 0");
		if (y < 0)
			throw new IllegalArgumentException("y must be >= 0");
		if (width < 0)
			throw new IllegalArgumentException("width must be >= 0");
		if (height < 0)
			throw new IllegalArgumentException("height must be >= 0");
		if (x + width > getWidth())
			throw new IllegalArgumentException("x + width must be <= bitmap.width()");
		if (y + height > getHeight())
			throw new IllegalArgumentException("y + height must be <= bitmap.height()");
		if (Math.abs(stride) < width)
			throw new IllegalArgumentException("abs(stride) must be >= width");
		int lastScanline = offset + (height - 1) * stride;
		int length = pixels.length;
		if (offset < 0 || (offset + width > length) || lastScanline < 0 || (lastScanline + width > length))
			throw new ArrayIndexOutOfBoundsException();
	}

	public void recycle() {
		if (native_bitmap != 0)
			native_recycle(native_bitmap);
		native_bitmap = 0;
		recycled = true;
	}

	public byte[] getNinePatchChunk() {
		return ninePatchChunk;
	}

	void setNinePatchChunk(byte[] chunk) {
		ninePatchChunk = chunk;
	}

	public int getRowBytes() {
		return stride;
	}

	// used by BitmapFactory to satify Options.inBitmap if set
	void transferFrom(Bitmap src) {
		if (native_bitmap != 0)
			native_recycle(native_bitmap);
		this.native_bitmap = src.native_bitmap;
		this.width = src.width;
		this.height = src.height;
		this.stride = src.stride;
		this.config = src.config;
		this.hasAlpha = src.hasAlpha;
		this.mutable = src.mutable;
		this.ninePatchChunk = src.ninePatchChunk;
		this.recycled = false;
		src.native_bitmap = 0;
		src.recycled = true;
	}

	public int getAllocationByteCount() {
		return height * getRowBytes();
	}

	public void prepareToDraw() {}

	public void setDensity(int density) {}

	public int getScaledWidth(int density) {
		return width;
	}

	public int getScaledHeight(int density) {
		return height;
	}

	public boolean isRecycled() {
		return recycled;
	}

	public void setHasAlpha(boolean hasAlpha) {
		this.hasAlpha = hasAlpha;
	}

	public boolean hasAlpha() {
		return hasAlpha;
	}

	public Bitmap copy(Bitmap.Config config, boolean isMutable) {
		checkRecycled("Can't copy a recycled bitmap");
		Bitmap bitmap = new Bitmap(native_copy(native_bitmap, config.nativeInt), width, height, config);
		bitmap.mutable = isMutable;
		bitmap.hasAlpha = hasAlpha;
		return bitmap;
	}

	public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
		checkRecycled("Can't call getPixels() on a recycled bitmap");
		if (width == 0 || height == 0)
			return;
		checkPixelsAccess(x, y, width, height, offset, stride, pixels);
		native_get_pixels(native_bitmap, pixels, offset, stride, x, y, width, height);
	}

	public int getPixel(int x, int y) {
		checkRecycled("Can't call getPixel() on a recycled bitmap");
		checkPixelAccess(x, y);
		return native_get_pixel(native_bitmap, x, y);
	}

	public void copyPixelsToBuffer(Buffer buffer) {
		checkRecycled("copyPixelsToBuffer called on recycled bitmap");
		int elements = buffer.remaining();
		int shift = buffer instanceof java.nio.ByteBuffer ? 0 : buffer instanceof java.nio.ShortBuffer ? 1 : 2;
		long bufferSize = (long)elements << shift;
		long pixelSize = getByteCount();
		if (bufferSize < pixelSize)
			throw new RuntimeException("Buffer not large enough for pixels");
		native_copy_to_buffer(native_bitmap, buffer, getRowBytes());
		buffer.position((int)(buffer.position() + (pixelSize >> shift)));
	}

	public int getByteCount() {
		return getAllocationByteCount();
	}

	public boolean isMutable() {
		return mutable;
	}

	public boolean compress(Bitmap.CompressFormat format, int quality, OutputStream stream) throws IOException {
		checkRecycled("Can't compress a recycled bitmap");
		if (format == CompressFormat.PNG) {
			stream.write(native_save_to_png(native_bitmap));
			return true;
		} else {
			stream.write(("fixme Bitmap.compress " + format.name()).getBytes());
			return false;
		}
	}

	public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
		checkRecycled("Can't call setPixels() on a recycled bitmap");
		if (!isMutable())
			throw new IllegalStateException();
		if (width == 0 || height == 0)
			return;
		checkPixelsAccess(x, y, width, height, offset, stride, pixels);
		native_set_pixels(native_bitmap, pixels, offset, stride, x, y, width, height);
	}

	public void setPixel(int x, int y, int color) {
		checkRecycled("Can't call setPixel() on a recycled bitmap");
		if (!isMutable())
			throw new IllegalStateException();
		checkPixelAccess(x, y);
		native_set_pixel(native_bitmap, x, y, color);
	}

	public void reconfigure(int width, int height, Bitmap.Config config) {}

	public void setPremultiplied(boolean premultiplied) {}

	public Bitmap extractAlpha() {
		return copy(Config.ALPHA_8, true);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable {
		try {
			recycle();
		} finally {
			super.finalize();
		}
	}

	private static native long native_new(int width, int height, int config);
	private static native long native_copy(long bitmap, int config);
	static native long native_canvas_new(long bitmap);
	static native void native_canvas_free(long canvas);
	private static native int native_get_width(long bitmap);
	private static native int native_get_height(long bitmap);
	private static native void native_erase(long bitmap, int color);
	private static native void native_recycle(long bitmap);
	private static native void native_get_pixels(long bitmap, int[] pixels, int offset, int stride, int x, int y, int width, int height);
	private static native int native_get_pixel(long bitmap, int x, int y);
	private static native void native_copy_to_buffer(long bitmap, Buffer buffer, int row_bytes);
	private static native byte[] native_save_to_png(long bitmap);
	private static native void native_set_pixels(long bitmap, int[] pixels, int offset, int stride, int x, int y, int width, int height);
	private static native void native_set_pixel(long bitmap, int x, int y, int color);
}
