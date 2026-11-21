package android.content.pm;

import android.atl.ATLInstaller;
import android.atl.ATLLoadedApp;
import android.atl.ATLRegistry;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Slog;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sessions of this VM, each its creating app's: only that app lists, opens or abandons it. A commit
 * asks the user through STATUS_PENDING_USER_ACTION, as Android does for an installer without
 * INSTALL_PACKAGES. ATLInstaller writes the package.
 */
public class PackageInstaller {
	private static final String TAG = "PackageInstaller";

	public static final String EXTRA_STATUS = "android.content.pm.extra.STATUS";
	public static final String EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE";
	public static final String EXTRA_PACKAGE_NAME = "android.content.pm.extra.PACKAGE_NAME";
	public static final String EXTRA_SESSION_ID = "android.content.pm.extra.SESSION_ID";
	/** @hide the action of STATUS_PENDING_USER_ACTION's intent */
	public static final String ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL";

	public static final int STATUS_PENDING_USER_ACTION = -1;
	public static final int STATUS_SUCCESS = 0;
	public static final int STATUS_FAILURE = 1;
	public static final int STATUS_FAILURE_BLOCKED = 2;
	public static final int STATUS_FAILURE_ABORTED = 3;
	public static final int STATUS_FAILURE_INVALID = 4;
	public static final int STATUS_FAILURE_CONFLICT = 5;
	public static final int STATUS_FAILURE_STORAGE = 6;
	public static final int STATUS_FAILURE_INCOMPATIBLE = 7;

	private static final HashMap<Integer, Session> sessions = new HashMap<>();
	private static int nextSessionId = 1;

	public int createSession(SessionParams params) throws IOException {
		synchronized (sessions) {
			int id = nextSessionId++;
			sessions.put(id, new Session(id, params, ATLInstaller.newStagingDir(), ATLLoadedApp.ofCurrentThreadOrThrow()));
			return id;
		}
	}

	// Android's answer for another app's session and for none
	private static Session own(int sessionId) {
		Session session = session(sessionId);
		if (session == null || session.owner != ATLLoadedApp.ofCurrentThread())
			throw new SecurityException("Caller has no access to session " + sessionId);
		return session;
	}

	public Session openSession(int sessionId) throws IOException {
		return own(sessionId);
	}

	public void abandonSession(int sessionId) {
		own(sessionId).abandon();
	}

	public SessionInfo getSessionInfo(int sessionId) {
		Session session = session(sessionId);
		return session != null ? new SessionInfo(session) : null;
	}

	public List<SessionInfo> getMySessions() {
		ATLLoadedApp app = ATLLoadedApp.ofCurrentThread();
		List<SessionInfo> infos = new ArrayList<>();
		synchronized (sessions) {
			for (Session session : sessions.values())
				if (session.owner == app)
					infos.add(new SessionInfo(session));
		}
		return infos;
	}

	public List<SessionInfo> getAllSessions() {
		List<SessionInfo> infos = new ArrayList<>();
		synchronized (sessions) {
			for (Session session : sessions.values())
				infos.add(new SessionInfo(session));
		}
		return infos;
	}

	public void registerSessionCallback(SessionCallback callback) {}

	public void unregisterSessionCallback(SessionCallback callback) {}

	/** @hide */
	public static Session session(int sessionId) {
		synchronized (sessions) {
			return sessions.get(sessionId);
		}
	}

	public abstract static class SessionCallback {
		public abstract void onActiveChanged(int sessionId, boolean active);

		public abstract void onBadgingChanged(int sessionId);

		public abstract void onCreated(int sessionId);

		public abstract void onFinished(int sessionId, boolean success);

		public abstract void onProgressChanged(int sessionId, float progress);
	}

	public static class SessionParams {
		public static final int MODE_FULL_INSTALL = 1;
		public static final int MODE_INHERIT_EXISTING = 2;

		final int mode;
		String appPackageName;

		public SessionParams(int mode) {
			this.mode = mode;
		}

		public void setAppPackageName(String appPackageName) {
			this.appPackageName = appPackageName;
		}

		public void setSize(long sizeBytes) {}

		public void setAppLabel(CharSequence appLabel) {}

		public void setAppIcon(Bitmap appIcon) {}

		public void setInstallLocation(int installLocation) {}

		public void setOriginatingUri(Uri originatingUri) {}

		public void setReferrerUri(Uri referrerUri) {}

		public void setInstallReason(int installReason) {}

		public void setRequireUserAction(int requireUserAction) {}

		public void setPackageSource(int packageSource) {}

		public void setRequestUpdateOwnership(boolean requestUpdateOwnership) {}
	}

	public static class SessionInfo {
		private final int sessionId;
		private final String appPackageName;
		private final boolean active;

		SessionInfo(Session session) {
			sessionId = session.id;
			appPackageName = session.params.appPackageName;
			active = session.staged != null;
		}

		public int getSessionId() {
			return sessionId;
		}

		public String getAppPackageName() {
			return appPackageName;
		}

