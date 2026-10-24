/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/view/ViewRootImpl.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.view;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.inputmethod.InputMethodManager;

/* What AOSP's ViewRootImpl does with input, for ATL's hosts: touch mode, and the entries the ui
 * backends call (atl_upcall_touch, atl_upcall_key) with the root view of the window that got the
 * event. */
public final class ATLInput {
	// Android starts touch devices in touch mode (config_defaultInTouchMode)
	private static boolean touchMode = true;
	/* ESCAPE went down unhandled: Back stands in for it until its up. ATL's, since a keyboard on iOS
	 * has no Back key; Android 10's keymap gives plain ESCAPE no fallback, and Dialog takes it. */
	private static boolean escapeFallsBack;
	// Android's IME takes Back while it shows: the keyboard hides and the window gets neither half
	private static boolean keyboardTookBack;

	private ATLInput() {}

	static boolean isInTouchMode() {
		return touchMode;
	}

	// Instrumentation.setInTouchMode: only the mode; focus follows at the next input
	public static void setInTouchMode(boolean inTouchMode) {
		touchMode = inTouchMode;
	}

	static boolean touch(View root, MotionEvent event) {
		Window window = root.decor_window;
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_SCROLL)
			ensureTouchMode(event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN), root);
		Window.Callback callback = window != null ? window.getCallback() : null;
		return callback != null ? callback.dispatchTouchEvent(event) : root.dispatchTouchEvent(event);
	}

	// the app's window gives keys to its top drawn dialog, as Android gives that window the focus
	static boolean key(View root, KeyEvent event) {
		Window window = root.decor_window;
		if (window != null && window.getCallback() instanceof Activity) {
			Dialog dialog = Dialog.atlTopDrawn();
			if (dialog != null)
				window = dialog.getWindow();
		}
		View decor = window != null ? window.atlDecor() : root;

		if (checkForLeavingTouchModeAndConsume(event, decor))
			return true;
		boolean handled = deliver(window, decor, event);
		if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
			if (event.getAction() == KeyEvent.ACTION_DOWN && !handled && event.getRepeatCount() == 0) {
				escapeFallsBack = true;
				return deliver(window, decor, fallback(event, KeyEvent.KEYCODE_BACK));
			}
			if (escapeFallsBack) {
				if (event.getAction() == KeyEvent.ACTION_UP)
					escapeFallsBack = false;
				return deliver(window, decor, fallback(event, KeyEvent.KEYCODE_BACK));
			}
		}
		if (!handled && event.getAction() == KeyEvent.ACTION_DOWN)
			handled = performFocusNavigation(event, decor);
		return handled;
	}

	// TextView.onEditorAction's Enter: ViewRootImpl.dispatchKeyFromIme
	public static boolean dispatchKeyFromIme(View view, KeyEvent event) {
		return key(view.getRootView(), event);
	}

	private static boolean deliver(Window window, View decor, KeyEvent event) {
		if (keyboardTakesBack(event))
			return true;
		Window.Callback callback = window != null ? window.getCallback() : null;
		return callback != null ? callback.dispatchKeyEvent(event) : decor.dispatchKeyEvent(event);
	}

	private static boolean keyboardTakesBack(KeyEvent event) {
		if (event.getKeyCode() != KeyEvent.KEYCODE_BACK)
			return false;
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			if (event.getRepeatCount() == 0 && InputMethodManager.atlHideKeyboard())
				keyboardTookBack = true;
			return keyboardTookBack;
		}
		boolean took = keyboardTookBack;
		keyboardTookBack = false;
		return took;
	}

	private static KeyEvent fallback(KeyEvent event, int keyCode) {
		return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), keyCode, event.getRepeatCount(), event.getMetaState(),
		                    event.getDeviceId(), event.getScanCode(), event.getFlags() | KeyEvent.FLAG_FALLBACK, event.getSource());
	}

	// true when the mode changed and focus with it
	static boolean ensureTouchMode(boolean inTouchMode, View root) {
		if (touchMode == inTouchMode)
			return false;
		touchMode = inTouchMode;
		Window window = root.decor_window;
		if (window != null && window.view_tree_observer != null)
			window.view_tree_observer.dispatchOnTouchModeChanged(inTouchMode);
		return inTouchMode ? enterTouchMode(root) : leaveTouchMode(root);
	}

	private static boolean enterTouchMode(View root) {
		if (root.hasFocus()) {
			// note: not relying on mFocusedView here because this could
			// be when the window is first being added, and mFocused isn't
			// set yet.
			final View focused = root.findFocus();
			if (focused != null && !focused.isFocusableInTouchMode()) {
				final ViewGroup ancestorToTakeFocus = findAncestorToTakeFocusInTouchMode(focused);
				if (ancestorToTakeFocus != null) {
					// there is an ancestor that wants focus after its
					// descendants that is focusable in touch mode.. give it
					// focus
					return ancestorToTakeFocus.requestFocus();
				} else {
					// There's nothing to focus. Clear and propagate through the
					// hierarchy, but don't attempt to place new focus.
					focused.clearFocusInternal(null, true, false);
					return true;
				}
			}
		}
		return false;
	}

	private static ViewGroup findAncestorToTakeFocusInTouchMode(View focused) {
		ViewParent parent = focused.getParent();
		while (parent instanceof ViewGroup) {
			final ViewGroup vgParent = (ViewGroup)parent;
			if (vgParent.getDescendantFocusability() == ViewGroup.FOCUS_AFTER_DESCENDANTS
			    && vgParent.isFocusableInTouchMode()) {
				return vgParent;
			}
			parent = vgParent.getParent();
		}
		return null;
	}

	private static boolean leaveTouchMode(View root) {
		if (root.hasFocus()) {
			View focusedView = root.findFocus();
			if (!(focusedView instanceof ViewGroup)) {
				// some view has focus, let it keep it
				return false;
			} else if (((ViewGroup)focusedView).getDescendantFocusability() != ViewGroup.FOCUS_AFTER_DESCENDANTS) {
				// some view group has focus, and doesn't prefer its children
				// over itself for focus, so let them keep it.
				return false;
			}
		}

		// find the best view to give focus to in this brave new non-touch-mode
		// world
		return root.restoreDefaultFocus();
	}

	private static boolean checkForLeavingTouchModeAndConsume(KeyEvent event, View root) {
		// Only relevant in touch mode.
		if (!touchMode) {
			return false;
		}

		// Only consider leaving touch mode on DOWN or MULTIPLE actions, never on UP.
		final int action = event.getAction();
		if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_MULTIPLE) {
			return false;
		}

		// Don't leave touch mode if the IME told us not to.
		if ((event.getFlags() & KeyEvent.FLAG_KEEP_TOUCH_MODE) != 0) {
			return false;
		}

		// If the key can be used for keyboard navigation then leave touch mode
		// and select a focused view if needed (in ensureTouchMode).
		// When a new focused view is selected, we consume the navigation key because
		// navigation doesn't make much sense unless a view already has focus so
		// the key's purpose is to set focus.
		if (isNavigationKey(event)) {
			return ensureTouchMode(false, root);
		}

		// If the key can be used for typing then leave touch mode
		// and select a focused view if needed (in ensureTouchMode).
		// Always allow the view to process the typing key.
		if (isTypingKey(event)) {
			ensureTouchMode(false, root);
			return false;
		}

		return false;
	}

	private static boolean isNavigationKey(KeyEvent keyEvent) {
		switch (keyEvent.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_PAGE_UP:
			case KeyEvent.KEYCODE_PAGE_DOWN:
			case KeyEvent.KEYCODE_MOVE_HOME:
			case KeyEvent.KEYCODE_MOVE_END:
			case KeyEvent.KEYCODE_TAB:
			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_ENTER:
				return true;
		}
		return false;
	}

	private static boolean isTypingKey(KeyEvent keyEvent) {
		return keyEvent.getUnicodeChar() > 0;
	}

	private static boolean performFocusNavigation(KeyEvent event, View root) {
		int direction = 0;
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (event.hasNoModifiers()) {
					direction = View.FOCUS_LEFT;
				}
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (event.hasNoModifiers()) {
					direction = View.FOCUS_RIGHT;
				}
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				if (event.hasNoModifiers()) {
					direction = View.FOCUS_UP;
				}
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (event.hasNoModifiers()) {
					direction = View.FOCUS_DOWN;
				}
				break;
			case KeyEvent.KEYCODE_TAB:
				if (event.hasNoModifiers()) {
					direction = View.FOCUS_FORWARD;
				} else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
					direction = View.FOCUS_BACKWARD;
				}
				break;
		}
		if (direction != 0) {
			View focused = root.findFocus();
			if (focused != null) {
				View v = focused.focusSearch(direction);
				if (v != null && v != focused) {
					// do the math the get the interesting rect
					// of previous focused into the coord system of
					// newly focused view
					Rect rect = new Rect();
					focused.getFocusedRect(rect);
					if (root instanceof ViewGroup) {
						((ViewGroup)root).offsetDescendantRectToMyCoords(focused, rect);
						((ViewGroup)root).offsetRectIntoDescendantCoords(v, rect);
					}
					if (v.requestFocus(direction, rect)) {
						return true;
					}
				}
			} else {
				if (root.restoreDefaultFocus()) {
					return true;
				}
			}
		}
		return false;
	}
}
