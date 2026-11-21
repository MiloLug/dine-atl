package dev.dine.nav;

import android.view.MotionEvent;

/* Logs each touch event the activity's window dispatches to it as "nav: <name> touch <action>", a
 * CANCEL with its first pointer's position and its pointer count: "touch ACTION_CANCEL <x>,<y> <n>". */
public abstract class TouchLoggingActivity extends LoggingActivity {
	TouchLoggingActivity(String name) {
		super(name);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		boolean cancel = event.getAction() == MotionEvent.ACTION_CANCEL;
		log("touch " + MotionEvent.actionToString(event.getAction()) + (cancel ? " " + (int)event.getX() + "," + (int)event.getY() + " " + event.getPointerCount() : ""));
		return super.dispatchTouchEvent(event);
	}
}
