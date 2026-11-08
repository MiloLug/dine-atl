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
 */

package android.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnTouchListener;
import com.android.internal.R;
import java.util.ArrayList;
import java.util.List;

/* PopupMenu's API over the ui slot's native menu (ui_menu_show): its items go to the backend at
 * show(), which answers once with the picked item or the dismissal (atlMenuResult). */

public class PopupMenu {
	private final Context mContext;
	private final View mAnchor;
	private OnMenuItemClickListener mMenuItemClickListener;
	private OnDismissListener mOnDismissListener;
	private OnTouchListener mDragListener;
	private long shown; // the native menu while it is up, else 0
	private List<MenuItemImpl> shown_items; // its items in the order the backend got them
	private MenuImpl menu;

	/**
	 * Constructor to create a new popup menu with an anchor view.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 */
	public PopupMenu(Context context, View anchor) {
		this(context, anchor, Gravity.NO_GRAVITY);
	}
	/**
	 * Constructor to create a new popup menu with an anchor view and alignment
	 * gravity.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 * @param gravity The {@link Gravity} value for aligning the popup with its
	 *        anchor.
	 */
	public PopupMenu(Context context, View anchor, int gravity) {
		this(context, anchor, gravity, R.attr.popupMenuStyle, 0);
	}
	/**
	 * Constructor a create a new popup menu with a specific style.
	 *
	 * @param context Context the popup menu is running in, through which it
	 *        can access the current theme, resources, etc.
	 * @param anchor Anchor view for this popup. The popup will appear below
	 *        the anchor if there is room, or above it if there is not.
	 * @param gravity The {@link Gravity} value for aligning the popup with its
	 *        anchor.
	 * @param popupStyleAttr An attribute in the current theme that contains a
	 *        reference to a style resource that supplies default values for
	 *        the popup window. Can be 0 to not look for defaults.
	 * @param popupStyleRes A resource identifier of a style resource that
	 *        supplies default values for the popup window, used only if
	 *        popupStyleAttr is 0 or can not be found in the theme. Can be 0
	 *        to not look for defaults.
	 */
	public PopupMenu(Context context, View anchor, int gravity, int popupStyleAttr,
	                 int popupStyleRes) {
		mContext = context;
		mAnchor = anchor;
		menu = new MenuImpl();
	}

	/**
	 * @return a {@link MenuInflater} that can be used to inflate menu items
	 *         from XML into the menu returned by {@link #getMenu()}
	 * @see #getMenu()
	 */
	public MenuInflater getMenuInflater() {
		return new MenuInflater(mContext);
	}

