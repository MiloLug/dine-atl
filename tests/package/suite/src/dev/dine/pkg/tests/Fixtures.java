package dev.dine.pkg.tests;

import java.io.File;

/** The fixture APKs the harness built; DINE_TEST_FIXTURES names their dir. */
final class Fixtures {
	static File apk(String name) {
		String dir = System.getenv("DINE_TEST_FIXTURES");
		if (dir == null)
			throw new IllegalStateException("DINE_TEST_FIXTURES is not set");
		return new File(dir, name + ".apk");
	}
}
