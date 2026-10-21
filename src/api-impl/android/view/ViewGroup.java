/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/view/ViewGroup.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.view;

import android.R;
import android.animation.LayoutTransition;
import android.annotation.UnsupportedAppUsage;
import android.atl.GskCanvas;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;

public class ViewGroup extends View implements ViewParent, ViewManager {
	/* clang-format off */
	static final int FLAG_CLIP_CHILDREN        = (1 << 0);
	static final int FLAG_INVALIDATE_REQUIRED  = (1 << 2);
	static final int FLAG_ANIMATION_DONE       = (1 << 4);
	static final int FLAG_OPTIMIZE_INVALIDATE  = (1 << 7);
	static final int FLAG_CLEAR_TRANSFORMATION = (1 << 8);
	@UnsupportedAppUsage
	protected static final int FLAG_USE_CHILD_DRAWING_ORDER = (1 << 10);
	@UnsupportedAppUsage
	protected static final int FLAG_SUPPORT_STATIC_TRANSFORMATIONS = (1 << 11);
	private static final int FLAG_MASK_FOCUSABILITY  = (3 << 17);
	public static final int FOCUS_BEFORE_DESCENDANTS = (1 << 17);
	public static final int FOCUS_AFTER_DESCENDANTS  = (2 << 17);
	public static final int FOCUS_BLOCK_DESCENDANTS  = (3 << 17);
	@UnsupportedAppUsage
	protected static final int FLAG_DISALLOW_INTERCEPT = (1 << 19);
	static final int FLAG_IS_TRANSITION_GROUP          = (1 << 24);
	static final int FLAG_IS_TRANSITION_GROUP_SET      = (1 << 25);
	static final int FLAG_TOUCHSCREEN_BLOCKS_FOCUS     = (1 << 26);
	/* clang-format on */

	public ArrayList<View> children;
	private ArrayList<View> detachedChildren;
	View focusedChild; // AOSP's mFocused: the child that is or holds the focus
	private int descendantFocusability = FOCUS_BEFORE_DESCENDANTS;
	private boolean touchscreenBlocksFocus;
	private boolean disallowIntercept;
	private boolean splitMotionEvents = true;
	private TouchTarget firstTouchTarget;
	private boolean pendingHideDetachedChildren = false;
	private OnHierarchyChangeListener onHierarchyChangeListener;
	private LayoutTransition transition;
	private ViewGroupOverlay viewGroupOverlay;

	public ViewGroup(Context context) {
		this(context, null);
	}

	public ViewGroup(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr);