	/**
	 * Sets a listener that will be notified when the user selects an item from
	 * the menu.
	 *
	 * @param listener the listener to notify
	 */
	public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
		mMenuItemClickListener = listener;
	}

	/**
	 * Sets a listener that will be notified when this menu is dismissed.
	 *
	 * @param listener the listener to notify
	 */
	public void setOnDismissListener(OnDismissListener listener) {
		mOnDismissListener = listener;
	}

	// titles, parents and flags per item, depth first; flags: 1 enabled, 2 checkable, 4 checked, 8 submenu
	private native long nativeShow(long anchor, String[] titles, int[] parents, int[] flags);
	private native void nativeClose(long menu);

	// the visible items, a submenu's after the item that opens it
	private static void flatten(MenuImpl from, int parent, List<MenuItemImpl> items, List<Integer> parents) {
		for (MenuItemImpl item : from.items) {
			if (!item.visible)
				continue;
			int index = items.size();
			items.add(item);
			parents.add(parent);
			if (item.subMenu != null)
				flatten(item.subMenu, index, items, parents);
		}
	}

	// the backend's answer, or nativeClose's: an item's index or -1 (UI_MENU_DISMISSED)
	private void atlMenuResult(int index) {
		shown = 0;
		if (mContext.atl_process_app().open_menu == this)
			mContext.atl_process_app().open_menu = null;
		MenuItemImpl item = index >= 0 ? shown_items.get(index) : null;
		shown_items = null;
		/* Copyright (C) 2006 The Android Open Source Project: MenuItemImpl.invoke, then the
		 * dismissal that a pick brings */
		if (item != null && (item.click_listener == null || !item.click_listener.onMenuItemClick(item))
		    && mMenuItemClickListener != null)
			mMenuItemClickListener.onMenuItemClick(item);
		if (mOnDismissListener != null)
			mOnDismissListener.onDismiss(this);
	}

	/**
	 * Inflate a menu resource into this PopupMenu. This is equivalent to
	 * calling {@code popupMenu.getMenuInflater().inflate(menuRes, popupMenu.getMenu())}.
	 *
	 * @param menuRes Menu resource to inflate
	 * @throws Exception
	 */
	public void inflate(int menuRes) throws Exception {
		getMenuInflater().inflate(menuRes, getMenu());
	}

	/**
	* Show the menu popup anchored to the view specified during construction.
	*
	* @see #dismiss()
	*/
	public void show() {
		if (shown != 0)
			return;
		List<MenuItemImpl> items = new ArrayList<>();
		List<Integer> parents = new ArrayList<>();
		flatten(menu, -1, items, parents);
		String[] titles = new String[items.size()];
		int[] parent = new int[items.size()];
		int[] flags = new int[items.size()];
		for (int i = 0; i < titles.length; i++) {
			MenuItemImpl item = items.get(i);
			titles[i] = String.valueOf(item.title);
			parent[i] = parents.get(i);
			flags[i] = (item.enabled ? 1 : 0) | (item.checkable ? 2 : 0) | (item.checked ? 4 : 0) | (item.subMenu != null ? 8 : 0);
		}
		shown_items = items;
		shown = nativeShow(mAnchor.widget, titles, parent, flags);
		mContext.atl_process_app().open_menu = this;
	}

	public Menu getMenu() {
		return menu;
	}

	public void dismiss() {
		if (shown != 0)
			nativeClose(shown);
	}

	/**
	 * Interface responsible for receiving menu item click events if the items
	 * themselves do not have individual item click listeners.
	 */
	public interface OnMenuItemClickListener {
		/**
		 * This method will be invoked when a menu item is clicked if the item
		 * itself did not already handle the event.
		 *
		 * @param item the menu item that was clicked
		 * @return {@code true} if the event was handled, {@code false}
		 *         otherwise
		 */
		boolean onMenuItemClick(MenuItem item);
	}

	/**
	 * Callback interface used to notify the application that the menu has closed.
	 */
	public interface OnDismissListener {
		/**
		 * Called when the associated menu has been dismissed.
		 *
		 * @param menu the popup menu that was dismissed
		 */
		void onDismiss(PopupMenu menu);
	}

	private class MenuImpl implements Menu {
		List<MenuItemImpl> items = new ArrayList<>();

		@Override
		public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
			MenuItemImpl item = new MenuItemImpl(itemId, this, title, null);
			item.group = groupId;
			items.add(item);
			return item;
		}

		@Override
		public MenuItem add(int groupId, int itemId, int order, int titleRes) {
			return add(groupId, itemId, order, mContext.getString(titleRes));
		}

		@Override
		public MenuItem findItem(int id) {
			for (MenuItemImpl item : items) {
				if (item.id == id)
					return item;
				if (item.subMenu != null) {
					MenuItem found = item.subMenu.findItem(id);
					if (found != null)
						return found;
				}
			}
			return null;
		}

		@Override
		public MenuItem getItem(int index) {
			return items.get(index);
		}

		@Override
		public void clear() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'clear'");
		}

		@Override
		public void removeGroup(int groupId) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'removeGroup'");
		}

		@Override
		public SubMenu addSubMenu(int id) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'addSubMenu'");
		}

		@Override
		public MenuItem add(int id) {
			return add(0, 0, 0, id);
		}

		@Override
		public MenuItem add(CharSequence text) {
			return add(0, 0, 0, text);
		}

		@Override
		public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setGroupCheckable'");
		}

		@Override
		public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
			SubMenuImpl submenu = new SubMenuImpl(itemId, this, title);
			submenu.item.group = groupId;
			items.add(submenu.item);
			return submenu;
		}

		@Override
		public void setGroupVisible(int group, boolean visible) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setGroupVisible'");
		}

		@Override
		public void removeItem(int id) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'removeItem'");
		}

		@Override
		public int size() {
			return items.size();
		}

		@Override
		public boolean hasVisibleItems() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'hasVisibleItems'");
		}

		@Override
		public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
			return addSubMenu(groupId, itemId, order, mContext.getString(titleRes));
		}

		@Override
		public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller, Intent[] specifics, Intent intent, int flags, MenuItem[] outSpecificItems) {
			return 0;
		}

		@Override
		public void close() {
			dismiss();
		}

		/* Copyright (C) 2006 The Android Open Source Project: MenuBuilder.performIdentifierAction,
		 * without the submenu it would open */
		public boolean performIdentifierAction(int id, int flags) {
			MenuItemImpl item = (MenuItemImpl)findItem(id);
			if (item == null || !item.enabled)
				return false;
			boolean handled = item.click_listener != null && item.click_listener.onMenuItemClick(item);
			if (!handled && mMenuItemClickListener != null)
				handled = mMenuItemClickListener.onMenuItemClick(item);
			dismiss();
			return handled;
		}
	}

	private class SubMenuImpl extends MenuImpl implements SubMenu {
		private MenuItemImpl item;

		public SubMenuImpl(int id, MenuImpl parent, CharSequence title) {
			item = new MenuItemImpl(id, parent, title, this);
		}

		@Override
		public MenuItem getItem() {
			return item;
		}

		@Override
		public void clearHeader() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'clearHeader'");
		}

		@Override
		public SubMenu setIcon(Drawable icon) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setIcon'");
		}
	}

	private class MenuItemImpl implements MenuItem {
		private int id;
		int group;
		private MenuImpl parent;
		CharSequence title;
		SubMenuImpl subMenu;
		boolean visible = true, enabled = true, checkable, checked;
		OnMenuItemClickListener click_listener;

		private MenuItemImpl(int id, MenuImpl parent, CharSequence title, SubMenuImpl subMenu) {
			this.id = id;
			this.parent = parent;
			this.title = title;
			this.subMenu = subMenu;
		}

		@Override
		public MenuItem setIcon(int iconRes) {
			return this;
		}

		@Override
		public MenuItem setIcon(Drawable icon) {
			return this;
		}

		@Override
		public MenuItem setVisible(boolean visible) {
			this.visible = visible;
			return this;
		}

		@Override
		public MenuItem setChecked(boolean checked) {
			this.checked = checked;
			return this;
		}

		@Override
		public MenuItem setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		@Override
		public MenuItem setCheckable(boolean checkable) {
			this.checkable = checkable;
			return this;
		}

		@Override
		public boolean isCheckable() {
			return checkable;
		}

		@Override
		public MenuItem setTitleCondensed(CharSequence titleCondensed) {
			return this;
		}

		@Override
		public MenuItem setTitle(CharSequence title) {
			this.title = title;
			return this;
		}

		@Override
		public MenuItem setActionView(View actionView) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setActionView'");
		}

		@Override
		public void setShowAsAction(int action) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setShowAsAction'");
		}

		@Override
		public int getItemId() {
			return id;
		}

		@Override
		public int getGroupId() {
			return group;
		}

		@Override
		public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener) {
			click_listener = listener;
			return this;
		}

		@Override
		public MenuItem setTitle(int resId) {
			return setTitle(mContext.getString(resId));
		}

		@Override
		public boolean isVisible() {
			return visible;
		}

		@Override
		public Drawable getIcon() {
			return new Drawable();
		}

		@Override
		public SubMenu getSubMenu() {
			return subMenu;
		}

		@Override
		public MenuItem setActionView(int resId) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setActionView'");
		}

		@Override
		public View getActionView() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'getActionView'");
		}

		@Override
		public boolean hasSubMenu() {
			return subMenu != null;
		}

		@Override
		public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setOnActionExpandListener'");
		}

		@Override
		public boolean isChecked() {
			return checked;
		}

		@Override
		public MenuItem setShowAsActionFlags(int action) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setShowAsActionFlags'");
		}

		@Override
		public MenuItem setAlphabeticShortcut(char alphaChar) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setAlphabeticShortcut'");
		}

		@Override
		public MenuItem setShortcut(char numeric, char alpha) { return this; }

		@Override
		public int getOrder() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'getOrder'");
		}

		@Override
		public boolean isEnabled() {
			return enabled;
		}

		@Override
		public CharSequence getTitleCondensed() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'getTitleCondensed'");
		}

		@Override
		public CharSequence getTitle() {
			return title;
		}

		@Override
		public MenuItem setNumericShortcut(char numericChar) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setNumericShortcut'");
		}

		@Override
		public boolean expandActionView() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'expandActionView'");
		}

		@Override
		public boolean isActionViewExpanded() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'isActionViewExpanded'");
		}

		@Override
		public MenuItem setIntent(Intent intent) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setIntent'");
		}

		@Override
		public MenuItem setIconTintList(ColorStateList tint) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Unimplemented method 'setIconTintList'");
		}
	}
}
