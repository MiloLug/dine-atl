package android.graphics;

public class RadialGradient extends Shader {
	private final float x, y, radius;
	private final int[] colors;
	private final float[] positions;
	private final TileMode tile;

	public RadialGradient(float x, float y, float radius, int[] colors, float[] positions, TileMode tile) {
		if (radius <= 0)
			throw new IllegalArgumentException("radius must be > 0");
		checkColors(colors, positions);
		this.x = x;
		this.y = y;
		this.radius = radius;
		this.colors = colors.clone();
		this.positions = positions != null ? positions.clone() : null;
		this.tile = tile;
	}

	public RadialGradient(float x, float y, float radius, long[] colors, float[] positions, TileMode tile) {
		this(x, y, radius, argb(colors), positions, tile);
	}

	public RadialGradient(float x, float y, float radius, int centerColor, int edgeColor, TileMode tile) {
		this(x, y, radius, new int[] {centerColor, edgeColor}, null, tile);
	}

	public RadialGradient(float x, float y, float radius, long centerColor, long edgeColor, TileMode tile) {
		this(x, y, radius, argb(new long[] {centerColor, edgeColor}), null, tile);
	}

	@Override
	long createNativeInstance(long nativeMatrix) {
		return nativeCreate(nativeMatrix, x, y, radius, colors, positions, tile.nativeInt);
	}

	private static native long nativeCreate(long matrix, float x, float y, float radius, int[] colors, float[] positions, int tile);
}
