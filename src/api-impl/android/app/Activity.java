/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/app/Activity.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.app;

import android.app.ActionBar;
import android.atl.ATLInstallerUi;
import android.atl.ATLLoadedApp;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageParser;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Activity extends ContextThemeWrapper implements Window.Callback, KeyEvent.Callback, LayoutInflater.Factory2, android.content.ComponentCallbacks2 {
	private final static String TAG = "Activity";

	public static final int RESULT_CANCELED = 0;
	public static final int RESULT_OK = -1;

	Window window;
	int requested_orientation = -1 /*ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED*/; // dummy
	public Intent intent;
	private Activity resultActivity;
	private int resultRequestCode;
	private boolean paused = false;
	private boolean stopped = false; // set by native code: onStop ran, onRestart is due
	private CharSequence title = null;
	private ActionBar action_bar;
	private boolean title_ready; // AOSP's mTitleReady: from onPostCreate
	private boolean has_permissions_request;
	List<Fragment> fragments = new ArrayList<>();
	boolean destroyed = false;
	private boolean finishing = false;

	public static Activity internalCreateActivity(ATLLoadedApp app, ATLLoadedApp processApp, String className, long native_window, Intent intent) throws ReflectiveOperationException {
		Activity activity = app.createActivity(className, intent, processApp);
		activity.window.set_native_window(native_window);
		return activity;
	}

	/**
	 * Helper function to be called from native code to construct main activity
	 *
	 * @param className  class name of activity or null
	 * @return  instance of main activity class
	 * @throws Exception
	 */
	private static Activity createMainActivity(ATLLoadedApp app, String className, long native_window, String uriString) throws ReflectiveOperationException {
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (className == null) {
			for (PackageParser.Activity activity : app.pkg.activities) {
				if (!activity.info.enabled)
					continue;
				boolean done = false;
				for (PackageParser.IntentInfo intent : activity.intents) {
					Slog.i(TAG, intent.toString());
					if ((uri == null && intent.hasCategory("android.intent.category.LAUNCHER") && intent.hasAction("android.intent.action.MAIN")) ||        // NOLINT
					    (uri != null && intent.hasDataScheme(uri.getScheme())                  && intent.hasCategory("android.intent.category.DEFAULT"))) { // NOLINT
						className = activity.info.targetActivity != null ? activity.info.targetActivity : activity.className;
						done = true;
						break;
					}
				}
				if (done)
					break;
			}
		} else {
			className = className.replace('/', '.');
		}
		if (className == null) {
			if (uri != null)
				System.err.println("Failed to find Activity to launch URI: " + uri);
			else
				System.err.println("Failed to find main Activity");
			System.exit(1);
		}
		return internalCreateActivity(app, app, className, native_window, uri != null ? new Intent("android.intent.action.VIEW", uri) : new Intent());
	}

	public Activity() {
		super(null);
	}

	public View root_view;

	public final Application getApplication() {
		return (Application)getApplicationContext();
	}

	public WindowManager getWindowManager() {
		return window.getWindowManager();
	}

	// AOSP's: the window service of an activity is its window's, which adds windows with its token
	@Override
	public Object getSystemService(String name) {
		if ("window".equals(name))
			return getWindowManager();
		return super.getSystemService(name);
	}

	@Override
	public Object getSystemService(Class<?> serviceClass) throws InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
		if (serviceClass == WindowManager.class)
			return getWindowManager();
		return super.getSystemService(serviceClass);
	}

	public String getCallingPackage() {
		return null; // [from api reference] Note: if the calling activity is not expecting a result (that is it did not use the startActivityForResult(Intent, int) form that includes a request code), then the calling package will be null.
	}

	public ComponentName getComponentName() {
		return intent.getComponent();
	}

	public Intent getIntent() {
		return intent;
	}

	public int getRequestedOrientation() {
		return requested_orientation;
	}

	public void setRequestedOrientation(int orientation) {
		requested_orientation = orientation;
	}

	public boolean isFinishing() {
		return finishing;
	}

	public final boolean requestWindowFeature(int featureId) {
		return getWindow().requestFeature(featureId);
	}

	public final void setVolumeControlStream(int streamType) {}

	/* Copyright (C) 2006 The Android Open Source Project: the lifecycle callbacks, the
	 * application's and this activity's (API 29), around and in the lifecycle calls */
	private final ArrayList<Application.ActivityLifecycleCallbacks> lifecycle_callbacks = new ArrayList<>();

	public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
		synchronized (lifecycle_callbacks) {
			lifecycle_callbacks.add(callback);
		}
	}

	public void unregisterActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
		synchronized (lifecycle_callbacks) {
			lifecycle_callbacks.remove(callback);
		}
	}

	private interface LifecycleCall {
		void call(Application.ActivityLifecycleCallbacks callbacks);
	}

	// the lifecycle callbacks in AOSP's order
	private void dispatch(LifecycleCall call, boolean application_first, boolean reversed) {
		Application application = getApplication();
		Application.ActivityLifecycleCallbacks[] own;
		synchronized (lifecycle_callbacks) {
			own = lifecycle_callbacks.toArray(new Application.ActivityLifecycleCallbacks[0]);
		}
		if (application_first && application != null)
			for (Application.ActivityLifecycleCallbacks callbacks : application.collectActivityLifecycleCallbacks())
				call.call(callbacks);
		for (int i = 0; i < own.length; i++)
			call.call(own[reversed ? own.length - 1 - i : i]);
		if (!application_first && application != null)
			for (Application.ActivityLifecycleCallbacks callbacks : application.collectActivityLifecycleCallbacks())
				call.call(callbacks);
	}

	// what the native lifecycle calls (shared/activity.c through handle_cache) run
	final void atlPerformCreate(final Bundle savedInstanceState) {
		dispatch(c -> c.onActivityPreCreated(this, savedInstanceState), true, false);
		onCreate(savedInstanceState);
		dispatch(c -> c.onActivityPostCreated(this, savedInstanceState), false, false);
	}

	final void atlPerformStart() {
		dispatch(c -> c.onActivityPreStarted(this), true, false);
		onStart();
		dispatch(c -> c.onActivityPostStarted(this), false, false);
	}

	final void atlPerformResume() {
		dispatch(c -> c.onActivityPreResumed(this), true, false);
		onResume();
	}

	final void atlPerformPostResume() {
		onPostResume();
		dispatch(c -> c.onActivityPostResumed(this), false, false);
	}

	final void atlPerformPause() {
		dispatch(c -> c.onActivityPrePaused(this), true, true);
		onPause();
		dispatch(c -> c.onActivityPostPaused(this), false, true);
	}

	final void atlPerformStop() {
		dispatch(c -> c.onActivityPreStopped(this), true, true);
		onStop();
		dispatch(c -> c.onActivityPostStopped(this), false, true);
	}

	final void atlPerformDestroy() {
		dispatch(c -> c.onActivityPreDestroyed(this), true, true);
		onDestroy();
		dispatch(c -> c.onActivityPostDestroyed(this), false, true);
	}

	protected void onCreate(Bundle savedInstanceState) {
		Slog.i(TAG, "- onCreate - yay!");
		dispatch(c -> c.onActivityCreated(this, savedInstanceState), true, false);

		for (Fragment fragment : fragments) {
			fragment.onCreate(savedInstanceState);
		}

		return;
	}

	/* Copyright (C) 2006 The Android Open Source Project: the title */
	protected void onPostCreate(Bundle savedInstanceState) {
		Slog.i(TAG, "- onPostCreate - yay!");
		title_ready = true;
		onTitleChanged(getTitle(), 0);
		return;
	}

	protected void onStart() {
		Slog.i(TAG, "- onStart - yay!");
		dispatch(c -> c.onActivityStarted(this), true, false);

		for (Fragment fragment : fragments) {
			fragment.onStart();
		}

		return;
	}

	protected void onRestart() {
		Slog.i(TAG, "- onRestart - yay!");

		return;
	}

	protected void onResume() {
		Slog.i(TAG, "- onResume - yay!");
		dispatch(c -> c.onActivityResumed(this), true, false);

		for (Fragment fragment : fragments) {
			fragment.onResume();
		}

		paused = false;
		return;
	}

	protected void onPostResume() {
		Slog.i(TAG, "- onPostResume - yay!");
		return;
	}

	protected void onPause() {
		Slog.i(TAG, "- onPause - yay!");
		dispatch(c -> c.onActivityPaused(this), false, true);

		for (Fragment fragment : fragments) {
			fragment.onPause();
		}

		paused = true;
		return;
	}

	protected void onStop() {
		Slog.i(TAG, "- onStop - yay!");
		dispatch(c -> c.onActivityStopped(this), false, true);

		for (Fragment fragment : fragments) {
			fragment.onStop();
		}

		return;
	}

	protected void onDestroy() {
		Slog.i(TAG, "- onDestroy - yay!");
		dispatch(c -> c.onActivityDestroyed(this), false, true);

		for (Fragment fragment : fragments) {
			fragment.onDestroy();
		}

		destroyed = true;
		return;
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		Slog.i(TAG, "- onWindowFocusChanged - yay! (hasFocus: " + hasFocus + ")");

		return;
	}

	protected void onSaveInstanceState(Bundle outState) {
	}

	public void onConfigurationChanged(Configuration newConfig) {
	}

	public void onLowMemory() {
	}

	public void onTrimMemory(int level) {
	}

	/* --- */

	public void setContentView(int layoutResID) throws Exception {
		Slog.i(TAG, "- setContentView - yay!");

		root_view = getLayoutInflater().inflate(layoutResID, null, false);

		window.setContentView(root_view);
		onContentChanged();
	}

	public void setContentView(View view, ViewGroup.LayoutParams layoutParams) {
		setContentView(view);
	}

	public void setContentView(View view) {
		window.setContentView(view);
		onContentChanged();
	}

	public <T extends android.view.View> T findViewById(int id) {
		View view = window.findViewById(id);

		return (T)view;
	}

	public void invalidateOptionsMenu() {
		Slog.i(TAG, "invalidateOptionsMenu() called, should we do something?");
	}

	public Window getWindow() {
		return this.window;
	}

	public Display getDisplay() {
		return new Display();
	}

	public final void runOnUiThread(Runnable action) {
		if (Looper.myLooper() == getMainLooper()) {
			action.run();
		} else {
			new Handler(getMainLooper()).post(action);
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

	/** @hide for results that come from outside an activity (ATLInstallerUi) */
	public void dispatchActivityResult(int requestCode, int resultCode, Intent data) {
		onActivityResult(requestCode, resultCode, data);
	}

	// the order must match GtkFileChooserAction enum
	private static final List<String> FILE_CHOOSER_ACTIONS = Arrays.asList(
	    "android.intent.action.OPEN_DOCUMENT",     // (0) GTK_FILE_CHOOSER_ACTION_OPEN
	    "android.intent.action.CREATE_DOCUMENT",   // (1) GTK_FILE_CHOOSER_ACTION_SAVE
	    "android.intent.action.OPEN_DOCUMENT_TREE" // (2) GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER
	);

	// callback from native code
	protected void fileChooserResultCallback(int requestCode, int resultCode, int action, String uri) {
		onActivityResult(requestCode, resultCode, new Intent(FILE_CHOOSER_ACTIONS.get(action), uri != null ? Uri.parse(uri) : null));
	}

	public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
		Slog.i(TAG, "startActivityForResult(" + intent + ", " + requestCode + "," + options + ") called");
		if (ATLInstallerUi.startActivity(this, intent, this, requestCode)) {
			return;
		} else if (intent.getComponent() != null) {
			try {
				final Activity activity = internalCreateActivity(get_atl_loaded_app(), atl_process_app(), intent.getComponent().getClassName(), getWindow().native_window, intent);
				activity.resultRequestCode = requestCode;
				activity.resultActivity = this;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						nativeStartActivity(activity);
					}
				});
			} catch (ReflectiveOperationException e) {
				onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
			}
		} else if (FILE_CHOOSER_ACTIONS.contains(intent.getAction())) {
			nativeFileChooser(FILE_CHOOSER_ACTIONS.indexOf(intent.getAction()), intent.getType(), intent.getStringExtra("android.intent.extra.TITLE"), requestCode);
		} else if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
			nativeFileChooser(0 /*GTK_FILE_CHOOSER_ACTION_OPEN*/, intent.getType(), intent.getStringExtra(Intent.EXTRA_TITLE), requestCode);
		} else {
			Slog.i(TAG, "startActivityForResult: intent was not handled. Calling onActivityResult(RESULT_CANCELED).");
			onActivityResult(requestCode, 0 /*RESULT_CANCELED*/, new Intent());
		}
	}
	public void startActivityForResult(Intent intent, int requestCode) {
		startActivityForResult(intent, requestCode, null);
	}

	public void setResult(int resultCode, Intent data) {
		if (resultActivity != null) {
			resultActivity.onActivityResult(resultRequestCode, resultCode, data);
		}
	}

	public void setResult(int resultCode) {
		setResult(resultCode, null);
	}

	protected Dialog onCreateDialog(int id) {
		Slog.i(TAG, "Activity.onCreateDialog(" + id + ") called");
		return null;
	}

	protected void onPrepareDialog(int id, Dialog dialog) {
		Slog.i(TAG, "Activity.onPrepareDialog(" + id + ") called");
	}

	private Map<Integer, Dialog> dialogs = new HashMap<Integer, Dialog>();

	public final void showDialog(int id) {
		Slog.i(TAG, "Activity.showDialog(" + id + ") called");
		Dialog dialog = dialogs.get(id);
		if (dialog == null)
			dialogs.put(id, dialog = onCreateDialog(id));
		if (dialog == null) {
			Slog.w(TAG, "Dialog " + id + " was not created");
			return;
		}
		onPrepareDialog(id, dialog);
		dialog.show();
	}

	public boolean showDialog(int id, Bundle args) {
		return false;
	}

	public void removeDialog(int id) {
		Dialog dialog = dialogs.remove(id);
		if (dialog != null)
			dialog.dismiss();
	}

	public void finish() {
		finishing = true;
		new Handler(getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (window != null && window.native_window != 0) {
					nativeFinish(getWindow().native_window);
					window.native_window = 0;
				}
			}
		});
	}

	public Object getLastNonConfigurationInstance() {
		return null;
	}

	public FragmentManager getFragmentManager() {
		return new FragmentManager(this);
	}

	public LayoutInflater getLayoutInflater() {
		return (LayoutInflater)getSystemService("layout_inflater");
	}

	public boolean isChangingConfigurations() { return false; }

	@Override
	public void onContentChanged() {
		Slog.i(TAG, "- onContentChanged - yay!");
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onCreatePanelMenu(int featureId, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			// HACK: catch non critical error occuring in Open Sudoku app
			try {
				return onCreateOptionsMenu(menu);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	@Override
	public View onCreatePanelView(int featureId) {
		return null;
	}

	public MenuInflater getMenuInflater() {
		return new MenuInflater(this);
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onPreparePanel(int featureId, View view, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL && menu != null) {
			return onPrepareOptionsMenu(menu);
		}
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			return onOptionsItemSelected(item);
		}
		return false;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	public void onOptionsMenuClosed(Menu menu) {}

	@Override
	public void onPanelClosed(int featureId, Menu menu) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			onOptionsMenuClosed(menu);
		}
	}

	// attach's label: the app's onTitleChanged runs only from setTitle and onPostCreate
	public void atlAttachTitle(CharSequence title) {
		this.title = title;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public void setTitle(CharSequence title) {
		this.title = title;
		onTitleChanged(title, 0);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	protected void onTitleChanged(CharSequence title, int color) {
		if (title_ready) {
			window.setTitle(title);
			if (action_bar != null)
				action_bar.setWindowTitle(title);
		}
	}

	public void setTitle(int titleId) {
		setTitle(getText(titleId));
	}

	public CharSequence getTitle() {
		return title;
	}

	// Android 12+: the task root moves to the back instead of finishing, when the host lets it
	public void onBackPressed() {
		System.out.println("onBackPressed() called");
		if (!isTaskRoot() || !moveTaskToBack(false))
			finish();
	}

	public void setIntent(Intent newIntent) {
		this.intent = newIntent;
	}

	public Intent getParentActivityIntent() {
		return null;
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		Slog.i(TAG, "onMenuOpened(" + featureId + ", " + menu + ") called");
		return false;
	}

	public void recreate() {
		finishing = true;
		try {
			/* TODO: check if this is a toplevel activity */
			Activity activity = internalCreateActivity(get_atl_loaded_app(), atl_process_app(), this.getClass().getName(), getWindow().native_window, intent);
			new Handler().post(new Runnable() {
				@Override
				public void run() {
					nativeFinish(0);
					nativeStartActivity(activity);
				}
			});
		} catch (ReflectiveOperationException e) {
			Slog.i(TAG, "exception in Activity.recreate, this is kinda sus");
			e.printStackTrace();
		}
	}

	public String getLocalClassName() {
		final String pkg = getPackageName();
		final String cls = this.getClass().getName();
		int packageLen = pkg.length();
		if (!cls.startsWith(pkg) || cls.length() <= packageLen || cls.charAt(packageLen) != '.') {
			return cls;
		}
		return cls.substring(packageLen + 1);
	}

	public SharedPreferences getPreferences(int mode) {
		return getSharedPreferences(getLocalClassName(), mode);
	}

	protected void onNewIntent(Intent intent) {}

	public final Activity getParent() {
		return null;
	}

	public boolean hasWindowFocus() {
		return true; // FIXME?
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public void finishAffinity() {
		finish();
	}

	public void overridePendingTransition(int enterAnim, int exitAnim) {}

	public native boolean isTaskRoot();
	private native boolean nativeMoveTaskToBack();

	public void postponeEnterTransition() {}

	public void startPostponedEnterTransition() {}

	public boolean isChild() {
		return false;
	}

	public void setTaskDescription(ActivityManager.TaskDescription description) {}

	private native void nativeFinish(long native_window);
	public static native void nativeStartActivity(Activity activity);
	public static native boolean nativeResumeActivity(Context context, Class<? extends Activity> activityClass, Intent intent);
	public static native void nativeOpenURI(String uri);
	public native void nativeFileChooser(int action, String type, String title, int requestCode);
	public void reportFullyDrawn() {}
	public void setVisible(boolean visible) {}
	public Uri getReferrer() { return null; }
	public void setDefaultKeyMode(int flag) {}
	public void registerForContextMenu(View view) {}
	public native boolean isInMultiWindowMode();

	// we never put an activity into picture-in-picture, so this is always false
	public boolean isInPictureInPictureMode() { return false; }


	public void setDisablePreviewScreenshots(boolean disable) {}
	public final View requireViewById(int id) {
		View view = findViewById(id);
		if (view == null)
			throw new IllegalArgumentException("ID does not reference a View inside this View");
		return view;
	}

	public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
		return null;
	}

	public boolean onSearchRequested() {
		return false;
	}

	public View getCurrentFocus() {
		return window != null ? window.getCurrentFocus() : null;
	}

	public void setProgressBarIndeterminateVisibility(boolean indeterminate) {}

	public int getChangingConfigurations() {
		return 0;
	}

	public int getTaskId() {
		/* we don't support multiple activity stacks, so this is probably fine? */
		return System.identityHashCode(this.getApplicationContext());
	}

	public boolean moveTaskToBack(boolean nonRoot) {
		if (!nonRoot && !isTaskRoot())
			return false;
		return nativeMoveTaskToBack();
	}

	void setFinishOnTouchOutside(boolean finish) {
	}

	public void closeOptionsMenu() {
	}

	public void finishActivity(int requestCode) {
		/* TODO: track started activities so we can finish the right one here */
		Slog.w(TAG, "finishActivity: stub");
	}

	public void finishAfterTransition() {
		finish();
	}

	/* Copyright (C) 2006 The Android Open Source Project: dispatchKeyEvent to onUserInteraction */
	public boolean dispatchKeyEvent(KeyEvent event) {
		onUserInteraction();
		Window win = getWindow();
		if (win.superDispatchKeyEvent(event)) {
			return true;
		}
		View decor = win.getDecorView();
		return event.dispatch(this, decor != null ? decor.getKeyDispatcherState() : null, this);
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			event.startTracking();
			return true;
		}
		return false;
	}

	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
			onBackPressed();
			return true;
		}
		return false;
	}

	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		return false;
	}

	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			onUserInteraction();
		}
		if (getWindow().superDispatchTouchEvent(ev)) {
			return true;
		}
		return onTouchEvent(ev);
	}

	public boolean onTouchEvent(MotionEvent event) {
		return false;
	}

	public void onUserInteraction() {}

	/* Copyright (C) 2006 The Android Open Source Project: requestPermissions to
	 * shouldShowRequestPermissionRationale. No dialog asks the user: each permission is answered
	 * from checkSelfPermission, after the call returns, as the permission activity's result would. */
	public final void requestPermissions(String[] permissions, int requestCode) {
		request_permissions(permissions, requestCode, null);
	}

	final void request_permissions(final String[] permissions, final int requestCode, final Fragment fragment) {
		if (requestCode < 0)
			throw new IllegalArgumentException("requestCode should be >= 0");
		if (has_permissions_request) {
			Slog.w(TAG, "Can request only one set of permissions at a time");
			// empty arrays are a cancellation
			dispatch_permissions_result(fragment, requestCode, new String[0], new int[0]);
			return;
		}
		has_permissions_request = true;
		final int[] results = new int[permissions.length];
		for (int i = 0; i < permissions.length; i++)
			results[i] = checkSelfPermission(permissions[i]);
		new Handler(getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				has_permissions_request = false;
				dispatch_permissions_result(fragment, requestCode, permissions, results);
			}
		});
	}

	private void dispatch_permissions_result(Fragment fragment, int requestCode, String[] permissions, int[] results) {
		if (fragment != null)
			fragment.onRequestPermissionsResult(requestCode, permissions, results);
		else
			onRequestPermissionsResult(requestCode, permissions, results);
	}

	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

	public boolean shouldShowRequestPermissionRationale(String permission) {
		return false;
	}

	/* Copyright (C) 2006 The Android Open Source Project: initWindowDecorActionBar */
	public ActionBar getActionBar() {
		window.getDecorView(); // the theme's features
		if (action_bar == null && window.hasFeature(Window.FEATURE_ACTION_BAR))
			action_bar = new ATLActionBar(this);
		return action_bar;
	}

	@Override
	public void atl_attach_base_context(Context baseContext) {
		super.atl_attach_base_context(baseContext);
		this.window = new Window(this, this);
	}

	public void applyOverrideConfiguration(Configuration overrideConfiguration) {}

	public ComponentName getCallingActivity() {
		return resultActivity == null ? null : resultActivity.getComponentName();
	}

	public boolean onNavigateUp() {
		return false;
	}

	public void setExitSharedElementCallback(SharedElementCallback callback) {}

	public void setEnterSharedElementCallback(SharedElementCallback callback) {}
}
