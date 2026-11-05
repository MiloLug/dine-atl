package android.atl;

import android.graphics.Bitmap;

/**
 * @hide Natives for the test suites, in builds with the test_hooks option; elsewhere they throw
 * UnsatisfiedLinkError.
 */
public final class ATLTestHooks {
	private ATLTestHooks() {}

	/* A copy of what AndroidBitmap_lockPixels gives an app, the pixels in the config's NDK format
	 * (ARGB_8888: R, G, B, A bytes, premultiplied), rows getRowBytes() apart; null: it failed. Builds
	 * without libandroid (all but gtk) use the conversions it uses. */
	public static native byte[] lockPixels(Bitmap bitmap);
	/* pixels written into the locked buffer, then AndroidBitmap_unlockPixels. */
	public static native void unlockPixels(Bitmap bitmap, byte[] pixels);

	/* A canvas that records as a view's does, for drawViewCanvas. */
	public static ATLCanvas newViewCanvas(int width, int height) {
		return new ATLCanvas(nativeNewViewCanvas(), width, height);
	}
	/* The recording drawn into target as the window would show it (gsk: on the main thread, with
	 * the display's renderer); false: the backend draws views nowhere it can read. The canvas is
	 * used up. */
	public static boolean drawViewCanvas(ATLCanvas canvas, Bitmap target) {
		long handle = canvas.handle;
		canvas.handle = 0;
		return nativeDrawViewCanvas(handle, target);
	}
	private static native long nativeNewViewCanvas();
	private static native boolean nativeDrawViewCanvas(long canvas, Bitmap target);
}
