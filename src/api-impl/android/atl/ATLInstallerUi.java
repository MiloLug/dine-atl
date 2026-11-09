package android.atl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * What an app sees of installing: the confirmation dialogs, ACTION_INSTALL_PACKAGE,
 * ACTION_UNINSTALL_PACKAGE and a session's confirm intent, and the PACKAGE_* broadcasts in this
 * process. ATLInstaller does the writing.
 */
public final class ATLInstallerUi {
	private static final String TAG = "ATLInstallerUi";
	private static final int RESULT_FIRST_USER = 1;

	private ATLInstallerUi() {}

	private interface Answer {
		void answer(boolean yes);
	}

	private interface Confirmed {
		void confirmed(boolean install, boolean allowFakeSignature);
	}

	/** startActivity's hook: true when the intent was an install or uninstall. resultTo, when set, gets the result. */
	public static boolean startActivity(Context context, Intent intent, Activity resultTo, int requestCode) {
		String action = intent.getAction();
		if (PackageInstaller.ACTION_CONFIRM_INSTALL.equals(action)) {
			onMain(context, () -> confirmSession(context, intent));
		} else if (Intent.ACTION_INSTALL_PACKAGE.equals(action)) {
			onMain(context, () -> install(context, intent, resultTo, requestCode));
		} else if ((Intent.ACTION_UNINSTALL_PACKAGE.equals(action) || Intent.ACTION_DELETE.equals(action)) &&
		           intent.getData() != null && "package".equals(intent.getData().getScheme())) {
			onMain(context, () -> uninstall(context, intent, resultTo, requestCode));
		} else {
			return false;
		}
		return true;
	}

	private static void onMain(Context context, Runnable runnable) {
		if (Looper.myLooper() == context.getMainLooper())
			runnable.run();
		else
			new Handler(context.getMainLooper()).post(runnable);
	}

	private static void ask(Context context, String title, String message, String yes, String no, Answer answer) {
		boolean[] answered = {false};
		Answer once = (yesNo) -> {
			if (!answered[0]) {
				answered[0] = true;
				answer.answer(yesNo);
			}
		};
		AlertDialog dialog = new AlertDialog.Builder(context)
		                         .setTitle(title)
		                         .setMessage(message)
		                         .setPositiveButton(yes, (d, which) -> once.answer(true))
		                         .setNegativeButton(no, (d, which) -> once.answer(false))
		                         .setOnCancelListener(d -> once.answer(false))
		                         .create();
		// the system's installer, no activity of the app's: a window of the app's own
		dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
		dialog.show();
	}

	/** The install question, then the fake-signature one when the package asks for it. */
	private static void confirm(Context context, ATLInstaller.Staged staged, Confirmed confirmed) {
		String name = staged.pkg.packageName;
		ask(context, "Install " + name + "?", "Version " + staged.pkg.mVersionName + " (" + staged.pkg.mVersionCode + ")",
		    "Install", "Cancel", install -> {
			    if (!install || staged.fakeSignature() == null) {
				    confirmed.confirmed(install, false);
				    return;
			    }
			    ask(context, "Allow " + name + " a fake signature?",
			        name + " asks to be reported as signed with another key (FAKE_PACKAGE_SIGNATURE), as microG asks for Google's. Apps that check signatures will believe it.",
			        "Allow", "Deny", allow -> confirmed.confirmed(true, allow));
		    });
	}

	private static void confirmSession(Context context, Intent intent) {
		int id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
		PackageInstaller.Session session = PackageInstaller.session(id);
		if (session == null || session.staged() == null) {
			Slog.w(TAG, "no committed session " + id);
			return;
		}
		confirm(context, session.staged(), (install, allowFakeSignature) -> {
			ATLInstaller.Result result = session.confirmed(install, allowFakeSignature, context.getPackageName());
			if (result != null && result.status == PackageInstaller.STATUS_SUCCESS)
				announce(context, result, false);
		});
	}

