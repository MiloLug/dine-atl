package android.graphics;

public class LinearGradient extends Shader {
	private final float x0, y0, x1, y1;
	private final int[] colors;
	private final float[] positions;
	private final TileMode tile;

	public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode tile) {
		checkColors(colors, positions);
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.colors = colors.clone();
		this.positions = positions != null ? positions.clone() : null;
		this.tile = tile;
	}

	public LinearGradient(float x0, float y0, float x1, float y1, long[] colors, float[] positions, TileMode tile) {
		this(x0, y0, x1, y1, argb(colors), positions, tile);
	}

	public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
		this(x0, y0, x1, y1, new int[] {color0, color1}, null, tile);
	}

	public LinearGradient(float x0, float y0, float x1, float y1, long color0, long color1, TileMode tile) {
		this(x0, y0, x1, y1, argb(new long[] {color0, color1}), null, tile);
	}

	@Override
	long createNativeInstance(long nativeMatrix) {
		return nativeCreate(nativeMatrix, x0, y0, x1, y1, colors, positions, tile.nativeInt);
	}

	private static native long nativeCreate(long matrix, float x0, float y0, float x1, float y1, int[] colors, float[] positions, int tile);
}
