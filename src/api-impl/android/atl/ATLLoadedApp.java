package android.atl;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.Application;
import android.app.ContextImpl;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobService;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.location.LocationListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import dalvik.system.BaseDexClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.ref.CleanupDomain;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Hold ATL Specific application data to load apps
 */
public final class ATLLoadedApp {
	private final static String TAG = "ATLLoadedApp";
	private static ATLLoadedApp system_application;
	public final Resources default_resources;
	public final ClassLoader class_loader;
	public final PackageParser.Package pkg;
	public final int effective_sdk_compat;
	private final Object init_lock;
	private final HashMap<String, Service> running_services;
	private Application application;
	private Resources.Theme default_theme;
	/** The app's threads; a host's C half (atl_app_bind) gives it the app. */
	public final ThreadGroup group;
	private final CleanupDomain cleanup_domain;
	long native_app; // struct atl_app, 0 while no host runs this app; set once per VM
	/** @hide Looper's; written under Looper.class */
	public volatile Looper main_looper;
	/** Whether a host started the app (its Application, providers, onCreate); else it is only loaded. */
	public boolean started;
	/** The app's configuration: a copy of the display's that display changes update. */
	public final Configuration config;
	private PackageManager package_manager;

	// the app's share of what the framework keeps; the classes that own them use them
	public final Map<IntentFilter, BroadcastReceiver> receivers = new ConcurrentHashMap<>();
	public final Map<String, SharedPreferences> shared_prefs = new HashMap<>();
	public final HashMap<String, ATLProvider> providers = new HashMap<>(); // by authority
	public final Map<Integer, JobInfo> pending_jobs = new HashMap<>();
	public final IdentityHashMap<Class<? extends JobService>, JobService> job_services = new IdentityHashMap<>();
	public final HashMap<String, Constructor<? extends View>> view_constructors = new HashMap<>(); // by class name
	public final InputMethodManager input_method = new InputMethodManager();
	public final Set<LocationListener> location_listeners = new HashSet<>();
	public int mpris_notification_id = -1;
	// ATLInput's: Android starts touch devices in touch mode (config_defaultInTouchMode)
	public boolean touch_mode = true;
	public final Set<Integer> keys_falling_back = new HashSet<>();
	public boolean keyboard_took_back;
	/** @hide Toast's, made at the first show */
	public android.widget.Toast.Queue toasts;
	/** @hide the PopupMenu up, which takes Back: its native menu is no window of the registry */
	public android.widget.PopupMenu open_menu;
	// every Resources made for the app, for a configuration change (AOSP's ResourcesManager's)
	private final java.util.ArrayList<java.lang.ref.WeakReference<Resources>> made_resources = new java.util.ArrayList<>();

	ATLLoadedApp(Resources resources, ClassLoader classLoader,
	             PackageParser.Package pkg, Configuration config) {
		this.default_resources = resources;
		this.config = config;
		made_resources.add(new java.lang.ref.WeakReference<>(resources));
		this.class_loader = classLoader;
		this.pkg = pkg;
		this.effective_sdk_compat = Math.min(pkg.applicationInfo.targetSdkVersion, Build.VERSION.SDK_INT);
		this.init_lock = new Object();
		this.running_services = new HashMap<>();
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		while (root.getParent() != null)
			root = root.getParent();
		this.group = new ThreadGroup(root, pkg.packageName);
		this.cleanup_domain = new CleanupDomain(group);
	}

	/** The calling thread's app; null on a thread of no app. */
	public static native ATLLoadedApp ofCurrentThread();

	private static native void nativeEnd(long app);

	/** Ends the app (its instrumentation finished): its main loop stops; the other apps run on. */
	public void end() {
		nativeEnd(native_app);
	}

	/** The calling thread's app, for the APIs that have no answer without one. */
	public static ATLLoadedApp ofCurrentThreadOrThrow() {
		ATLLoadedApp app = ofCurrentThread();
		if (app == null)
			throw new IllegalStateException("no app runs on this thread");
		return app;
	}