	private static void install(Context context, Intent intent, Activity resultTo, int requestCode) {
		ATLInstaller.Staged staged;
		try {
			File dir = ATLInstaller.newStagingDir();
			try (InputStream in = open(context, intent.getData())) {
				ATLInstaller.copy(in, new File(dir, "base.apk"));
			} catch (IOException | RuntimeException e) {
				ATLInstaller.deleteDir(dir);
				throw e;
			}
			staged = ATLInstaller.stageDir(dir, new String[] {"base.apk"});
		} catch (IOException | RuntimeException e) {
			Slog.w(TAG, "can't install " + intent.getData(), e);
			result(resultTo, requestCode, RESULT_FIRST_USER, PackageManager.INSTALL_FAILED_INTERNAL_ERROR);
			return;
		}
		confirm(context, staged, (install, allowFakeSignature) -> {
			if (!install) {
				ATLInstaller.abandon(staged);
				result(resultTo, requestCode, Activity.RESULT_CANCELED, null);
				return;
			}
			ATLInstaller.Result result = ATLInstaller.commit(staged, true, allowFakeSignature, context.getPackageName());
			if (result.status == PackageInstaller.STATUS_SUCCESS) {
				result(resultTo, requestCode, Activity.RESULT_OK, result.legacyStatus);
				announce(context, result, false);
			} else {
				result(resultTo, requestCode, RESULT_FIRST_USER, result.legacyStatus);
			}
		});
	}

	private static void uninstall(Context context, Intent intent, Activity resultTo, int requestCode) {
		String name = intent.getData().getSchemeSpecificPart();
		ask(context, "Uninstall " + name + "?", "Its data is removed too.", "Uninstall", "Cancel", yes -> {
			if (!yes) {
				result(resultTo, requestCode, Activity.RESULT_CANCELED, null);
				return;
			}
			ATLInstaller.Result result = ATLInstaller.uninstall(name);
			boolean ok = result.status == PackageInstaller.STATUS_SUCCESS;
			result(resultTo, requestCode, ok ? Activity.RESULT_OK : RESULT_FIRST_USER, null);
			if (ok)
				announce(context, result, true);
		});
	}

	private static InputStream open(Context context, Uri uri) throws IOException {
		if (uri == null)
			throw new IOException("no APK in the intent");
		if (uri.getScheme() == null || "file".equals(uri.getScheme()))
			return new FileInputStream(uri.getPath());
		return context.getContentResolver().openInputStream(uri);
	}

	private static void result(Activity resultTo, int requestCode, int resultCode, Integer installResult) {
		if (resultTo == null)
			return;
		Intent data = new Intent();
		if (installResult != null)
			data.putExtra(Intent.EXTRA_INSTALL_RESULT, installResult);
		resultTo.dispatchActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Android's broadcasts for an install, an update or a removal, to this process's receivers. A
	 * package that replaced or removed itself ends, as Android kills its process.
	 */
	private static void announce(Context context, ATLInstaller.Result result, boolean removed) {
		Uri data = Uri.fromParts("package", result.packageName, null);
		Context app = context.getApplicationContext();
		if (removed || result.replaced)
			app.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_REMOVED, data).putExtra(Intent.EXTRA_UID, result.uid)
			                      .putExtra(Intent.EXTRA_REPLACING, result.replaced).putExtra(Intent.EXTRA_DATA_REMOVED, removed));
		if (!removed) {
			app.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_ADDED, data).putExtra(Intent.EXTRA_UID, result.uid)
			                      .putExtra(Intent.EXTRA_REPLACING, result.replaced));
			if (result.replaced)
				app.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_REPLACED, data).putExtra(Intent.EXTRA_UID, result.uid)
				                      .putExtra(Intent.EXTRA_REPLACING, true));
		}
		if (context.getPackageName().equals(result.packageName) && (removed || result.replaced)) {
			Slog.i(TAG, result.packageName + " " + (removed ? "removed" : "replaced") + " itself: ending");
			new Handler(context.getMainLooper()).post(() -> System.exit(0));
		}
	}
}
