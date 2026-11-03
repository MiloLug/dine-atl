package dev.dine.framework.basics;

import android.app.Activity;
import android.os.Looper;

public class PermissionActivity extends Activity {
	int code = -1;
	String[] permissions;
	int[] results;
	boolean on_main;

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		code = requestCode;
		this.permissions = permissions;
		results = grantResults;
		on_main = Looper.myLooper() == Looper.getMainLooper();
	}
}
