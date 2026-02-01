package android.service.quicksettings;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.os.IBinder;

public class TileService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Unimplemented method 'onBind'");
	}

	public static void requestListeningState(Context ctx, ComponentName name) {
		if (ctx == null) {
			throw new NullPointerException("Context is null");
		}

		// TODO: Missing implementation.
	}

}
