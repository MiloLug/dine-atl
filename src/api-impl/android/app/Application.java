/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/app/Application.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.app;

import android.R;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageParser;
import android.content.res.Configuration;
import android.os.Bundle;

public class Application extends ContextWrapper {
	public long native_window;

	private String get_app_icon_path() {
		String icon_path = null;
		try {
			icon_path = getString(this.get_atl_loaded_app().pkg.applicationInfo.icon);
		} catch (android.content.res.Resources.NotFoundException e) {
			e.printStackTrace();
		}
		if (icon_path == null) {
			icon_path = getString(R.mipmap.sym_def_app_icon);
		} else if (icon_path.endsWith(".xml")) {
			icon_path = null;
		}
		return icon_path;
	}

	private long get_app_icon_paintable() {
		return getPackageManager().getApplicationIcon(this.get_atl_loaded_app().pkg.applicationInfo).paintable;
	}

	String get_app_label() {
		return getString(this.get_atl_loaded_app().pkg.applicationInfo.labelRes);
	}

	String get_supported_mime_types() {
		StringBuilder mimeTypes = new StringBuilder();
		for (PackageParser.Activity activity : this.get_atl_loaded_app().pkg.activities) {
			if (activity.intents == null)
				continue;
			for (PackageParser.IntentInfo intent : activity.intents) {
				for (int i = 0; i < intent.countDataSchemes(); i++) {
					String scheme = intent.getDataScheme(i);
					// ignore http and https, as there is no way to only handle specific hosts in a .desktop file
					if (!"http".equals(scheme) && !"https".equals(scheme))
						mimeTypes.append("x-scheme-handler/").append(intent.getDataScheme(i)).append(";");
				}
			}
		}
		return (mimeTypes.length() == 0) ? null : mimeTypes.toString();
	}

	/* Copyright (C) 2006 The Android Open Source Project: ActivityLifecycleCallbacks, its API 29
	 * pre and post methods, and the dispatch below */
	public interface ActivityLifecycleCallbacks {
		default void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {}
		void onActivityCreated(Activity activity, Bundle savedInstanceState);
		default void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {}
		default void onActivityPreStarted(Activity activity) {}
		void onActivityStarted(Activity activity);
		default void onActivityPostStarted(Activity activity) {}
		default void onActivityPreResumed(Activity activity) {}
		void onActivityResumed(Activity activity);
		default void onActivityPostResumed(Activity activity) {}
		default void onActivityPrePaused(Activity activity) {}
		void onActivityPaused(Activity activity);
		default void onActivityPostPaused(Activity activity) {}
		default void onActivityPreStopped(Activity activity) {}
		void onActivityStopped(Activity activity);
		default void onActivityPostStopped(Activity activity) {}
		default void onActivityPreSaveInstanceState(Activity activity, Bundle outState) {}
		void onActivitySaveInstanceState(Activity activity, Bundle outState);
		default void onActivityPostSaveInstanceState(Activity activity, Bundle outState) {}
		default void onActivityPreDestroyed(Activity activity) {}
		void onActivityDestroyed(Activity activity);
		default void onActivityPostDestroyed(Activity activity) {}
	}

	private final java.util.ArrayList<ActivityLifecycleCallbacks> activity_lifecycle_callbacks = new java.util.ArrayList<>();

	// a copy: a callback may register or unregister one
	ActivityLifecycleCallbacks[] collectActivityLifecycleCallbacks() {
		synchronized (activity_lifecycle_callbacks) {
			return activity_lifecycle_callbacks.toArray(new ActivityLifecycleCallbacks[0]);
		}
	}
	/**
	 * Callback interface for use with {@link Application#registerOnProvideAssistDataListener}
	 * and {@link Application#unregisterOnProvideAssistDataListener}.
	 */
	public interface OnProvideAssistDataListener {
		/**
		 * This is called when the user is requesting an assist, to build a full
		 * {@link Intent#ACTION_ASSIST} Intent with all of the context of the current
		 * application.  You can override this method to place into the bundle anything
		 * you would like to appear in the {@link Intent#EXTRA_ASSIST_CONTEXT} part
		 * of the assist Intent.
		 */
		public void onProvideAssistData(Activity activity, Bundle data);
	}

	public Application() {
		super(null);
	}
	/**
	 * Called when the application is starting, before any activity, service,
	 * or receiver objects (excluding content providers) have been created.
	 * Implementations should be as quick as possible (for example using
	 * lazy initialization of state) since the time spent in this function
	 * directly impacts the performance of starting the first activity,
	 * service, or receiver in a process.
	 * If you override this method, be sure to call super.onCreate().
	 */
	public void onCreate() {
	}
	/**
	 * This method is for use in emulated process environments.  It will
	 * never be called on a production Android device, where processes are
	 * removed by simply killing them; no user code (including this callback)
	 * is executed when doing so.
	 */
	public void onTerminate() {
	}
	public void onConfigurationChanged(Configuration newConfig) {
	}
	public void onLowMemory() {
	}
	public void onTrimMemory(int level) {
	}
	/*public void registerComponentCallbacks(ComponentCallbacks callback) {
	}
	public void unregisterComponentCallbacks(ComponentCallbacks callback) {
	}*/
	public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
		synchronized (activity_lifecycle_callbacks) {
			activity_lifecycle_callbacks.add(callback);
		}
	}
	public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
		synchronized (activity_lifecycle_callbacks) {
			activity_lifecycle_callbacks.remove(callback);
		}
	}
	public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
	}
	public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
	}
	public static String getProcessName() {
		// the package: android:process is ignored
		return ActivityThread.currentPackageName();
	}
}
