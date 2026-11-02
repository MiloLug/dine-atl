package dev.dine.pkg.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class Svc extends Service {
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
