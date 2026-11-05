package android.app;

import android.content.Context;
import android.graphics.Bitmap;

public class WallpaperManager {

	public static WallpaperManager getInstance(Context context) {
		return new WallpaperManager();
	}

	public void setBitmap(Bitmap bitmap) {
		if (bitmap.isRecycled()) // Android 10's compress
			throw new IllegalStateException("Can't compress a recycled bitmap");
		set_bitmap(bitmap.getNativeInstance());
	}

	private static native void set_bitmap(long bitmap);
}
