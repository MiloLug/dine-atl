/*
 * Portions Copyright (C) 2010 The Android Open Source Project, from
 * core/java/com/android/internal/app/WindowDecorActionBar.java at android-security-10.0.0_r75,
 * Apache License 2.0; see doc/AOSP.md.
 */
package android.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SpinnerAdapter;
import java.util.ArrayList;

/* An activity's ActionBar: it keeps what the app sets and draws nothing (ATL-42). Tabs and list
 * navigation call the app's listeners as WindowDecorActionBar does, so content the app switches
 * on a selection still appears. */
class ATLActionBar extends ActionBar {
	private final Activity activity;
	private CharSequence title;
	private boolean title_set; // ToolbarWidgetWrapper's mTitleSet
	private CharSequence subtitle;
	private Drawable icon;
	private Drawable logo;
	private Drawable background;
	private View custom_view;
	private int display_options;
	private int navigation_mode = NAVIGATION_MODE_STANDARD;
	private boolean showing = true;

	private final ArrayList<TabImpl> tabs = new ArrayList<>();
	private TabImpl selected_tab;
	private int saved_tab_position = Tab.INVALID_POSITION;

	private SpinnerAdapter list_adapter;
	private OnNavigationListener list_listener;
	private int list_position = -1;

	private final ArrayList<OnMenuVisibilityListener> menu_visibility_listeners = new ArrayList<>();

	ATLActionBar(Activity activity) {
		this.activity = activity;
		TypedArray a = activity.obtainStyledAttributes(null, com.android.internal.R.styleable.ActionBar, com.android.internal.R.attr.actionBarStyle, 0);
		display_options = a.getInt(com.android.internal.R.styleable.ActionBar_displayOptions, DISPLAY_SHOW_HOME | DISPLAY_SHOW_TITLE);
		title = a.getText(com.android.internal.R.styleable.ActionBar_title);
		subtitle = a.getText(com.android.internal.R.styleable.ActionBar_subtitle);
		icon = a.getDrawable(com.android.internal.R.styleable.ActionBar_icon);
		logo = a.getDrawable(com.android.internal.R.styleable.ActionBar_logo);
		navigation_mode = a.getInt(com.android.internal.R.styleable.ActionBar_navigationMode, NAVIGATION_MODE_STANDARD);
		a.recycle();
		title_set = !TextUtils.isEmpty(title);
		if (!title_set)
			title = activity.getTitle();
	}

	@Override
	public void setCustomView(View view) {
		custom_view = view;
	}

	@Override
	public void setCustomView(View view, LayoutParams layoutParams) {
		if (view != null)
			view.setLayoutParams(layoutParams);
		custom_view = view;
	}

	@Override
	public void setCustomView(int resId) {
		setCustomView(LayoutInflater.from(getThemedContext()).inflate(resId, null, false));
	}

	@Override
	public void setIcon(int resId) {
		setIcon(resId != 0 ? activity.getDrawable(resId) : null);
	}

	@Override
	public void setIcon(Drawable icon) {
		this.icon = icon;
	}

	@Override
	public void setLogo(int resId) {
		setLogo(resId != 0 ? activity.getDrawable(resId) : null);
	}

	@Override
	public void setLogo(Drawable logo) {
		this.logo = logo;
	}