	@NonNull
	public static ATLLoadedApp getSystemApplication() {
		if (system_application != null) {
			return system_application;
		}
		PackageParser packageParser = new PackageParser(null);
		Resources resources = Resources.getSystem();
		String[] outError = new String[1];
		PackageParser.Package pkg;
		try {
			pkg = packageParser.parsePackage(resources,
			                                 resources.getAssets().openXmlResourceParser(1, "AndroidManifest.xml"),
			                                 PackageParser.PARSE_IS_SYSTEM, outError);
		} catch (XmlPullParserException | IOException e) {
			throw new RuntimeException(outError[0], e);
		}
		packageParser.collectCertificates(pkg, PackageParser.PARSE_IS_SYSTEM);
		return system_application = new ATLLoadedApp(resources,
		                                             ATLLoadedApp.class.getClassLoader(), pkg, new Configuration(Context.sys_config));
	}

	/**
	 * A host's start of the app on its main thread, after the main Looper: its Application, its
	 * providers, the UnifiedPush export, Application.onCreate.
	 */
	public Application start(long native_window) {
		started = true;
		if (class_loader instanceof ATLClassLoader)
			((ATLClassLoader) class_loader).domain = cleanup_domain;
		Application application = getApplication();
		application.native_window = native_window;
		ContentProvider.createContentProviders(this);
		Context.exportUnifiedPush(this);
		application.onCreate();
		return application;
	}

	/** The app's APKs, the base first, as a class path. */
	public String classPath() {
		return classPath(pkg.applicationInfo);
	}

	public String dataDir() {
		return pkg.applicationInfo.dataDir;
	}

	/** The stored APKs, the base first, as a class path. */
	public static String classPath(ApplicationInfo info) {
		StringBuilder path = new StringBuilder(info.sourceDir);
		if (info.splitSourceDirs != null)
			for (String split : info.splitSourceDirs)
				path.append(File.pathSeparator).append(split);
		return path.toString();
	}

	/** An installed package's app: its APKs on the class path, ATL's natives and its own lib dir for native code. */
	@NonNull
	public static ATLLoadedApp load(PackageParser.Package pkg) {
		String classLoaderPath = classPath(pkg.applicationInfo);
		if (ATLToggles.ATL_GHAX) {
			// Always load ghax.jar as the first element to override application library classes
			classLoaderPath = ATLPaths.ghax_jar.getPath() + File.pathSeparator + classLoaderPath;
		}
		String nativePath = System.getProperty("java.library.path") + File.pathSeparator + pkg.applicationInfo.nativeLibraryDir;
		BaseDexClassLoader classLoader = new ATLClassLoader(classLoaderPath, nativePath, ATLLoadedApp.class.getClassLoader());
		AssetManager assetManager = new AssetManager(classLoaderPath + ":" + System.getProperty("java.class.path"),
		                                             pkg.applicationInfo.dataDir);
		Configuration config = new Configuration(Context.sys_config);
		Resources resources = new Resources(assetManager, new DisplayMetrics(), config);
		return new ATLLoadedApp(resources, classLoader, pkg, config);
	}

	public PackageManager getPackageManager() {
		synchronized (init_lock) {
			if (package_manager == null)
				package_manager = new PackageManager();
			return package_manager;
		}
	}

	public Resources createDefaultResources() {
		return this.createResources(this.default_resources.getDisplayMetrics(), config);
	}

	public Resources createResources(DisplayMetrics displayMetrics, Configuration configuration) {
		if (displayMetrics == null)
			displayMetrics = this.default_resources.getDisplayMetrics();
		if (configuration == null)
			configuration = config;
		Resources resources = new Resources(this.default_resources.getAssets(), displayMetrics, configuration);
		synchronized (made_resources) {
			for (java.util.Iterator<java.lang.ref.WeakReference<Resources>> it = made_resources.iterator(); it.hasNext();) {
				if (it.next().get() == null)
					it.remove();
			}
			made_resources.add(new java.lang.ref.WeakReference<>(resources));
		}
		return resources;
	}

