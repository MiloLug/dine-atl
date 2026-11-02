package android.content;

import android.app.PendingIntent;
import android.os.Handler;

/** A PendingIntent's sending side; in-process until MA7 carries it between apps. */
public class IntentSender {
	private final PendingIntent target;

	/** @hide */
	public IntentSender(PendingIntent target) {
		this.target = target;
	}

	public void sendIntent(Context context, int code, Intent intent, OnFinished onFinished, Handler handler) throws SendIntentException {
		sendIntent(context, code, intent, onFinished, handler, null);
	}

	public void sendIntent(Context context, int code, Intent intent, OnFinished onFinished, Handler handler, String requiredPermission) throws SendIntentException {
		target.send(context, code, intent);
		if (onFinished != null)
			onFinished.onSendFinished(this, intent, code, null, null);
	}

	public interface OnFinished {
		void onSendFinished(IntentSender sender, Intent intent, int resultCode, String resultData, android.os.Bundle resultExtras);
	}

	public static class SendIntentException extends Exception {
		public SendIntentException() {}

		public SendIntentException(String message) {
			super(message);
		}
	}
}
