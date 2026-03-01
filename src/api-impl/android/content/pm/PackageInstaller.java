package android.content.pm;

import java.util.Collections;
import java.util.List;

public class PackageInstaller {

	public List getMySessions() {
		return Collections.emptyList();
	}

	public void registerSessionCallback(SessionCallback callback) {
	}

	public abstract static class SessionCallback {
	}
}
