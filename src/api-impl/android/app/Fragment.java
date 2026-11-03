package android.app;

import android.os.Bundle;

public class Fragment {

	Activity activity;

	public void onCreate(Bundle savedInstanceState) {
	}

	public void onStart() {
	}

	public void onResume() {
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onDestroy() {
	}

	public Activity getActivity() {
		return activity;
	}

	public final void requestPermissions(String[] permissions, int requestCode) {
		if (activity == null)
			throw new IllegalStateException("Fragment " + this + " not attached to Activity");
		activity.request_permissions(permissions, requestCode, this);
	}

	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

	public boolean shouldShowRequestPermissionRationale(String permission) {
		return false;
	}
}
