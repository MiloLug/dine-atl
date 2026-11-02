package android.app;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.GestureDetector;

public class PendingIntent implements Parcelable {

	private int requestCode;
	Intent intent;
	int type; // 0: activity, 1: service, 2: broadcast
	private final Context context; // the creator's; it sends

	private PendingIntent(Context context, int requestCode, Intent intent, int type) {
		this.context = context;
		this.requestCode = requestCode;
		this.intent = intent;
		this.type = type;
	}
	public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
		return new PendingIntent(context, requestCode, intent, 2);
	}

	public IntentSender getIntentSender() {
		return new IntentSender(this);
	}

	/** fillIn's extras are added to a copy of the intent; the creator's context sends it. */
	public void send(Context context, int code, Intent fillIn) {
		Intent sent = new Intent(intent);
		if (fillIn != null && fillIn.getExtras() != null)
			sent.getExtras().putAll(fillIn.getExtras());
		Context sender = this.context != null ? this.context : ATLLoadedApp.getPrimaryApplication().getApplication();
		if (type == 0) { // type Activity
			sender.startActivity(sent);
		} else if (type == 1) { // type Service
			sender.startService(sent);
		} else if (type == 2) { // type Broadcast
			sender.sendBroadcast(sent);
		}
	}

	public void send() {
		send(null, 0, null);
	}

	public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
		return new PendingIntent(context, requestCode, intent, 0);
	}

	public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
		return new PendingIntent(context, requestCode, intent, 1);
	}

	public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags, Bundle options) {
		return new PendingIntent(context, requestCode, intents[0], 0);
	}

	public String toString() {
		return "PendingIntent [requestCode=" + requestCode + ", intent=" + intent + ", type="
		     + new String[] {"activity", "service", "broadcast"}[type] + "]";
	}

	public void cancel() {}

	public class CanceledException extends Exception {
	}

	public String getCreatorPackage() {
		return ATLLoadedApp.getPrimaryApplication().pkg.packageName;
	}

	public int getCreatorUid() {
		return ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.uid;
	}

	public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) {
		return getService(context, requestCode, intent, flags);
	}
}
