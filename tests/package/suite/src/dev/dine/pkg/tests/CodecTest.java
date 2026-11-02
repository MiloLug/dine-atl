package dev.dine.pkg.tests;

import android.atl.ATLLoadedApp;
import android.content.pm.PackageCodec;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.DisplayMetrics;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** A Package decoded from the registry's bytes is the parsed one, shared references included. */
public class CodecTest extends android.test.InstrumentationTestCase {
	private static final int ALL_FLAGS = PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS |
	                                     PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS |
	                                     PackageManager.GET_INSTRUMENTATION | PackageManager.GET_META_DATA |
	                                     PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS |
	                                     PackageManager.GET_PERMISSIONS | PackageManager.GET_URI_PERMISSION_PATTERNS |
	                                     PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_GIDS;

	private static PackageParser.Package parse(String fixture) {
		File apk = Fixtures.apk(fixture);
		PackageParser parser = new PackageParser(apk.getPath());
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setToDefaults();
		PackageParser.Package pkg = parser.parsePackage(apk, apk.getPath(), metrics, 0);
		assertNotNull(fixture, pkg);
		parser.collectCertificates(pkg, 0);
		return pkg;
	}

	private static void roundTrip(PackageParser.Package fresh) {
		PackageParser.Package decoded = PackageCodec.unmarshall(PackageCodec.marshall(fresh));
		new Graph().compare("pkg", fresh, decoded);
		for (int flags : new int[] {0, ALL_FLAGS}) {
			PackageInfo a = PackageParser.generatePackageInfo(fresh, null, flags, 0, 0, null, new PackageUserState(), 0);
			PackageInfo b = PackageParser.generatePackageInfo(decoded, null, flags, 0, 0, null, new PackageUserState(), 0);
			new Graph().compare("info(" + flags + ")", a, b);
		}
	}

	public void testExt() {
		roundTrip(parse("ext"));
	}

	public void testApp() {
		roundTrip(parse("app"));
	}

	public void testUnsigned() {
		roundTrip(parse("plain"));
	}

	public void testSpoof() {
		roundTrip(parse("spoof"));
	}

	// framework-res: permissions and permission groups
	public void testSystem() {
		roundTrip(ATLLoadedApp.getSystemApplication().pkg);
	}

	// read at run time: javac would fold the constant from the jar this APK compiled against
	public void testFieldsHash() throws Exception {
		Object pinned = PackageCodec.class.getField("FIELDS_HASH").get(null);
		assertEquals("a field of PackageCodec.CLASSES changed: see PackageCodec.FIELDS_HASH",
		             pinned, PackageCodec.fieldsHash());
	}

	/** Field by field; an object reached twice on one side must pair with one object on the other. */
	private static final class Graph {
		private final IdentityHashMap<Object, Object> pairs = new IdentityHashMap<>();

		void compare(String path, Object a, Object b) {
			if (a == null || b == null) {
				assertTrue(path + ": " + a + " vs " + b, a == b);
				return;
			}
			if (isValue(a)) {
				assertEquals(path, a.toString(), b.toString());
				if (!(a instanceof CharSequence))   // a label may decode as a String of a SpannedString
					assertEquals(path, a.getClass(), b.getClass());
				return;
			}
			if (pairs.containsKey(a)) {
				assertSame(path + ": shared on one side only", pairs.get(a), b);
				return;
			}
			pairs.put(a, b);
			assertEquals(path, a.getClass(), b.getClass());
			if (a.getClass().isArray()) {
				assertEquals(path + ".length", Array.getLength(a), Array.getLength(b));
				for (int i = 0; i < Array.getLength(a); i++)
					compare(path + "[" + i + "]", Array.get(a, i), Array.get(b, i));
			} else if (a instanceof List) {
				List<?> la = (List<?>)a, lb = (List<?>)b;
				assertEquals(path + ".size", la.size(), lb.size());
				for (int i = 0; i < la.size(); i++)
					compare(path + "[" + i + "]", la.get(i), lb.get(i));
			} else if (a instanceof Set) {
				assertEquals(path, a, b);
			} else if (a instanceof Bundle) {
				Bundle ba = (Bundle)a, bb = (Bundle)b;
				assertEquals(path + ".keys", ba.keySet(), bb.keySet());
				for (String key : ba.keySet())
					compare(path + "[" + key + "]", ba.get(key), bb.get(key));
			} else if (a instanceof Signature) {
				assertEquals(path, a, b);
			} else {
				for (Class<?> c = a.getClass(); c != Object.class; c = c.getSuperclass())
					for (Field f : c.getDeclaredFields())
						compareField(path, f, a, b);
			}
		}

		private void compareField(String path, Field f, Object a, Object b) {
			String name = f.getDeclaringClass().getName().replace("android.content.pm.", "") + "." + f.getName();
			if (Modifier.isStatic(f.getModifiers()) || PackageCodec.SKIPPED.contains(name))
				return;
			f.setAccessible(true);
			try {
				compare(path + "." + f.getName(), f.get(a), f.get(b));
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		private static boolean isValue(Object o) {
			return o instanceof CharSequence || o instanceof Number || o instanceof Boolean ||
			    o instanceof Character || o instanceof Enum;
		}
	}
}
