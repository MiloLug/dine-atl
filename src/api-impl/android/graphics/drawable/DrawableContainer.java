/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * graphics/java/android/graphics/drawable/DrawableContainer.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.SparseArray;

public class DrawableContainer extends Drawable implements Drawable.Callback {

	private DrawableContainerState state;
	private int curIndex = -1;
	private Drawable currDrawable;
	private boolean mutated;

	// Drawable() calls this override, so no constructor here
	protected native long native_constructor();
	protected native void native_selectChild(long container, long child);

	/* Copyright (C) 2006 The Android Open Source Project */
	public boolean selectDrawable(int index) {
		if (index == curIndex)
			return false;
		if (currDrawable != null)
			currDrawable.setVisible(false, false);
		if (index >= 0 && index < state.childCount) {
			currDrawable = state.getChild(index);
			curIndex = index;
			if (currDrawable != null)
				initializeDrawableForDisplay(currDrawable);
		} else {
			currDrawable = null;
			curIndex = -1;
		}
		native_selectChild(paintable, currDrawable != null ? currDrawable.paintable : 0);
		invalidateSelf();
		return true;
	}

	private void initializeDrawableForDisplay(Drawable d) {
		d.setVisible(isVisible(), true);
		d.setState(getState());
		d.setBounds(getBounds());
		d.setLayoutDirection(getLayoutDirection());
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	protected void setConstantState(DrawableContainerState state) {
		this.state = state;
		// the state's children may be other objects than the ones shown
		if (curIndex >= 0) {
			currDrawable = state.getChild(curIndex);
			if (currDrawable != null)
				initializeDrawableForDisplay(currDrawable);
			native_selectChild(paintable, currDrawable != null ? currDrawable.paintable : 0);
		}
	}

	@Override
	public ConstantState getConstantState() {
		return state.canConstantState() ? state : null;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public Drawable mutate() {
		if (!mutated && super.mutate() == this) {
			final DrawableContainerState clone = cloneConstantState();
			clone.mutate();
			setConstantState(clone);
			mutated = true;
		}
		return this;
	}

	// the base of mutate(): subclasses return a shallow copy of their state
	DrawableContainerState cloneConstantState() {
		return state;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public void invalidateDrawable(Drawable who) {
		if (who == currDrawable && getCallback() != null)
			getCallback().invalidateDrawable(this);
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		if (who == currDrawable && getCallback() != null)
			getCallback().scheduleDrawable(this, what, when);
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		if (who == currDrawable && getCallback() != null)
			getCallback().unscheduleDrawable(this, what);
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	public abstract static class DrawableContainerState extends ConstantState {

		final DrawableContainer owner;
		Resources sourceRes;
		private Drawable[] drawables;
		private int childCount;
		// a copy's children not made yet: built from the original's on first use
		private SparseArray<ConstantState> futures;
		private boolean checkedConstantState;
		private boolean canConstantState;

		public DrawableContainerState(DrawableContainerState orig, DrawableContainer owner, Resources res) {
			this.owner = owner;
			sourceRes = res != null ? res : (orig != null ? orig.sourceRes : null);
			if (orig != null) {
				checkedConstantState = true;
				canConstantState = true;
				drawables = new Drawable[orig.drawables.length];
				childCount = orig.childCount;
				futures = orig.futures != null ? orig.futures.clone() : new SparseArray<>(childCount);
				// a child without a constant state can't be copied: the copy shares it
				for (int i = 0; i < childCount; i++) {
					if (orig.drawables[i] != null) {
						final ConstantState cs = orig.drawables[i].getConstantState();
						if (cs != null)
							futures.put(i, cs);
						else
							drawables[i] = orig.drawables[i];
					}
				}
			} else {
				drawables = new Drawable[10];
			}
		}

		public int getCapacity() {
			return drawables.length;
		}

		public int getChildCount() {
			return childCount;
		}

		public final Drawable[] getChildren() {
			createAllFutures();
			return drawables;
		}

		public final Drawable getChild(int index) {
			final Drawable result = drawables[index];
			if (result != null)
				return result;
			if (futures != null) {
				final int keyIndex = futures.indexOfKey(index);
				if (keyIndex >= 0) {
					final Drawable prepared = prepareDrawable(futures.valueAt(keyIndex).newDrawable(sourceRes));
					drawables[index] = prepared;
					futures.removeAt(keyIndex);
					if (futures.size() == 0)
						futures = null;
					return prepared;
				}
			}
			return null;
		}

		public int addChild(Drawable dr) {
			final int pos = childCount;
			if (pos >= drawables.length)
				growArray(pos, pos + 10);
			dr.mutate();
			dr.setVisible(false, true);
			dr.setCallback(owner);
			drawables[pos] = dr;
			childCount++;
			checkedConstantState = false;
			return pos;
		}

		public void growArray(int oldSize, int newSize) {
			Drawable[] newDrawables = new Drawable[newSize];
			System.arraycopy(drawables, 0, newDrawables, 0, oldSize);
			drawables = newDrawables;
		}

		private void createAllFutures() {
			if (futures != null) {
				for (int keyIndex = 0; keyIndex < futures.size(); keyIndex++)
					drawables[futures.keyAt(keyIndex)] = prepareDrawable(futures.valueAt(keyIndex).newDrawable(sourceRes));
				futures = null;
			}
		}

		private Drawable prepareDrawable(Drawable child) {
			child = child.mutate();
			child.setCallback(owner);
			return child;
		}

		// futures mutate when they are made
		private void mutate() {
			for (int i = 0; i < childCount; i++) {
				if (drawables[i] != null)
					drawables[i].mutate();
			}
		}

		public synchronized boolean canConstantState() {
			if (checkedConstantState)
				return canConstantState;
			createAllFutures();
			checkedConstantState = true;
			for (int i = 0; i < childCount; i++) {
				if (drawables[i].getConstantState() == null) {
					canConstantState = false;
					return false;
				}
			}
			canConstantState = true;
			return true;
		}

		@Override
		public int getChangingConfigurations() {
			return owner.getChangingConfigurations();
		}
	}

	@Override
	public Drawable getCurrent() {
		return currDrawable;
	}

	@Override
	public void draw(Canvas canvas) {
		if (currDrawable != null)
			currDrawable.draw(canvas);
	}

	@Override
	public int getIntrinsicHeight() {
		return currDrawable != null ? currDrawable.getIntrinsicHeight() : -1;
	}

	@Override
	public int getIntrinsicWidth() {
		return currDrawable != null ? currDrawable.getIntrinsicWidth() : -1;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		if (currDrawable != null)
			currDrawable.setBounds(bounds);
	}

	public void setEnterFadeDuration(int duration) {}

	public void setExitFadeDuration(int duration) {}
}
