package android.content.pm;

/** One signer, no key rotation: v1 signatures carry no history. */
public final class SigningInfo {
	private final Signature[] signers;

	public SigningInfo() {
		signers = null;
	}

	/** @hide */
	public SigningInfo(Signature[] signers) {
		this.signers = signers;
	}

	public Signature[] getApkContentsSigners() {
		return signers;
	}

	public boolean hasMultipleSigners() {
		return signers != null && signers.length > 1;
	}

	public Signature[] getSigningCertificateHistory() {
		return signers;
	}

	public boolean hasPastSigningCertificates() {
		return false;
	}
}
