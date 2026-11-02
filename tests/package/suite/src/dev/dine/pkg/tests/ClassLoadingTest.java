package dev.dine.pkg.tests;

import dalvik.system.DelegateLastClassLoader;
import dalvik.system.PathClassLoader;

/** An installed extension's code loaded from its sourceDir, as Mihon loads its extensions. */
public class ClassLoadingTest extends android.test.InstrumentationTestCase {
	private static final String EXT_CLASS = "dev.dine.pkg.ext.Ext";

	private String sourceDir() throws Exception {
		return getInstrumentation().getTargetContext().getPackageManager().getApplicationInfo("dev.dine.pkg.ext", 0).sourceDir;
	}

	private static void assertLoads(ClassLoader loader) throws Exception {
		Class<?> ext = Class.forName(EXT_CLASS, false, loader);
		assertSame(loader, ext.getClassLoader());
		assertEquals("ext says hello", ext.getMethod("hello").invoke(null));
	}

	public void testDelegateLastClassLoader() throws Exception {
		assertLoads(new DelegateLastClassLoader(sourceDir(), null, getClass().getClassLoader()));
	}

	// the order of Mihon's compat loader below API 27: boot, its own dex, then the parent
	public void testChildFirstPathClassLoader() throws Exception {
		assertLoads(new PathClassLoader(sourceDir(), null, getClass().getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				Class<?> c = findLoadedClass(name);
				if (c == null) {
					try {
						c = getSystemClassLoader().loadClass(name);
					} catch (ClassNotFoundException e) {
						try {
							c = findClass(name);
						} catch (ClassNotFoundException e2) {
							c = super.loadClass(name, resolve);
						}
					}
				}
				return c;
			}
		});
	}
}
