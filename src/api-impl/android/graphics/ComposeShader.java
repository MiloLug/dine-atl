package android.graphics;

public class ComposeShader extends Shader {
	private final Shader shaderA;
	private final Shader shaderB;
	private final int mode;

	public ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode) {
		this(shaderA, shaderB, ((PorterDuffXfermode)mode).porterDuffMode);
	}

	public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
		this(shaderA, shaderB, mode.nativeInt);
	}

	private ComposeShader(Shader shaderA, Shader shaderB, int mode) {
		if (shaderA == null || shaderB == null)
			throw new IllegalArgumentException("Shader parameters must not be null");
		this.shaderA = shaderA;
		this.shaderB = shaderB;
		this.mode = mode;
	}

	// shaderA is the mode's destination, shaderB its source
	@Override
	long createNativeInstance(long nativeMatrix) {
		return nativeCreate(nativeMatrix, shaderA.getNativeInstance(), shaderB.getNativeInstance(), mode);
	}

	private static native long nativeCreate(long matrix, long dst, long src, int mode);
}
