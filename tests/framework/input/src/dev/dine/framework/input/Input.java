package dev.dine.framework.input;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Method;
import java.util.List;

// Helpers shared by the input tests; views are only touched on the Android main thread.
final class Input {
	private Input() {}

	static void onMain(Instrumentation instrumentation, Runnable runnable) {
		instrumentation.runOnMainSync(runnable);
	}

	// lets posted and delayed work (PerformClick, UnsetPressedState) run
	static void settle(Instrumentation instrumentation, long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
		}
		instrumentation.runOnMainSync(new Runnable() {
			public void run() {}
		});
	}

	static MotionEvent touch(int action, float x, float y) {
		long now = SystemClock.uptimeMillis();
		return MotionEvent.obtain(now, now, action, x, y, 0);
	}

	static MotionEvent touches(int action, int[] ids, float[] xy) {
		MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
		MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
		for (int i = 0; i < ids.length; i++) {
			properties[i] = new MotionEvent.PointerProperties();
			properties[i].id = ids[i];
			coords[i] = new MotionEvent.PointerCoords();
			coords[i].x = xy[i * 2];
			coords[i].y = xy[i * 2 + 1];
		}
		long now = SystemClock.uptimeMillis();
		return MotionEvent.obtain(now, now, action, ids.length, properties, coords, 0, 0, 1, 1, 0, 0, 0, 0);
	}

	static String name(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				return "DOWN";
			case MotionEvent.ACTION_UP:
				return "UP";
			case MotionEvent.ACTION_MOVE:
				return "MOVE";
			case MotionEvent.ACTION_CANCEL:
				return "CANCEL";
			case MotionEvent.ACTION_POINTER_DOWN:
				return "POINTER_DOWN";
			case MotionEvent.ACTION_POINTER_UP:
				return "POINTER_UP";
			default:
				return "action " + event.getActionMasked();
		}
	}

	static void record(List<String> log, String who, MotionEvent event) {
		log.add(who + " " + name(event) + " " + (int)event.getX() + "," + (int)event.getY());
	}

	// ATL's entries for the hosts' input (what ViewRootImpl does), package-private in android.view
	static boolean key(View root, KeyEvent event) {
		return call("key", root, KeyEvent.class, event);
	}

	// a touchscreen's: a mouse would leave touch mode
	static boolean hostTouch(View root, MotionEvent event) {
		event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
		return call("touch", root, MotionEvent.class, event);
	}

	private static boolean call(String method, View root, Class<?> type, Object event) {
		try {
			Method entry = Class.forName("android.view.ATLInput").getDeclaredMethod(method, View.class, type);
			entry.setAccessible(true);
			return (Boolean)entry.invoke(null, root, event);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
