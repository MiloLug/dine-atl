package dev.dine.framework.windows;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.Button;
import android.widget.PopupMenu;

// PopupMenu's items and listeners over the ui slot's native menu
public class PopupMenuTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;
	private PopupMenu popup;
	private final StringBuilder events = new StringBuilder();

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				Button anchor = new Button(activity);
				anchor.setText("anchor");
				activity.setContentView(anchor);
				popup = new PopupMenu(activity, anchor);
				Menu menu = popup.getMenu();
				menu.add(0, 1, 0, "Copy");
				menu.add(0, 2, 0, "Paste").setEnabled(false);
				menu.add(0, 3, 0, "Bold").setCheckable(true).setChecked(true);
				SubMenu more = menu.addSubMenu(0, 4, 0, "More");
				more.add(0, 5, 0, "Deep").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						events.append("item listener " + item.getTitle() + ";");
						return true;
					}
				});
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						events.append("menu listener " + item.getTitle() + ";");
						return true;
					}
				});
				popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
					public void onDismiss(PopupMenu menu) {
						events.append("dismissed;");
					}
				});
			}
		});
	}

	private void onMain(Runnable runnable) {
		getInstrumentation().runOnMainSync(runnable);
		getInstrumentation().waitForIdleSync();
	}

	public void testItemsKeepTheirState() {
		Menu menu = popup.getMenu();
		assertFalse(menu.findItem(2).isEnabled());
		assertTrue(menu.findItem(3).isCheckable());
		assertTrue(menu.findItem(3).isChecked());
		assertTrue(menu.findItem(4).hasSubMenu());
		assertEquals("Deep", String.valueOf(menu.findItem(5).getTitle()));
	}

	public void testShownMenuDismissedReachesItsListener() {
		onMain(new Runnable() {
			public void run() {
				popup.show();
				popup.dismiss();
			}
		});
		assertEquals("dismissed;", events.toString());
	}

	public void testPickedItemGoesToTheMenuListener() {
		onMain(new Runnable() {
			public void run() {
				popup.getMenu().performIdentifierAction(1, 0);
			}
		});
		assertEquals("menu listener Copy;", events.toString());
	}

	public void testItemListenerComesFirst() {
		onMain(new Runnable() {
			public void run() {
				popup.getMenu().performIdentifierAction(5, 0);
			}
		});
		assertEquals("item listener Deep;", events.toString());
	}

	public void testDisabledItemDoesNothing() {
		onMain(new Runnable() {
			public void run() {
				assertFalse(popup.getMenu().performIdentifierAction(2, 0));
			}
		});
		assertEquals("", events.toString());
	}
}