	/* atl_nav_set_night's post, on the app's main thread: AOSP's ActivityThread.handleConfigurationChanged
	 * for the night bits. An activity whose manifest's configChanges covers uiMode hears it, any other
	 * keeps its configuration until a recreation (multi-app MA5). */
	private void applyNight(int night, Activity[] activities) {
		applySystemNight(night);
		int old = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (old == night)
			return;
		config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
		java.util.ArrayList<Resources> live = new java.util.ArrayList<>();
		synchronized (made_resources) {
			for (java.util.Iterator<java.lang.ref.WeakReference<Resources>> it = made_resources.iterator(); it.hasNext();) {
				Resources resources = it.next().get();
				if (resources == null)
					it.remove();
				else if (resources != default_resources)
					live.add(resources);
			}
		}
		live.add(default_resources); // last: the app's Resources share its AssetManager, whose configuration the last one sets
		for (Resources resources : live)
			setNight(resources, old, night);
		Configuration changed = new Configuration(config);
		if (application != null)
			application.onConfigurationChanged(changed);
		for (Activity activity : activities) {
			if ((manifestConfigChanges(activity) & android.content.pm.ActivityInfo.CONFIG_UI_MODE) == 0) {
				Log.i(TAG, activity.getClass().getName() + " doesn't handle uiMode; keeps its configuration");
				continue;
			}
			Configuration own = new Configuration(activity.getResources().getConfiguration());
			activity.onConfigurationChanged(own);
			activity.getWindow().getDecorView().dispatchConfigurationChanged(own);
		}
	}