	/* Copyright (C) 2010 The Android Open Source Project: setListNavigationCallbacks to
	 * getNavigationItemCount, setNavigationMode to selectTab */
	@Override
	public void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener callback) {
		list_adapter = adapter;
		list_listener = callback;
		list_position = -1;
		select_first_list_item();
	}

	/* A Spinner selects its first item when it gets an adapter and reports it to the listener. */
	private void select_first_list_item() {
		if (navigation_mode == NAVIGATION_MODE_LIST && list_position < 0 && list_adapter != null && list_adapter.getCount() > 0)
			setSelectedNavigationItem(0);
	}

	@Override
	public void setSelectedNavigationItem(int position) {
		switch (navigation_mode) {
			case NAVIGATION_MODE_TABS:
				selectTab(tabs.get(position));
				break;
			case NAVIGATION_MODE_LIST:
				list_position = position;
				if (list_listener != null && list_adapter != null)
					list_listener.onNavigationItemSelected(position, list_adapter.getItemId(position));
				break;
			default:
				throw new IllegalStateException("setSelectedNavigationIndex not valid for current navigation mode");
		}
	}

	@Override
	public int getSelectedNavigationIndex() {
		switch (navigation_mode) {
			case NAVIGATION_MODE_TABS:
				return selected_tab != null ? selected_tab.getPosition() : -1;
			case NAVIGATION_MODE_LIST:
				return list_position;
			default:
				return -1;
		}
	}

	@Override
	public int getNavigationItemCount() {
		switch (navigation_mode) {
			case NAVIGATION_MODE_TABS:
				return tabs.size();
			case NAVIGATION_MODE_LIST:
				return list_adapter != null ? list_adapter.getCount() : 0;
			default:
				return 0;
		}
	}

	@Override
	public void setTitle(CharSequence title) {
		this.title = title;
		title_set = true;
	}

	@Override
	public void setTitle(int resId) {
		setTitle(activity.getText(resId));
	}

	/* Copyright (C) 2014 The Android Open Source Project: ToolbarWidgetWrapper's */
	// the app's own title outranks the window's
	@Override
	public void setWindowTitle(CharSequence title) {
		if (!title_set)
			this.title = title;
	}

	@Override
	public void setSubtitle(CharSequence subtitle) {
		this.subtitle = subtitle;
	}

	@Override
	public void setSubtitle(int resId) {
		setSubtitle(activity.getText(resId));
	}

	@Override
	public void setDisplayOptions(int options) {
		display_options = options;
	}

	@Override
	public void setDisplayOptions(int options, int mask) {
		display_options = (options & mask) | (display_options & ~mask);
	}

	@Override
	public void setDisplayUseLogoEnabled(boolean useLogo) {
		setDisplayOptions(useLogo ? DISPLAY_USE_LOGO : 0, DISPLAY_USE_LOGO);
	}

	@Override
	public void setDisplayShowHomeEnabled(boolean showHome) {
		setDisplayOptions(showHome ? DISPLAY_SHOW_HOME : 0, DISPLAY_SHOW_HOME);
	}

	@Override
	public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
		setDisplayOptions(showHomeAsUp ? DISPLAY_HOME_AS_UP : 0, DISPLAY_HOME_AS_UP);
	}

	@Override
	public void setDisplayShowTitleEnabled(boolean showTitle) {
		setDisplayOptions(showTitle ? DISPLAY_SHOW_TITLE : 0, DISPLAY_SHOW_TITLE);
	}

	@Override
	public void setDisplayShowCustomEnabled(boolean showCustom) {
		setDisplayOptions(showCustom ? DISPLAY_SHOW_CUSTOM : 0, DISPLAY_SHOW_CUSTOM);
	}

	@Override
	public void setBackgroundDrawable(Drawable d) {
		background = d;
	}

	@Override
	public View getCustomView() {
		return custom_view;
	}

	@Override
	public CharSequence getTitle() {
		return title;
	}

	@Override
	public CharSequence getSubtitle() {
		return subtitle;
	}

	@Override
	public int getNavigationMode() {
		return navigation_mode;
	}

	@Override
	public void setNavigationMode(int mode) {
		int old = navigation_mode;
		if (old == NAVIGATION_MODE_TABS) {
			saved_tab_position = getSelectedNavigationIndex();
			selectTab(null);
		}
		navigation_mode = mode;
		if (mode == NAVIGATION_MODE_TABS && saved_tab_position != Tab.INVALID_POSITION) {
			setSelectedNavigationItem(saved_tab_position);
			saved_tab_position = Tab.INVALID_POSITION;
		}
		select_first_list_item();
	}

	@Override
	public int getDisplayOptions() {
		return display_options;
	}

	@Override
	public Tab newTab() {
		return new TabImpl();
	}

	@Override
	public void addTab(Tab tab) {
		addTab(tab, tabs.isEmpty());
	}

	@Override
	public void addTab(Tab tab, boolean setSelected) {
		addTab(tab, tabs.size(), setSelected);
	}

	@Override
	public void addTab(Tab tab, int position) {
		addTab(tab, position, tabs.isEmpty());
	}

	@Override
	public void addTab(Tab tab, int position, boolean setSelected) {
		TabImpl impl = (TabImpl)tab;
		if (impl.listener == null)
			throw new IllegalStateException("Action Bar Tab must have a Callback");
		tabs.add(position, impl);
		renumber_tabs(position);
		if (setSelected)
			selectTab(tab);
	}

	private void renumber_tabs(int from) {
		for (int i = from; i < tabs.size(); i++)
			tabs.get(i).position = i;
	}

	@Override
	public void removeTab(Tab tab) {
		removeTabAt(tab.getPosition());
	}

	@Override
	public void removeTabAt(int position) {
		int selected = selected_tab != null ? selected_tab.getPosition() : saved_tab_position;
		TabImpl removed = tabs.remove(position);
		if (removed != null)
			removed.position = Tab.INVALID_POSITION;
		renumber_tabs(position);
		if (selected == position)
			selectTab(tabs.isEmpty() ? null : tabs.get(Math.max(0, position - 1)));
	}

	@Override
	public void removeAllTabs() {
		if (selected_tab != null)
			selectTab(null);
		for (TabImpl tab : tabs)
			tab.position = Tab.INVALID_POSITION;
		tabs.clear();
		saved_tab_position = Tab.INVALID_POSITION;
	}

	@Override
	public void selectTab(Tab tab) {
		if (navigation_mode != NAVIGATION_MODE_TABS) {
			saved_tab_position = tab != null ? tab.getPosition() : Tab.INVALID_POSITION;
			return;
		}
		FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
		if (selected_tab == tab) {
			if (selected_tab != null)
				selected_tab.listener.onTabReselected(selected_tab, ft);
		} else {
			if (selected_tab != null)
				selected_tab.listener.onTabUnselected(selected_tab, ft);
			selected_tab = (TabImpl)tab;
			if (selected_tab != null)
				selected_tab.listener.onTabSelected(selected_tab, ft);
		}
		ft.commit();
	}

	@Override
	public Tab getSelectedTab() {
		return selected_tab;
	}

	@Override
	public Tab getTabAt(int index) {
		return tabs.get(index);
	}

	@Override
	public int getTabCount() {
		return tabs.size();
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public void show() {
		showing = true;
	}

	@Override
	public void hide() {
		showing = false;
	}

	@Override
	public boolean isShowing() {
		return showing;
	}

	@Override
	public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
		menu_visibility_listeners.add(listener);
	}

	@Override
	public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
		menu_visibility_listeners.remove(listener);
	}

	@Override
	public void dispatchMenuVisibilityChanged(boolean visible) {
		for (OnMenuVisibilityListener listener : menu_visibility_listeners)
			listener.onMenuVisibilityChanged(visible);
	}

	@Override
	public Context getThemedContext() {
		return activity;
	}

	private class TabImpl extends Tab {
		private TabListener listener;
		private Object tag;
		private Drawable icon;
		private CharSequence text;
		private CharSequence content_description;
		private View custom_view;
		private int position = INVALID_POSITION;

		@Override
		public int getPosition() {
			return position;
		}

		@Override
		public Drawable getIcon() {
			return icon;
		}

		@Override
		public CharSequence getText() {
			return text;
		}

		@Override
		public Tab setIcon(Drawable icon) {
			this.icon = icon;
			return this;
		}

		@Override
		public Tab setIcon(int resId) {
			return setIcon(activity.getDrawable(resId));
		}

		@Override
		public Tab setText(CharSequence text) {
			this.text = text;
			return this;
		}

		@Override
		public Tab setText(int resId) {
			return setText(activity.getText(resId));
		}

		@Override
		public Tab setCustomView(View view) {
			custom_view = view;
			return this;
		}

		@Override
		public Tab setCustomView(int layoutResId) {
			return setCustomView(LayoutInflater.from(getThemedContext()).inflate(layoutResId, null));
		}

		@Override
		public View getCustomView() {
			return custom_view;
		}

		@Override
		public Tab setTag(Object obj) {
			tag = obj;
			return this;
		}

		@Override
		public Object getTag() {
			return tag;
		}

		@Override
		public Tab setTabListener(TabListener listener) {
			this.listener = listener;
			return this;
		}

		@Override
		public void select() {
			selectTab(this);
		}

		@Override
		public Tab setContentDescription(int resId) {
			return setContentDescription(activity.getText(resId));
		}

		@Override
		public Tab setContentDescription(CharSequence contentDesc) {
			content_description = contentDesc;
			return this;
		}

		@Override
		public CharSequence getContentDescription() {
			return content_description;
		}
	}
}