		children = new ArrayList<View>();
		detachedChildren = new ArrayList<View>();

		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ViewGroup, defStyleAttr, defStyleRes);
		descendantFocusability = DESCENDANT_FOCUSABILITY_FLAGS[a.getInt(com.android.internal.R.styleable.ViewGroup_descendantFocusability, 0)];
		splitMotionEvents = a.getBoolean(com.android.internal.R.styleable.ViewGroup_splitMotionEvents, true);
		touchscreenBlocksFocus = a.getBoolean(com.android.internal.R.styleable.ViewGroup_touchscreenBlocksFocus, false);
		a.recycle();
	}

	private static final int[] DESCENDANT_FOCUSABILITY_FLAGS = {FOCUS_BEFORE_DESCENDANTS, FOCUS_AFTER_DESCENDANTS, FOCUS_BLOCK_DESCENDANTS};

	public void addView(View child) {
		addView(child, -1);
	}

	public void addView(View child, int index) {
		LayoutParams params = child.getLayoutParams();
		if (params == null) {
			params = generateDefaultLayoutParams();
		}
		addView(child, index, params);
	}

	public void addView(View child, LayoutParams params) {
		addView(child, -1, params);
	}

	public void addView(View child, int width, int height) {
		final LayoutParams params = generateDefaultLayoutParams();
		params.width = width;
		params.height = height;
		addView(child, params);
	}

	public void addView(View child, int index, LayoutParams params) {
		addViewInternal(child, index, params, true);
		requestLayout();
	}

	public void onViewAdded(View view) {}
	public void onViewRemoved(View view) {}

	protected void addViewInternal(View child, int index, LayoutParams params, boolean callOnViewAdded) {
		if (child.parent == this)
			return;
		if (!checkLayoutParams(params))
			params = generateLayoutParams(params);

		child.parent = this;
		child.setLayoutParams(params);
		if (index < 0)
			index = children.size();
		children.add(index, child);
		int sortedIndex = index;
		for (View v : children) {
			if (v.getZ() != 0.0f) {
				// sort children by z-order
				ArrayList<View> sortedChildren = new ArrayList<View>(children);
				sortedChildren.sort(new Comparator<View>() {
					@Override
					public int compare(View o1, View o2) {
						return (int)(o1.getZ() - o2.getZ());
					}
				});
				sortedIndex = sortedChildren.indexOf(child);
				break;
			}
		}
		native_addView(widget, child.widget, sortedIndex, params);
		if (attachedToWindow)
			child.dispatchAttachedToWindow();
		if (callOnViewAdded) {
			onViewAdded(child);
			if (onHierarchyChangeListener != null)
				onHierarchyChangeListener.onChildViewAdded(this, child);
		}
	}

	protected boolean addViewInLayout(View child, int index, LayoutParams params) {
		addViewInternal(child, index, params, true);
		return true;
	}

	protected boolean addViewInLayout(View child, int index, LayoutParams params, boolean preventRequestLayout) {
		addViewInternal(child, index, params, true);
		if (!preventRequestLayout)
			requestLayout();
		return true;
	}

	// This internal method is used to share code between removeView and removeViewInLayout.
	// Reusing removeView in removeViewInLayout is not possible, because e.g.
	// ViewPager overrides removeView to call removeViewInLayout
	protected void removeViewInternal(View child) {
		if (child == null || child.parent != this)
			return;
		boolean clearChildFocus = loseChild(child);
		child.dispatchDetachedFromWindow();
		child.parent = null;
		children.remove(child);
		native_removeView(widget, child.widget);
		onViewRemoved(child);
		if (onHierarchyChangeListener != null) {
			onHierarchyChangeListener.onChildViewRemoved(this, child);
		}
		if (clearChildFocus)
			lostFocusedChild(child);
	}

	// a child leaves: its touch target goes, and its focus; true when it held the focus
	private boolean loseChild(View child) {
		cancelTouchTarget(child);
		if (child != focusedChild)
			return false;
		child.unFocus(null);
		return true;
	}

	private void lostFocusedChild(View child) {
		clearChildFocus(child);
		rootViewRequestFocus();
	}

	public void removeView(View child) {
		removeViewInternal(child);
		requestLayout();
	}

	public void removeViewInLayout(View child) {
		removeViewInternal(child);
	}

	public void removeViewAt(int index) {
		removeView(children.get(index));
	}

	public void removeAllViews() {
		View focused = null;
		for (Iterator<View> it = children.iterator(); it.hasNext();) {
			View child = it.next();
			if (loseChild(child))
				focused = child;
			child.dispatchDetachedFromWindow();
			child.parent = null;
			it.remove();
			native_removeView(widget, child.widget);
			onViewRemoved(child);
			if (onHierarchyChangeListener != null) {
				onHierarchyChangeListener.onChildViewRemoved(this, child);
			}
		}
		if (focused != null)
			lostFocusedChild(focused);
		requestLayout();
	}

	public void detachViewFromParent(int index) {
		detachViewFromParent(getChildAt(index));
	}

	public void attachViewToParent(View view, int index, LayoutParams params) {
		if (!detachedChildren.remove(view)) {
			addViewInternal(view, index, params, false);
			return;
		}
		if (!checkLayoutParams(params))
			params = generateLayoutParams(params);

		view.parent = this;
		view.setLayoutParams(params);
		if (index < 0)
			index = children.size();
		children.add(index, view);
		view.native_setVisibility(view.widget, view.getVisibility(), view.getAlpha(), view.isEnabled());
	}

	protected void removeDetachedView(View child, boolean animate) {
		if (!detachedChildren.remove(child))
			return;
		if (child == focusedChild) {
			child.unFocus(null);
			clearChildFocus(child);
		}
		child.dispatchDetachedFromWindow();
		child.parent = null;
		native_removeView(widget, child.widget);
		if (onHierarchyChangeListener != null) {
			onHierarchyChangeListener.onChildViewRemoved(this, child);
		}
	}

	// before API 30 (ATL's level): the children until one consumes all
	@Override
	public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
		insets = super.dispatchApplyWindowInsets(insets);
		if (!insets.isConsumed()) {
			for (View child : new ArrayList<View>(children)) {
				insets = child.dispatchApplyWindowInsets(insets);
				if (insets.isConsumed())
					break;
			}
		}
		return insets;
	}

	@Override
	public void dispatchAttachedToWindow() {
		if (attachedToWindow)
			return;
		super.dispatchAttachedToWindow();
		// onAttachedToWindow may add or remove children
		for (View child : new ArrayList<View>(children)) {
			if (child.parent == this)
				child.dispatchAttachedToWindow();
		}
	}

	@Override
	public void dispatchDetachedFromWindow() {
		if (!attachedToWindow)
			return;
		cancelAndClearTouchTargets(null);
		for (View child : new ArrayList<View>(children))
			child.dispatchDetachedFromWindow();
		super.dispatchDetachedFromWindow();
	}

	@Override
	protected void dispatchVisibilityChanged(View changedView, int visibility) {
		if (children == null) // happens if this gets called during super constructor
			return;

		for (View child : children) {
			child.dispatchVisibilityChanged(changedView, visibility);
		}
	}

	protected native void native_addView(long widget, long child, int index, LayoutParams params);
	protected native void native_removeView(long widget, long child);
	@Override
	protected native void native_drawChildren(long widget, long snapshot);
	protected native void native_drawChild(long widget, long child, long snapshot);
	@Override
	protected void native_drawContent(long widget, long snapshot) {}

	public View getChildAt(int index) {
		try {
			return children.get(index);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	public int indexOfChild(View child) {
		return children.indexOf(child);
	}

	public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
		view.setLayoutParams(params);
		view.requestLayout();
	}

	public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}
	protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return p;
	}

	public void bringChildToFront(View child) {
		// TODO: actually implement this (might make sense to implement it in the subclasses instead), when applicable
	}

	/**
	 * Returns the number of children in the group.
	 *
	 * @return a positive integer representing the number of children in
	 *         the group
	 */
	public int getChildCount() {
		return children.size();
	}

	public void setMotionEventSplittingEnabled(boolean enabled) {
		splitMotionEvents = enabled;
	}

	public boolean isMotionEventSplittingEnabled() {
		return splitMotionEvents;
	}

	public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
		this.onHierarchyChangeListener = listener;
	}

	protected boolean checkLayoutParams(LayoutParams params) {
		return true;
	}

	public LayoutTransition getLayoutTransition() {
		if (transition == null)
			transition = new LayoutTransition();
		return transition;
	}

	public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
		int specMode = MeasureSpec.getMode(spec);
		int specSize = MeasureSpec.getSize(spec);
		int size = Math.max(0, specSize - padding);
		int resultSize = 0;
		int resultMode = 0;
		switch (specMode) {
			// Parent has imposed an exact size on us
			case MeasureSpec.EXACTLY:
				if (childDimension >= 0) {
					resultSize = childDimension;
					resultMode = MeasureSpec.EXACTLY;
				} else if (childDimension == LayoutParams.MATCH_PARENT) {
					// Child wants to be our size. So be it.
					resultSize = size;
					resultMode = MeasureSpec.EXACTLY;
				} else if (childDimension == LayoutParams.WRAP_CONTENT) {
					// Child wants to determine its own size. It can't be
					// bigger than us.
					resultSize = size;
					resultMode = MeasureSpec.AT_MOST;
				}
				break;
			// Parent has imposed a maximum size on us
			case MeasureSpec.AT_MOST:
				if (childDimension >= 0) {
					// Child wants a specific size... so be it
					resultSize = childDimension;
					resultMode = MeasureSpec.EXACTLY;
				} else if (childDimension == LayoutParams.MATCH_PARENT) {
					// Child wants to be our size, but our size is not fixed.
					// Constrain child to not be bigger than us.
					resultSize = size;
					resultMode = MeasureSpec.AT_MOST;
				} else if (childDimension == LayoutParams.WRAP_CONTENT) {
					// Child wants to determine its own size. It can't be
					// bigger than us.
					resultSize = size;
					resultMode = MeasureSpec.AT_MOST;
				}
				break;
			// Parent asked to see how big we want to be
			case MeasureSpec.UNSPECIFIED:
				if (childDimension >= 0) {
					// Child wants a specific size... let them have it
					resultSize = childDimension;
					resultMode = MeasureSpec.EXACTLY;
				} else if (childDimension == LayoutParams.MATCH_PARENT) {
					// Child wants to be our size... find out how big it should
					// be
					resultSize = 0; // View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
					resultMode = MeasureSpec.UNSPECIFIED;
				} else if (childDimension == LayoutParams.WRAP_CONTENT) {
					// Child wants to determine its own size.... find out how
					// big it should be
					resultSize = 0; // View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
					resultMode = MeasureSpec.UNSPECIFIED;
				}
				break;
		}
		// noinspection ResourceType
		return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
	}

	protected void measureChildWithMargins(View child,
	                                       int parentWidthMeasureSpec, int widthUsed,
	                                       int parentHeightMeasureSpec, int heightUsed) {
		final MarginLayoutParams lp = (MarginLayoutParams)child.getLayoutParams();
		final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
		final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	protected void measureChild(View child, int parentWidthMeasureSpec,
	                            int parentHeightMeasureSpec) {
		final LayoutParams lp = child.getLayoutParams();
		final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight, lp.width);
		final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom, lp.height);
		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
		for (View child : children) {
			measureChild(child, widthMeasureSpec, heightMeasureSpec);
		}
	}

	public void setAddStatesFromChildren(boolean addsStates) {}

	public View getFocusedChild() { return focusedChild; }

	public int getDescendantFocusability() { return descendantFocusability; }

	public void setDescendantFocusability(int focusability) {
		switch (focusability) {
			case FOCUS_BEFORE_DESCENDANTS:
			case FOCUS_AFTER_DESCENDANTS:
			case FOCUS_BLOCK_DESCENDANTS:
				break;
			default:
				throw new IllegalArgumentException("must be one of FOCUS_BEFORE_DESCENDANTS, FOCUS_AFTER_DESCENDANTS, FOCUS_BLOCK_DESCENDANTS");
		}
		descendantFocusability = focusability;
	}

	public void startViewTransition(View view) {}
	public void endViewTransition(View view) {}

	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}

	public void focusableViewAvailable(View v) {}

	@Override
	public void setGravity(int gravity) {
		super.setGravity(gravity);
		// update children as necessary
		for (View child : children) {
			LayoutParams params = child.getLayoutParams();
			if (params.gravity == -1)
				child.setLayoutParams(params);
		}
	}

	protected void setChildrenDrawingOrderEnabled(boolean enabled) {}

	/* Copyright (C) 2006 The Android Open Source Project: requestDisallowInterceptTouchEvent to TouchTarget */
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		if (disallowIntercept == this.disallowIntercept) {
			// We're already in this state, assume our ancestors are too
			return;
		}
		this.disallowIntercept = disallowIntercept;
		// Pass it up to our parent
		if (parent != null) {
			parent.requestDisallowInterceptTouchEvent(disallowIntercept);
		}
	}

	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return false;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		// If the event targets the accessibility focused view and this is it, start
		// normal event dispatch. Maybe a descendant is what will handle the click.
		if (ev.isTargetAccessibilityFocus() && isAccessibilityFocused()) {
			ev.setTargetAccessibilityFocus(false);
		}

		boolean handled = false;
		final int action = ev.getAction();
		final int actionMasked = action & MotionEvent.ACTION_MASK;

		// Handle an initial down.
		if (actionMasked == MotionEvent.ACTION_DOWN) {
			// Throw away all previous state when starting a new touch gesture.
			// The framework may have dropped the up or cancel event for the previous gesture
			// due to an app switch, ANR, or some other state change.
			cancelAndClearTouchTargets(ev);
			resetTouchState();
		}

		// Check for interception.
		final boolean intercepted;
		if (actionMasked == MotionEvent.ACTION_DOWN || firstTouchTarget != null) {
			if (!disallowIntercept) {
				intercepted = onInterceptTouchEvent(ev);
				ev.setAction(action); // restore action in case it was changed
			} else {
				intercepted = false;
			}
		} else {
			// There are no touch targets and this action is not an initial down
			// so this view group continues to intercept touches.
			intercepted = true;
		}

		// If intercepted, start normal event dispatch. Also if there is already
		// a view that is handling the gesture, do normal event dispatch.
		if (intercepted || firstTouchTarget != null) {
			ev.setTargetAccessibilityFocus(false);
		}

		// Check for cancelation.
		final boolean canceled = actionMasked == MotionEvent.ACTION_CANCEL;

		// Update list of touch targets for pointer down, if needed.
		final boolean split = splitMotionEvents;
		TouchTarget newTouchTarget = null;
		boolean alreadyDispatchedToNewTouchTarget = false;
		if (!canceled && !intercepted) {
			if (actionMasked == MotionEvent.ACTION_DOWN
			    || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
			    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
				final int actionIndex = ev.getActionIndex(); // always 0 for down
				final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
				                                 : TouchTarget.ALL_POINTER_IDS;

				// Clean up earlier touch targets for this pointer id in case they
				// have become out of sync.
				removePointersFromTouchTargets(idBitsToAssign);

				final int childrenCount = children.size();
				if (newTouchTarget == null && childrenCount != 0) {
					final float x = ev.getX(actionIndex);
					final float y = ev.getY(actionIndex);
					// Find a child that can receive the event.
					// Scan children from front to back.
					final ArrayList<View> preorderedList = buildTouchDispatchChildList();
					for (int i = childrenCount - 1; i >= 0; i--) {
						final View child = preorderedList != null ? preorderedList.get(i) : children.get(i);

						if (!child.canReceivePointerEvents()
						    || !isTransformedTouchPointInView(x, y, child, null)) {
							ev.setTargetAccessibilityFocus(false);
							continue;
						}

						newTouchTarget = getTouchTarget(child);
						if (newTouchTarget != null) {
							// Child is already receiving touch within its bounds.
							// Give it the new pointer in addition to the ones it is handling.
							newTouchTarget.pointerIdBits |= idBitsToAssign;
							break;
						}

						if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
							// Child wants to receive touch within its bounds.
							newTouchTarget = addTouchTarget(child, idBitsToAssign);
							alreadyDispatchedToNewTouchTarget = true;
							break;
						}

						// The accessibility focus didn't handle the event, so clear
						// the flag and do a normal dispatch to all children.
						ev.setTargetAccessibilityFocus(false);
					}
				}

				if (newTouchTarget == null && firstTouchTarget != null) {
					// Did not find a child to receive the event.
					// Assign the pointer to the least recently added target.
					newTouchTarget = firstTouchTarget;
					while (newTouchTarget.next != null) {
						newTouchTarget = newTouchTarget.next;
					}
					newTouchTarget.pointerIdBits |= idBitsToAssign;
				}
			}
		}

		// Dispatch to touch targets.
		if (firstTouchTarget == null) {
			// No touch targets so treat this as an ordinary view.
			handled = dispatchTransformedTouchEvent(ev, canceled, null, TouchTarget.ALL_POINTER_IDS);
		} else {
			// Dispatch to touch targets, excluding the new touch target if we already
			// dispatched to it.  Cancel touch targets if necessary.
			TouchTarget predecessor = null;
			TouchTarget target = firstTouchTarget;
			while (target != null) {
				final TouchTarget next = target.next;
				if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
					handled = true;
				} else {
					final boolean cancelChild = intercepted;
					if (dispatchTransformedTouchEvent(ev, cancelChild, target.child, target.pointerIdBits)) {
						handled = true;
					}
					if (cancelChild) {
						if (predecessor == null) {
							firstTouchTarget = next;
						} else {
							predecessor.next = next;
						}
						target = next;
						continue;
					}
				}
				predecessor = target;
				target = next;
			}
		}

		// Update list of touch targets for pointer up or cancel, if needed.
		if (canceled
		    || actionMasked == MotionEvent.ACTION_UP
		    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
			resetTouchState();
		} else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
			final int actionIndex = ev.getActionIndex();
			final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
			removePointersFromTouchTargets(idBitsToRemove);
		}

		return handled;
	}

	// children in touch order when Z sorts them, else null: the children's order
	private ArrayList<View> buildTouchDispatchChildList() {
		boolean hasZ = false;
		for (View child : children) {
			if (child.getZ() != 0) {
				hasZ = true;
				break;
			}
		}
		if (!hasZ)
			return null;
		ArrayList<View> ordered = new ArrayList<View>(children);
		ordered.sort(new Comparator<View>() {
			@Override
			public int compare(View a, View b) {
				return Float.compare(a.getZ(), b.getZ());
			}
		});
		return ordered;
	}

	private void resetTouchState() {
		firstTouchTarget = null;
		disallowIntercept = false;
	}

	private void cancelAndClearTouchTargets(MotionEvent event) {
		if (firstTouchTarget != null) {
			if (event == null) {
				final long now = SystemClock.uptimeMillis();
				event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
				event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
			}

			for (TouchTarget target = firstTouchTarget; target != null; target = target.next) {
				dispatchTransformedTouchEvent(event, true, target.child, target.pointerIdBits);
			}
			firstTouchTarget = null;
		}
	}

	private TouchTarget getTouchTarget(View child) {
		for (TouchTarget target = firstTouchTarget; target != null; target = target.next) {
			if (target.child == child) {
				return target;
			}
		}
		return null;
	}

	private TouchTarget addTouchTarget(View child, int pointerIdBits) {
		final TouchTarget target = new TouchTarget(child, pointerIdBits);
		target.next = firstTouchTarget;
		firstTouchTarget = target;
		return target;
	}

	private void removePointersFromTouchTargets(int pointerIdBits) {
		TouchTarget predecessor = null;
		TouchTarget target = firstTouchTarget;
		while (target != null) {
			final TouchTarget next = target.next;
			if ((target.pointerIdBits & pointerIdBits) != 0) {
				target.pointerIdBits &= ~pointerIdBits;
				if (target.pointerIdBits == 0) {
					if (predecessor == null) {
						firstTouchTarget = next;
					} else {
						predecessor.next = next;
					}
					target = next;
					continue;
				}
			}
			predecessor = target;
			target = next;
		}
	}

	private void cancelTouchTarget(View view) {
		TouchTarget predecessor = null;
		TouchTarget target = firstTouchTarget;
		while (target != null) {
			final TouchTarget next = target.next;
			if (target.child == view) {
				if (predecessor == null) {
					firstTouchTarget = next;
				} else {
					predecessor.next = next;
				}

				final long now = SystemClock.uptimeMillis();
				MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
				event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
				view.dispatchTouchEvent(event);
				event.recycle();
				return;
			}
			predecessor = target;
			target = next;
		}
	}

	protected boolean isTransformedTouchPointInView(float x, float y, View child, PointF outLocalPoint) {
		final float[] point = new float[] {x, y};
		transformPointToViewLocal(point, child);
		final boolean isInView = child.pointInView(point[0], point[1]);
		if (isInView && outLocalPoint != null) {
			outLocalPoint.set(point[0], point[1]);
		}
		return isInView;
	}

	public void transformPointToViewLocal(float[] point, View child) {
		point[0] += getScrollX() - child.getLeft();
		point[1] += getScrollY() - child.getTop();

		if (!child.hasIdentityMatrix()) {
			child.getInverseMatrix().mapPoints(point);
		}
	}

	private boolean dispatchTransformedTouchEvent(MotionEvent event, boolean cancel, View child, int desiredPointerIdBits) {
		final boolean handled;

		// Canceling motions is a special case.  We don't need to perform any transformations
		// or filtering.  The important part is the action, not the contents.
		final int oldAction = event.getAction();
		if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
			event.setAction(MotionEvent.ACTION_CANCEL);
			if (child == null) {
				handled = super.dispatchTouchEvent(event);
			} else {
				handled = child.dispatchTouchEvent(event);
			}
			event.setAction(oldAction);
			return handled;
		}

		// Calculate the number of pointers to deliver.
		final int oldPointerIdBits = event.getPointerIdBits();
		final int newPointerIdBits = oldPointerIdBits & desiredPointerIdBits;

		// If for some reason we ended up in an inconsistent state where it looks like we
		// might produce a motion event with no pointers in it, then drop the event.
		if (newPointerIdBits == 0) {
			return false;
		}

		// If the number of pointers is the same and we don't need to perform any fancy
		// irreversible transformations, then we can reuse the motion event for this
		// dispatch as long as we are careful to revert any changes we make.
		// Otherwise we need to make a copy.
		final MotionEvent transformedEvent;
		if (newPointerIdBits == oldPointerIdBits) {
			if (child == null || child.hasIdentityMatrix()) {
				if (child == null) {
					handled = super.dispatchTouchEvent(event);
				} else {
					final float offsetX = getScrollX() - child.getLeft();
					final float offsetY = getScrollY() - child.getTop();
					event.offsetLocation(offsetX, offsetY);

					handled = child.dispatchTouchEvent(event);

					event.offsetLocation(-offsetX, -offsetY);
				}
				return handled;
			}
			transformedEvent = MotionEvent.obtain(event);
		} else {
			transformedEvent = event.split(newPointerIdBits);
		}

		// Perform any necessary transformations and dispatch.
		if (child == null) {
			handled = super.dispatchTouchEvent(transformedEvent);
		} else {
			final float offsetX = getScrollX() - child.getLeft();
			final float offsetY = getScrollY() - child.getTop();
			transformedEvent.offsetLocation(offsetX, offsetY);
			if (!child.hasIdentityMatrix()) {
				transformedEvent.transform(child.getInverseMatrix());
			}

			handled = child.dispatchTouchEvent(transformedEvent);
		}

		// Done.
		transformedEvent.recycle();
		return handled;
	}

	private static final class TouchTarget {
		public static final int ALL_POINTER_IDS = -1; // all ones

		// The touched child view.
		public final View child;

		// The combined bit mask of pointer ids for all pointers captured by the target.
		public int pointerIdBits;

		// The next target in the target list.
		public TouchTarget next;

		TouchTarget(View child, int pointerIdBits) {
			this.child = child;
			this.pointerIdBits = pointerIdBits;
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (isFocused()) {
			if (super.dispatchKeyEvent(event)) {
				return true;
			}
		} else if (focusedChild != null) {
			if (focusedChild.dispatchKeyEvent(event)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		int descendantFocusability = getDescendantFocusability();

		switch (descendantFocusability) {
			case FOCUS_BLOCK_DESCENDANTS:
				return super.requestFocus(direction, previouslyFocusedRect);
			case FOCUS_BEFORE_DESCENDANTS: {
				final boolean took = super.requestFocus(direction, previouslyFocusedRect);
				return took ? took : onRequestFocusInDescendants(direction, previouslyFocusedRect);
			}
			case FOCUS_AFTER_DESCENDANTS: {
				final boolean took = onRequestFocusInDescendants(direction, previouslyFocusedRect);
				return took ? took : super.requestFocus(direction, previouslyFocusedRect);
			}
			default:
				throw new IllegalStateException("descendant focusability must be one of FOCUS_BEFORE_DESCENDANTS, FOCUS_AFTER_DESCENDANTS, FOCUS_BLOCK_DESCENDANTS but is " + descendantFocusability);
		}
	}

	protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
		int index;
		int increment;
		int end;
		int count = children.size();
		if ((direction & FOCUS_FORWARD) != 0) {
			index = 0;
			increment = 1;
			end = count;
		} else {
			index = count - 1;
			increment = -1;
			end = -1;
		}
		for (int i = index; i != end; i += increment) {
			View child = children.get(i);
			if (child.getVisibility() == VISIBLE) {
				if (child.requestFocus(direction, previouslyFocusedRect)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	void unFocus(View focused) {
		if (focusedChild == null) {
			super.unFocus(focused);
		} else {
			focusedChild.unFocus(focused);
			focusedChild = null;
		}
	}

	@Override
	public void clearFocus() {
		if (focusedChild == null) {
			super.clearFocus();
		} else {
			View focused = focusedChild;
			focusedChild = null;
			focused.clearFocus();
		}
	}

	@Override
	public boolean hasFocus() {
		return isFocused() || focusedChild != null;
	}

	@Override
	public View findFocus() {
		if (isFocused()) {
			return this;
		}
		if (focusedChild != null) {
			return focusedChild.findFocus();
		}
		return null;
	}

	@Override
	public boolean hasFocusable() {
		if (getVisibility() != VISIBLE)
			return false;
		if (super.hasFocusable())
			return true;
		if (getDescendantFocusability() != FOCUS_BLOCK_DESCENDANTS) {
			for (View child : children) {
				if (child.hasFocusable())
					return true;
			}
		}
		return false;
	}

	// the root of the view tree searches, as AOSP's DecorView (isRootNamespace) and ViewRootImpl do
	public View focusSearch(View focused, int direction) {
		if (parent instanceof ViewGroup) {
			return parent.focusSearch(focused, direction);
		}
		return FocusFinder.getInstance().findNextFocus(this, focused, direction);
	}

	public void clearChildFocus(View child) {
		focusedChild = null;
		if (parent != null) {
			parent.clearChildFocus(this);
		}
	}

	@Override
	public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
		final int focusableCount = views.size();

		final int descendantFocusability = getDescendantFocusability();
		final boolean blockFocusForTouchscreen = shouldBlockFocusForTouchscreen();
		final boolean focusSelf = (isFocusableInTouchMode() || !blockFocusForTouchscreen);

		if (descendantFocusability == FOCUS_BLOCK_DESCENDANTS) {
			if (focusSelf) {
				super.addFocusables(views, direction, focusableMode);
			}
			return;
		}

		if (blockFocusForTouchscreen) {
			focusableMode |= FOCUSABLES_TOUCH_MODE;
		}

		if ((descendantFocusability == FOCUS_BEFORE_DESCENDANTS) && focusSelf) {
			super.addFocusables(views, direction, focusableMode);
		}

		int count = 0;
		final View[] visible = new View[children.size()];
		for (View child : children) {
			if (child.getVisibility() == VISIBLE) {
				visible[count++] = child;
			}
		}
		FocusFinder.sort(visible, 0, count, this, isLayoutRtl());
		for (int i = 0; i < count; ++i) {
			visible[i].addFocusables(views, direction, focusableMode);
		}

		// When set to FOCUS_AFTER_DESCENDANTS, we only add ourselves if
		// there aren't any focusable descendants.  this is
		// to avoid the focus search finding layouts when a more precise search
		// among the focusable children would be more interesting.
		if ((descendantFocusability == FOCUS_AFTER_DESCENDANTS) && focusSelf
		    && focusableCount == views.size()) {
			super.addFocusables(views, direction, focusableMode);
		}
	}

	@Override
	public void addTouchables(ArrayList<View> views) {
		super.addTouchables(views);
		for (View child : children) {
			if (child.getVisibility() == VISIBLE) {
				child.addTouchables(views);
			}
		}
	}

	@Override
	public void addKeyboardNavigationClusters(Collection<View> views, int direction) {
		final int focusableCount = views.size();

		super.addKeyboardNavigationClusters(views, direction);

		if (focusableCount != views.size()) {
			// No need to look for groups inside a group.
			return;
		}

		if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
			return;
		}

		int count = 0;
		final View[] visible = new View[children.size()];
		for (View child : children) {
			if (child.getVisibility() == VISIBLE) {
				visible[count++] = child;
			}
		}
		FocusFinder.sort(visible, 0, count, this, isLayoutRtl());
		for (int i = 0; i < count; ++i) {
			visible[i].addKeyboardNavigationClusters(views, direction);
		}
	}

	private boolean shouldBlockFocusForTouchscreen() {
		return getTouchscreenBlocksFocus() && getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
		    && !(isKeyboardNavigationCluster() && hasFocus());
	}

	@Override
	View findViewWithNextFocusForward(int forwardId) {
		View found = super.findViewWithNextFocusForward(forwardId);
		for (int i = 0; found == null && i < children.size(); i++)
			found = children.get(i).findViewWithNextFocusForward(forwardId);
		return found;
	}

	public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
		return false;
	}

	protected boolean isChildrenDrawingOrderEnabled() { return false; }

	@Override
	public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
		return false;
	}

	@Override
	public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
		return false;
	}

	@Override
	public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
		return false;
	}

	public void setClipToPadding(boolean clipToPadding) {}

	public View findViewById(int id) {
		if (Log.isLoggable(TAG, Log.VERBOSE))
			Slog.v(TAG, "findViewById: looking for id: " + String.format("%x", id) + "(" + getResources().getAssets().getResourceName(id) + ")"
			            + " | checking: " + this + ",id: " + String.format("%x", this.getId()) + ", id_str: " + this.getIdName());
		if (this.id == id) {
			if (Log.isLoggable(TAG, Log.VERBOSE))
				Slog.v(TAG, "findViewById: found: " + this + " | id: " + String.format("%x", this.getId()) + ", id_str: " + this.getIdName());
			return this;
		}
		for (View child : children) {
			View result = child.findViewById(id);
			if (result != null)
				return result;
		}
		return null;
	}

	public void detachViewFromParent(View view) {
		children.remove(view);
		view.parent = null;
		detachedChildren.add(view);
		if (!pendingHideDetachedChildren) {
			pendingHideDetachedChildren = true;
			post(new Runnable() {
				@Override
				public void run() {
					pendingHideDetachedChildren = false;
					for (View child : detachedChildren)
						child.native_setVisibility(child.widget, GONE, 0, false);
				}
			});
		}
	}

	public void setTouchscreenBlocksFocus(boolean touchscreenBlocksFocus) {
		this.touchscreenBlocksFocus = touchscreenBlocksFocus;
	}

	public void setClipChildren(boolean clipChildren) {}

	@Override
	protected void dispatchSetPressed(boolean pressed) {
		for (View child : children) {
			// Children that are clickable on their own should not
			// show a pressed state when their parent view does.
			// Clearing a pressed state always propagates.
			if (!pressed || (!child.isClickable() && !child.isLongClickable())) {
				child.setPressed(pressed);
			}
		}
	}

	@Override
	public View findViewWithTag(Object tag) {
		if (Objects.equals(tag, getTag()))
			return this;
		for (View child : children) {
			View result = child.findViewWithTag(tag);
			if (result != null)
				return result;
		}
		return null;
	}

	public void setLayoutTransition(LayoutTransition transition) {
		this.transition = transition;
	}

	public boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if (canvas instanceof GskCanvas)
			native_drawChild(widget, child.widget, ((GskCanvas)canvas).snapshot);
		return false;
	}

	protected void cleanupLayoutState(View child) {}

	public boolean shouldDelayChildPressedState() { return true; }

	public void removeAllViewsInLayout() {
		for (int i = getChildCount() - 1; i >= 0; i--) {
			removeViewInLayout(getChildAt(i));
		}
	}

	public void offsetChildrenTopAndBottom(int offset) {
		// FIXME
	}

	public final void offsetDescendantRectToMyCoords(View descendant, Rect rect) {
		offsetRectBetweenParentAndChild(descendant, rect, true);
	}

	public final void offsetRectIntoDescendantCoords(View descendant, Rect rect) {
		offsetRectBetweenParentAndChild(descendant, rect, false);
	}

	// clipToBounds isn't implemented: FocusFinder's touchable search is the only caller
	void offsetRectBetweenParentAndChild(View descendant, Rect rect, boolean offsetFromChildToParent, boolean clipToBounds) {
		offsetRectBetweenParentAndChild(descendant, rect, offsetFromChildToParent);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	void offsetRectBetweenParentAndChild(View descendant, Rect rect, boolean offsetFromChildToParent) {
		if (descendant == this)
			return;
		ViewParent theParent = descendant.parent;
		while (theParent instanceof View && theParent != this) {
			int dx = descendant.getLeft() - descendant.getScrollX();
			int dy = descendant.getTop() - descendant.getScrollY();
			if (offsetFromChildToParent)
				rect.offset(dx, dy);
			else
				rect.offset(-dx, -dy);
			descendant = (View)theParent;
			theParent = descendant.parent;
		}
		if (theParent != this)
			throw new IllegalArgumentException("parameter must be a descendant of this view");
		int dx = descendant.getLeft() - descendant.getScrollX();
		int dy = descendant.getTop() - descendant.getScrollY();
		if (offsetFromChildToParent)
			rect.offset(dx, dy);
		else
			rect.offset(-dx, -dy);
	}

	public boolean getClipToPadding() { return false; }

	public boolean isTransitionGroup() { return false; }

	public static class LayoutParams {
		public static final int FILL_PARENT = -1;
		public static final int MATCH_PARENT = -1;
		public static final int WRAP_CONTENT = -2;

		public int width = 0;
		public int height = 0;
		public float weight = 0;
		public int gravity = -1;

		public LayoutParams() {
			// FIXME
		}

		public LayoutParams(int width, int height) {
			this.width = width;
			this.height = height;
		}

		public LayoutParams(int width, int height, float weight) {
			this.width = width;
			this.height = height;
			this.weight = weight;
		}

		public LayoutParams(Context context, AttributeSet attrs) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewGroup_Layout);
			setBaseAttributes(a, R.styleable.ViewGroup_Layout_layout_width, R.styleable.ViewGroup_Layout_layout_height);
			a.recycle();
			a = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.layout_gravity});
			gravity = a.getInt(0, -1);
			a.recycle();
		}

		protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
			width = a.getLayoutDimension(widthAttr, "layout_width");
			height = a.getLayoutDimension(heightAttr, "layout_height");
		}

		public void resolveLayoutDirection(int layoutDirection) {}

		/**
		 * Used to animate layouts.
		 */
		//public LayoutAnimationController.AnimationParameters layoutAnimationParameters;
	}

	public static class MarginLayoutParams extends ViewGroup.LayoutParams {
		public int leftMargin;
		public int topMargin;
		public int rightMargin;
		public int bottomMargin;

		public MarginLayoutParams() {
			super();
		}

		public MarginLayoutParams(LayoutParams params) {
			super();
			width = params.width;
			height = params.height;
		}

		public MarginLayoutParams(int width, int height) {
			super(width, height);
		}

		public MarginLayoutParams(int width, int height, float weight) {
			super(width, height, weight);
		}

		public MarginLayoutParams(Context context, AttributeSet attributeSet) {
			super(context, attributeSet);
			TypedArray a = context.obtainStyledAttributes(attributeSet, com.android.internal.R.styleable.ViewGroup_MarginLayout);

			int margin = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_margin, -1);

			int marginVertical = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginVertical, -1);
			int marginHorizontal = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginHorizontal, -1);

			int marginStart = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginStart, -1);
			int marginEnd = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginEnd, -1);

			leftMargin = 0;
			topMargin = 0;
			rightMargin = 0;
			bottomMargin = 0;

			if (margin >= 0) {
				leftMargin = margin;
				topMargin = margin;
				rightMargin = margin;
				bottomMargin = margin;
			} else {
				if (marginVertical >= 0) {
					topMargin = marginVertical;
					bottomMargin = marginVertical;
				} else {
					topMargin = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginTop, 0);
					bottomMargin = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginBottom, 0);
				}

				if (marginHorizontal >= 0) {
					leftMargin = marginHorizontal;
					rightMargin = marginHorizontal;
				} else {
					leftMargin = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginLeft, 0);
					rightMargin = a.getDimensionPixelSize(com.android.internal.R.styleable.ViewGroup_MarginLayout_layout_marginRight, 0);

					if (marginStart >= 0) {
						leftMargin = marginStart;
					}

					if (marginEnd >= 0) {
						rightMargin = marginEnd;
					}
				}
			}

			a.recycle();
		}

		public MarginLayoutParams(ViewGroup.MarginLayoutParams source) {
			super();
			width = source.width;
			height = source.height;
			leftMargin = source.leftMargin;
			topMargin = source.topMargin;
			rightMargin = source.rightMargin;
			bottomMargin = source.bottomMargin;
		}

		public void setMargins(int left, int top, int right, int bottom) {
			leftMargin = left;
			topMargin = top;
			rightMargin = right;
			bottomMargin = bottom;
		}

		public int getMarginStart() {
			return leftMargin;
		}

		public int getMarginEnd() {
			return rightMargin;
		}

		public void setMarginStart(int marginStart) {
			leftMargin = marginStart;
		}
		public void setMarginEnd(int marginEnd) {
			rightMargin = marginEnd;
		}

		public int getLayoutDirection() { return LAYOUT_DIRECTION_LTR; }

		public boolean isMarginRelative() { return false; }
	}

	public interface OnHierarchyChangeListener {
		public void onChildViewAdded(View parent, View child);
		public void onChildViewRemoved(View parent, View child);
	}

	@Override
	public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'onNestedScrollAccepted'");
	}

	@Override
	public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'onNestedPreScroll'");
	}

	@Override
	public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'onNestedScroll'");
	}

	@Override
	public void onStopNestedScroll(View target) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'onStopNestedScroll'");
	}

	public void requestChildFocus(View child, View focused) {
		if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
			return;
		}

		// Unfocus us, if necessary
		super.unFocus(focused);

		// We had a previous notion of who had focus. Clear it.
		if (focusedChild != child) {
			if (focusedChild != null) {
				focusedChild.unFocus(focused);
			}

			focusedChild = child;
		}
		if (parent != null) {
			parent.requestChildFocus(this, focused);
		}
	}

	public boolean getClipChildren() {
		return false;
	}

	@Override
	public void onDescendantInvalidated(View child, View target) {}

	public boolean getTouchscreenBlocksFocus() { return touchscreenBlocksFocus; }

	public ViewGroupOverlay getOverlay() {
		if (viewGroupOverlay == null)
			viewGroupOverlay = new ViewGroupOverlay();
		return viewGroupOverlay;
	}

	public void scheduleLayoutAnimation() {}

	public boolean addStatesFromChildren() { return false; }
}