	// a configuration context that overrides the night bits keeps them
	private static void setNight(Resources resources, int old, int night) {
		Configuration config = new Configuration(resources.getConfiguration());
		if ((config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != old)
			return;
		config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
		resources.updateConfiguration(config, resources.getDisplayMetrics());
	}

	// the VM's display configuration, once per change whichever app's post comes first
	private static synchronized void applySystemNight(int night) {
		int old = Context.sys_config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (old == night)
			return;
		Context.sys_config.uiMode = (Context.sys_config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
		if (system_application != null)
			system_application.config.uiMode = Context.sys_config.uiMode;
		setNight(Resources.getSystem(), old, night);
	}

	// the manifest's: PackageManager.getActivityInfo claims uiMode for every activity (AppCompat's check)
	private int manifestConfigChanges(Activity activity) {
		for (PackageParser.Activity info : pkg.activities) {
			if (info.className.equals(activity.getClass().getName()))
				return info.info.configChanges;
		}
		return 0;
	}

	public ContextImpl createContext(DisplayMetrics displayMetrics, Configuration configuration, int theme) {
		return new ContextImpl(this.createResources(displayMetrics, configuration), this, theme);
	}

	public ContextImpl createContext(DisplayMetrics displayMetrics, Configuration configuration, Resources.Theme theme) {
		return new ContextImpl(this.createResources(displayMetrics, configuration), this, theme);
	}

	public Resources.Theme getDefaultTheme() {
		if (this.default_theme != null)
			return this.default_theme;
		synchronized (this.init_lock) {
			if (this.default_theme != null)
				return this.default_theme;
			this.default_theme = this.default_resources.newTheme();
			this.default_theme.applyStyle(this.pkg.applicationInfo.theme, true);
		}
		return this.default_theme;
	}

	public Application getApplicationUnsafe() throws ReflectiveOperationException {
		if (this.application != null)
			return application;
		final String applicationClassName = pkg.applicationInfo.className;
		synchronized (this.init_lock) {
			if (application != null)
				return application;
			if (applicationClassName != null) {
				Class<? extends Application> cls =
				    this.loadClass(pkg.applicationInfo.className)
					.asSubclass(Application.class);
				Constructor<? extends Application> constructor = cls.getConstructor();
				application = constructor.newInstance();
			} else {
				application = new Application();
			}
			application.atl_attach_base_context(new ContextImpl(
			    this.createDefaultResources(), this, pkg.applicationInfo.theme));
			// HACK: Set WhatsApp's custom logging mechanism to verbose for easier debugging.
			// Should be removed again once WhatsApp is fully supported
			try {
				this.loadClass("com.whatsapp.util.Log").getField("level").setInt(null, 5);
			} catch (Exception ignore) {
			}
		}
		return application;
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return Class.forName(name, false, class_loader);
	}

	public Application getApplication() {
		try {
			return this.getApplicationUnsafe();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** processApp: the running app whose window and main thread the activity gets */
	public Activity createActivity(String className, Intent intent, ATLLoadedApp processApp)
	    throws ReflectiveOperationException {
		int theme_res = 0;
		int label_res = 0;
		int soft_input_mode = 0;
		for (PackageParser.Activity activity : pkg.activities) {
			if (className.equals(activity.className)) {
				label_res = activity.info.labelRes;
				theme_res = activity.info.getThemeResource();
				soft_input_mode = activity.info.softInputMode;
				break;
			}
		}

		theme_res = Resources.selectDefaultTheme(theme_res, this.effective_sdk_compat);

		Class<? extends Activity> cls = this.loadClass(className).asSubclass(Activity.class);
		Constructor<? extends Activity> constructor = cls.getConstructor();
		Activity activity = constructor.newInstance();
		intent.setComponent(new ComponentName(pkg.packageName, className));
		activity.intent = intent;
		Resources r = this.createDefaultResources();
		activity.atl_attach_base_context(new ContextImpl(r, this, processApp, theme_res));
		if (label_res != 0) {
			activity.atlAttachTitle(r.getText(label_res));
		} else if (pkg.applicationInfo.labelRes != 0) {
			activity.atlAttachTitle(r.getText(pkg.applicationInfo.labelRes));
		}
		activity.getWindow().setSoftInputMode(soft_input_mode); // Activity.attach's
		return activity;
	}

	/** processApp: the caller's running app, whose process (main thread) runs the service, also another package's */
	public ComponentName startOrBindService(final Intent intent, final ServiceConnection serviceConnection, final ATLLoadedApp processApp) {
		ComponentName component = intent.getComponent();
		if (component == null) {
			int priority = Integer.MIN_VALUE;
			for (PackageParser.Service service : pkg.services) {
				if (service.intents == null)
					continue;
				for (PackageParser.IntentInfo intentInfo : service.intents) {
					if (intentInfo.matchAction(intent.getAction()) && intentInfo.priority > priority) {
						component = new ComponentName(pkg.packageName, service.className);
						priority = intentInfo.priority;
						break;
					}
				}
			}
		}
		if (component == null) {
			Slog.w(TAG, "startService: no matching service found for intent: " + intent);
			return null;
		}
		final String className = component.getClassName();

		new Handler(processApp.main_looper).post(new Runnable() {
			@Override
			public void run() {
				try {
					Service service = running_services.get(className);
					if (service == null) {
						Class<? extends Service> cls = ATLLoadedApp.this.loadClass(className).asSubclass(Service.class);
						service = cls.getConstructor().newInstance();
						service.attachBaseContext(new ContextImpl(
						    ATLLoadedApp.this.createDefaultResources(), ATLLoadedApp.this, processApp,
						    pkg.applicationInfo.theme));
						service.onCreate();
						running_services.put(className, service);
					}

					if (serviceConnection != null) {
						serviceConnection.onServiceConnected(intent.getComponent(), service.onBind(intent));
					} else {
						service.onStartCommand(intent, 0, 0);
					}
				} catch (ReflectiveOperationException e) {
					Slog.e(TAG, "startService: failed to start service " + className, e);
				}
			}
		});

		return component;
	}

	public boolean stopService(Intent intent) {
		if (intent.getComponent() == null)
			return false;
		String className = intent.getComponent().getClassName();
		Service service = running_services.get(className);
		if (service == null)
			return false;
		service.onDestroy();
		running_services.remove(className);
		return true;
	}

	public void receiveBroadcast(Context context, Intent intent) {
		ComponentName target = intent.getComponent();
		if ((target != null && !target.getPackageName().equals(pkg.packageName)) ||
		    (intent.getPackage() != null && !intent.getPackage().equals(pkg.packageName)))
			return;
		for (PackageParser.Activity receiver : pkg.receivers) {
			boolean matches = target != null ? target.getClassName().equals(receiver.className) : false;
			if (target == null && receiver.intents != null) {
				for (PackageParser.IntentInfo intentInfo : receiver.intents)
					matches |= intentInfo.match(null, intent, false, TAG) >= 0;
			}
			if (!matches)
				continue;
			try {
				Class<? extends BroadcastReceiver> cls = this.loadClass(receiver.className).asSubclass(BroadcastReceiver.class);
				BroadcastReceiver receiverInstance = cls.newInstance();
				receiverInstance.onReceive(context, intent);
			} catch (ReflectiveOperationException e) {
				Slog.e(TAG, "startService: failed to receive broadcast " + receiver.className, e);
			}
		}
	}

	public long getNativeWindow() {
		return this.application == null ? 0 : this.application.native_window;
	}
}
