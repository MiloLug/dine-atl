package android.view;

import android.content.Context;
import android.util.AttributeSet;

public class TextureView extends View {

	private SurfaceTextureListener surfaceTextureListener;

	public TextureView(Context context) {
		super(context);
	}

	public TextureView(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public TextureView(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public TextureView(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public interface SurfaceTextureListener {}

	public void setSurfaceTextureListener(SurfaceTextureListener surfaceTextureListener) {
		this.surfaceTextureListener = surfaceTextureListener;
	}

	public void setOpaque(boolean opaque) {}

	public SurfaceTextureListener getSurfaceTextureListener() {
		return surfaceTextureListener;
	}

	public boolean isAvailable() {
		return false;
	}
}