		public boolean isActive() {
			return active;
		}

		public boolean isSealed() {
			return active;
		}

		public float getProgress() {
			return active ? 1 : 0;
		}
	}

	public static class Session implements Closeable {
		final int id;
		final SessionParams params;
		final ATLLoadedApp owner;
		private final File dir;
		// the installer's names, in the order first written, and their files in dir: a name never reaches the disk
		private final Map<String, File> files = new LinkedHashMap<>();
		ATLInstaller.Staged staged;
		private IntentSender statusReceiver;

		Session(int id, SessionParams params, File dir, ATLLoadedApp owner) {
			this.id = id;
			this.params = params;
			this.dir = dir;
			this.owner = owner;
		}

		public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
			if (staged != null)
				throw new SecurityException("session " + id + " is committed");
			if (name.isEmpty() || name.contains("/") || name.startsWith("."))
				throw new IllegalArgumentException("invalid name " + name);
			File file;
			synchronized (files) {
				file = files.get(name);
				if (file == null) {
					// a dot: no stored name (ATLInstaller.storedName) starts with one
					file = new File(dir, ".write" + files.size());
					files.put(name, file);
				}
			}
			if (offsetBytes == 0)
				return new FileOutputStream(file);
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.seek(offsetBytes);
			return new FileOutputStream(raf.getFD()) {
				@Override
				public void close() throws IOException {
					super.close();
					raf.close();
				}
			};
		}

		public void fsync(OutputStream out) throws IOException {
			((FileOutputStream)out).getFD().sync();
		}

		public String[] getNames() {
			synchronized (files) {
				return files.keySet().toArray(new String[0]);
			}
		}

		public void setStagingProgress(float progress) {}

		/** @hide the committed APKs, null before commit */
		public ATLInstaller.Staged staged() {
			return staged;
		}

		/** Sends STATUS_PENDING_USER_ACTION; the confirmation then installs and sends the final status. */
		public void commit(IntentSender statusReceiver) {
			this.statusReceiver = statusReceiver;
			if (staged != null) {
				askUser();
				return;
			}
			String[] names = getNames();
			// the base APK first: "base.apk" when the installer named it so, else the first file written
			for (int i = 1; i < names.length; i++) {
				if (names[i].equals("base.apk")) {
					names[i] = names[0];
					names[0] = "base.apk";
				}
			}
			try {
				if (names.length == 0)
					throw new IOException("no APK written");
				// stored and synced whatever the installer synced, before the row can name them
				String[] stored = new String[names.length];
				for (int i = 0; i < names.length; i++) {
					stored[i] = ATLInstaller.storedName(i);
					File file = new File(dir, stored[i]);
					if (!files.get(names[i]).renameTo(file))
						throw new IOException("can't store " + names[i]);
					ATLRegistry.fsync(file);
				}
				ATLRegistry.fsync(dir);
				staged = ATLInstaller.stageDir(dir, stored);
			} catch (IOException | RuntimeException e) {
				ATLInstaller.deleteDir(dir);
				finish(STATUS_FAILURE_INVALID, null, e.getMessage());
				return;
			}
			askUser();
		}

		private void askUser() {
			Intent confirm = new Intent(ACTION_CONFIRM_INSTALL).putExtra(EXTRA_SESSION_ID, id);
			send(new Intent().putExtra(EXTRA_STATUS, STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm)
			         .putExtra(EXTRA_PACKAGE_NAME, staged.pkg.packageName));
		}

		/** @hide the confirmation's answer: installs, with the session's app as installer, or abandons on a refusal */
		public ATLInstaller.Result confirmed(boolean accepted, boolean allowFakeSignature) {
			ATLInstaller.Result result;
			if (accepted) {
				result = ATLInstaller.commit(staged, true, allowFakeSignature, owner.pkg.packageName);
			} else {
				ATLInstaller.abandon(staged);
				result = null;
			}
			if (result == null)
				finish(STATUS_FAILURE_ABORTED, staged.pkg.packageName, "the user cancelled the install");
			else
				finish(result.status, result.packageName, result.message);
			return result;
		}

		private void finish(int status, String packageName, String message) {
			synchronized (sessions) {
				sessions.remove(id);
			}
			send(new Intent().putExtra(EXTRA_STATUS, status).putExtra(EXTRA_PACKAGE_NAME, packageName)
			         .putExtra(EXTRA_STATUS_MESSAGE, message));
		}

		private void send(Intent status) {
			status.putExtra(EXTRA_SESSION_ID, id);
			try {
				statusReceiver.sendIntent(null, 0, status, null, null);
			} catch (IntentSender.SendIntentException e) {
				Slog.w(TAG, "session " + id + ": status not delivered", e);
			}
		}

		public void abandon() {
			synchronized (sessions) {
				sessions.remove(id);
			}
			if (staged != null)
				ATLInstaller.abandon(staged);
			else
				ATLInstaller.deleteDir(dir);
		}

		@Override
		public void close() {}
	}
}
