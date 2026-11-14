package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import dalvik.system.VMRuntime;

public class CreateActivity extends Activity {
	// the positive control of run-crash.sh's "no CheckJNI abort"
	static void logCheckJni() {
		Log.i("crash", "crash checkjni " + VMRuntime.getRuntime().isCheckJniEnabled());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logCheckJni();
		throw new IllegalStateException("crash in onCreate");
	}
}
