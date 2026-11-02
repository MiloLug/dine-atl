package dev.dine.pkg.driver;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the way apps do, for package.py's scenarios: DINE_TEST_SCENARIO is <kind>:<argument>, a
 * fixture APK name or a package. Prints "scenario: ..." lines for the harness.
 */
public class Main extends Activity {
	private static final String STATUS = "dev.dine.pkg.driver.STATUS";
	private String kind;

	private static void log(String line) {
		System.out.println("scenario: " + line);
		System.out.flush();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String scenario = System.getenv("DINE_TEST_SCENARIO");
		kind = scenario.substring(0, scenario.indexOf(':'));
		String argument = scenario.substring(scenario.indexOf(':') + 1);
		listenForPackages();
		try {
			switch (kind) {
				case "session":
				case "session-app-context":
				case "self-update":
					session(fixture(argument));
					break;
				case "intent-install":
					startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromFile(fixture(argument)))
					                           .putExtra(Intent.EXTRA_RETURN_RESULT, true), 1);
					break;
				case "intent-uninstall":
					startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + argument))
					                           .putExtra(Intent.EXTRA_RETURN_RESULT, true), 2);
					break;
				default:
					log("unknown scenario " + scenario);
					finish();
			}
		} catch (Exception e) {
			log("failed " + e);
			finish();
		}
	}

	private static File fixture(String name) {
		return new File(System.getenv("DINE_TEST_FIXTURES"), name + ".apk");
	}

	private void listenForPackages() {
		IntentFilter scoped = new IntentFilter();
		scoped.addAction(Intent.ACTION_PACKAGE_ADDED);
		scoped.addAction(Intent.ACTION_PACKAGE_REMOVED);
		scoped.addAction(Intent.ACTION_PACKAGE_REPLACED);
		scoped.addDataScheme("package");
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String what = intent.getAction().substring(intent.getAction().lastIndexOf('_') + 1).toLowerCase();
				log(what + " " + intent.getData().getSchemeSpecificPart() + " replacing=" + intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));
			}
		}, scoped);
		// without the package scheme: Android doesn't deliver these broadcasts to it
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				log("unscoped " + intent.getAction());
			}
		}, new IntentFilter(Intent.ACTION_PACKAGE_ADDED));
	}

	private void session(File apk) throws Exception {
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 99);
				log("status " + status + " " + intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
				if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
					Intent confirm = (Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT);
					Context starter = kind.equals("session-app-context") ? getApplicationContext() : Main.this;
					starter.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} else if (!kind.equals("self-update")) {
					finishSoon();
				}
			}
		}, new IntentFilter(STATUS));
		PackageInstaller installer = getPackageManager().getPackageInstaller();
		int id = installer.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
		PackageInstaller.Session session = installer.openSession(id);
		try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
			byte[] buffer = new byte[64 * 1024];
			for (int n; (n = in.read(buffer)) > 0;)
				out.write(buffer, 0, n);
			session.fsync(out);
		}
		Intent status = new Intent(STATUS).setPackage(getPackageName());
		session.commit(PendingIntent.getBroadcast(this, id, status, 0).getIntentSender());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		log("result " + resultCode + " " + data.getIntExtra(Intent.EXTRA_INSTALL_RESULT, 0));
		finishSoon();
	}

	// after the broadcasts that follow the result
	private void finishSoon() {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				finish();
			}
		});
	}
}
