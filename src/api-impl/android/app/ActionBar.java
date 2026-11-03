/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The API of core/java/android/app/ActionBar.java at android-security-10.0.0_r75, without its
 * documentation; modified for ATL.
 */

package android.app;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;

/* ATL draws no bar: ATLActionBar keeps what an app sets (ATL-42). */
public abstract class ActionBar {
	@Deprecated
	public static final int NAVIGATION_MODE_STANDARD = 0;
	@Deprecated
	public static final int NAVIGATION_MODE_LIST = 1;
	@Deprecated
	public static final int NAVIGATION_MODE_TABS = 2;

	public static final int DISPLAY_USE_LOGO = 0x1;
	public static final int DISPLAY_SHOW_HOME = 0x2;
	public static final int DISPLAY_HOME_AS_UP = 0x4;
	public static final int DISPLAY_SHOW_TITLE = 0x8;
	public static final int DISPLAY_SHOW_CUSTOM = 0x10;
	public static final int DISPLAY_TITLE_MULTIPLE_LINES = 0x20;

	public abstract void setCustomView(View view);
	public abstract void setCustomView(View view, LayoutParams layoutParams);
	public abstract void setCustomView(int resId);
	public abstract void setIcon(int resId);
	public abstract void setIcon(Drawable icon);
	public abstract void setLogo(int resId);
	public abstract void setLogo(Drawable logo);
	@Deprecated
	public abstract void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener callback);
	@Deprecated
	public abstract void setSelectedNavigationItem(int position);
	@Deprecated
	public abstract int getSelectedNavigationIndex();
	@Deprecated
	public abstract int getNavigationItemCount();
	public abstract void setTitle(CharSequence title);
	public abstract void setTitle(int resId);
	public abstract void setSubtitle(CharSequence subtitle);
	public abstract void setSubtitle(int resId);
	public abstract void setDisplayOptions(int options);
	public abstract void setDisplayOptions(int options, int mask);
	public abstract void setDisplayUseLogoEnabled(boolean useLogo);
	public abstract void setDisplayShowHomeEnabled(boolean showHome);
	public abstract void setDisplayHomeAsUpEnabled(boolean showHomeAsUp);
	public abstract void setDisplayShowTitleEnabled(boolean showTitle);
	public abstract void setDisplayShowCustomEnabled(boolean showCustom);
	public abstract void setBackgroundDrawable(Drawable d);

	public void setStackedBackgroundDrawable(Drawable d) {}

	public void setSplitBackgroundDrawable(Drawable d) {}

	public abstract View getCustomView();
	public abstract CharSequence getTitle();
	public abstract CharSequence getSubtitle();
	@Deprecated
	public abstract int getNavigationMode();
	@Deprecated
	public abstract void setNavigationMode(int mode);
	public abstract int getDisplayOptions();
	@Deprecated
	public abstract Tab newTab();
	@Deprecated
	public abstract void addTab(Tab tab);
	@Deprecated
	public abstract void addTab(Tab tab, boolean setSelected);
	@Deprecated
	public abstract void addTab(Tab tab, int position);
	@Deprecated
	public abstract void addTab(Tab tab, int position, boolean setSelected);
	@Deprecated
	public abstract void removeTab(Tab tab);
	@Deprecated
	public abstract void removeTabAt(int position);
	@Deprecated
	public abstract void removeAllTabs();
	@Deprecated
	public abstract void selectTab(Tab tab);
	@Deprecated
	public abstract Tab getSelectedTab();
	@Deprecated
	public abstract Tab getTabAt(int index);
	@Deprecated
	public abstract int getTabCount();
	public abstract int getHeight();
	public abstract void show();
	public abstract void hide();
	public abstract boolean isShowing();
	public abstract void addOnMenuVisibilityListener(OnMenuVisibilityListener listener);
	public abstract void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener);

	public void setHomeButtonEnabled(boolean enabled) {}

	public Context getThemedContext() {
		return null;
	}

	public boolean isTitleTruncated() {
		return false;
	}

	public void setHomeAsUpIndicator(Drawable indicator) {}

	public void setHomeAsUpIndicator(int resId) {}

	public void setHomeActionContentDescription(CharSequence description) {}

	public void setHomeActionContentDescription(int resId) {}

	public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
		if (hideOnContentScroll) {
			throw new UnsupportedOperationException("Hide on content scroll is not supported in this action bar configuration.");
		}
	}

	public boolean isHideOnContentScrollEnabled() {
		return false;
	}

	public int getHideOffset() {
		return 0;
	}

	public void setHideOffset(int offset) {
		if (offset != 0) {
			throw new UnsupportedOperationException("Setting an explicit action bar hide offset is not supported in this action bar configuration.");
		}
	}

	public void setElevation(float elevation) {
		if (elevation != 0) {
			throw new UnsupportedOperationException("Setting a non-zero elevation is not supported in this action bar configuration.");
		}
	}

	public float getElevation() {
		return 0;
	}

	/** @hide */
	public void setDefaultDisplayHomeAsUpEnabled(boolean enabled) {}

	/** @hide */
	public void setShowHideAnimationEnabled(boolean enabled) {}

	/** @hide */
	public void onConfigurationChanged(Configuration config) {}

	/** @hide */
	public void dispatchMenuVisibilityChanged(boolean visible) {}

	/** @hide */
	public ActionMode startActionMode(ActionMode.Callback callback) {
		return null;
	}

	/** @hide */
	public boolean openOptionsMenu() {
		return false;
	}

	/** @hide */
	public boolean closeOptionsMenu() {
		return false;
	}

	/** @hide */
	public boolean invalidateOptionsMenu() {
		return false;
	}

	/** @hide */
	public boolean onMenuKeyEvent(KeyEvent event) {
		return false;
	}

	/** @hide */
	public boolean onKeyShortcut(int keyCode, KeyEvent event) {
		return false;
	}

	/** @hide */
	public boolean collapseActionView() {
		return false;
	}

	/** @hide */
	public void setWindowTitle(CharSequence title) {}

	/** @hide */
	public void onDestroy() {}

	public interface OnNavigationListener {
		public boolean onNavigationItemSelected(int itemPosition, long itemId);
	}

	public interface OnMenuVisibilityListener {
		public void onMenuVisibilityChanged(boolean isVisible);
	}

	@Deprecated
	public static abstract class Tab {
		public static final int INVALID_POSITION = -1;

		public abstract int getPosition();
		public abstract Drawable getIcon();
		public abstract CharSequence getText();
		public abstract Tab setIcon(Drawable icon);
		public abstract Tab setIcon(int resId);
		public abstract Tab setText(CharSequence text);
		public abstract Tab setText(int resId);
		public abstract Tab setCustomView(View view);
		public abstract Tab setCustomView(int layoutResId);
		public abstract View getCustomView();
		public abstract Tab setTag(Object obj);
		public abstract Object getTag();
		public abstract Tab setTabListener(TabListener listener);
		public abstract void select();
		public abstract Tab setContentDescription(int resId);
		public abstract Tab setContentDescription(CharSequence contentDesc);
		public abstract CharSequence getContentDescription();
	}

	@Deprecated
	public interface TabListener {
		public void onTabSelected(Tab tab, FragmentTransaction ft);
		public void onTabUnselected(Tab tab, FragmentTransaction ft);
		public void onTabReselected(Tab tab, FragmentTransaction ft);
	}

	public static class LayoutParams extends ViewGroup.MarginLayoutParams {
		public int gravity = Gravity.NO_GRAVITY;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);

			TypedArray a = c.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ActionBar_LayoutParams);
			gravity = a.getInt(com.android.internal.R.styleable.ActionBar_LayoutParams_layout_gravity, Gravity.NO_GRAVITY);
			a.recycle();
		}

		public LayoutParams(int width, int height) {
			super(width, height);
			this.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
		}

		public LayoutParams(int width, int height, int gravity) {
			super(width, height);
			this.gravity = gravity;
		}

		public LayoutParams(int gravity) {
			this(WRAP_CONTENT, MATCH_PARENT, gravity);
		}

		public LayoutParams(LayoutParams source) {
			super(source);
			this.gravity = source.gravity;
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}
	}
}
