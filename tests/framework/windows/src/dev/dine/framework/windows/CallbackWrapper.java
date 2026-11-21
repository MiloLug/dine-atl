package dev.dine.framework.windows;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

// what AppCompat's delegate does: a Window.Callback in front of the activity, which the window's
// setCallback takes in its place
class CallbackWrapper implements Window.Callback {
	private final Window.Callback inner;

	CallbackWrapper(Window.Callback inner) {
		this.inner = inner;
	}

	public void onContentChanged() {
		inner.onContentChanged();
	}

	public boolean onCreatePanelMenu(int featureId, Menu menu) {
		return inner.onCreatePanelMenu(featureId, menu);
	}

	public View onCreatePanelView(int featureId) {
		return inner.onCreatePanelView(featureId);
	}

	public boolean onPreparePanel(int featureId, View view, Menu menu) {
		return inner.onPreparePanel(featureId, view, menu);
	}

	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		return inner.onMenuItemSelected(featureId, item);
	}

	public void onPanelClosed(int featureId, Menu menu) {
		inner.onPanelClosed(featureId, menu);
	}

	public boolean onMenuOpened(int featureId, Menu menu) {
		return inner.onMenuOpened(featureId, menu);
	}

	public boolean dispatchKeyEvent(KeyEvent event) {
		return inner.dispatchKeyEvent(event);
	}

	public boolean dispatchTouchEvent(MotionEvent event) {
		return inner.dispatchTouchEvent(event);
	}
}
