package android.graphics.drawable;

import android.content.Context;
import android.graphics.Bitmap;

public class Icon {
	private final Bitmap bitmap;
	private final String packageName;
	private final int resourceId;

	private Icon(Bitmap bitmap, String packageName, int resourceId) {
		this.bitmap = bitmap;
		this.packageName = packageName;
		this.resourceId = resourceId;
	}

	public static Icon createWithResource(String packageName, int resourceId) {
		return new Icon(null, packageName, resourceId);
	}

	public static Icon createWithBitmap(Bitmap bitmap) {
		return new Icon(bitmap, null, 0);
	}

	public Drawable loadDrawable(Context context) {
		if (bitmap != null) {
			return new BitmapDrawable(context.getResources(), bitmap);
		}
		if (packageName != null && resourceId != 0) {
			try {
				return context.getPackageManager().getResourcesForApplication(packageName).getDrawable(resourceId);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
}
