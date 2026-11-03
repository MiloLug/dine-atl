package android.graphics;

/* A recording kept as the pixels it drew: drawing it again replays those, not the calls. */
public class Picture {
	private Bitmap bitmap;
	private int width;
	private int height;

	public Picture() {}

	public Picture(Picture src) {
		if (src.bitmap != null)
			bitmap = src.bitmap.copy(Bitmap.Config.ARGB_8888, false);
		width = src.width;
		height = src.height;
	}

	public Canvas beginRecording(int width, int height) {
		this.width = width;
		this.height = height;
		bitmap = Bitmap.createBitmap(Math.max(width, 1), Math.max(height, 1), Bitmap.Config.ARGB_8888);
		return new Canvas(bitmap);
	}

	public void endRecording() {}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean requiresHardwareAcceleration() {
		return false;
	}

	public void draw(Canvas canvas) {
		if (bitmap != null)
			canvas.drawBitmap(bitmap, 0, 0, null);
	}
}
