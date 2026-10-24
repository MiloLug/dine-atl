/*
 * Portions Copyright (C) 2007 The Android Open Source Project, from
 * core/java/android/widget/CompoundButton.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.android.internal.R;

/* The checked state is Java's; the ui backend's control only shows it. The button drawable is kept,
 * not drawn: an onDraw here would make every CompoundButton drawn by Java. */
public abstract class CompoundButton extends Button implements Checkable {
	private boolean mChecked;
	private boolean mBroadcasting;
	private OnCheckedChangeListener mOnCheckedChangeListener;
	private OnCheckedChangeListener mOnCheckedChangeWidgetListener;

	private static final int[] CHECKED_STATE_SET = {
		R.attr.state_checked
	};

	Drawable button_drawable = null;
	public Drawable mButtonDrawable; // directly accessed by androidx

	public CompoundButton(Context context) {
		this(context, null);
	}

	public CompoundButton(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		TypedArray typedArray = context.obtainStyledAttributes(
		    attrs, R.styleable.CompoundButton, defStyleAttr, defStyleRes);
		setChecked(typedArray.getBoolean(R.styleable.CompoundButton_checked, false));
		typedArray.recycle();
	}

	@Override
	int kind() {
		return KIND_CHECK;
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	@Override
	public void toggle() {
		setChecked(!mChecked);
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	@Override
	public boolean performClick() {
		toggle();
		return super.performClick();
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	@Override
	public void setChecked(boolean checked) {
		if (mChecked != checked) {
			mChecked = checked;
			refreshDrawableState();

			// Avoid infinite recursions if setChecked() is called from a listener
			if (mBroadcasting) {
				return;
			}

			mBroadcasting = true;
			if (mOnCheckedChangeListener != null) {
				mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
			}
			if (mOnCheckedChangeWidgetListener != null) {
				mOnCheckedChangeWidgetListener.onCheckedChanged(this, mChecked);
			}
			mBroadcasting = false;
		}
	}

	public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
		mOnCheckedChangeListener = listener;
	}

	// RadioGroup's, beside the app's
	void setOnCheckedChangeWidgetListener(OnCheckedChangeListener listener) {
		mOnCheckedChangeWidgetListener = listener;
	}

	public static interface OnCheckedChangeListener {
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	@Override
	protected int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}

	public void setButtonTintList(ColorStateList list) {
	}

	public void setButtonDrawable(Drawable drawable) {
		button_drawable = drawable;
	}

	public Drawable getButtonDrawable() {
		return button_drawable;
	}

	public ColorStateList getButtonTintList() {
		return null;
	}

	public PorterDuff.Mode getButtonTintMode() {
		return null;
	}
}
