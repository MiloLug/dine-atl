package dev.dine.framework.basics;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class ActionBarTest extends android.test.InstrumentationTestCase {
	private WindowActivity start() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		WindowActivity activity = (WindowActivity)getInstrumentation().startActivitySync(intent);
		assertNotNull(activity);
		return activity;
	}

	public void testBarKeepsWhatTheAppSets() {
		final WindowActivity activity = start();
		final Object[] got = new Object[12];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				ActionBar bar = activity.getActionBar();
				got[0] = bar;
				got[1] = activity.getActionBar();
				bar.setDisplayOptions(0);
				bar.setHomeButtonEnabled(true);
				bar.setDisplayHomeAsUpEnabled(true);
				got[2] = bar.getDisplayOptions();
				bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME, ActionBar.DISPLAY_SHOW_TITLE);
				got[3] = bar.getDisplayOptions();
				bar.setTitle("bar title");
				bar.setSubtitle("bar subtitle");
				got[4] = bar.getTitle();
				got[5] = bar.getSubtitle();
				activity.setTitle("window title");
				got[6] = bar.getTitle();
				bar.hide();
				got[7] = bar.isShowing();
				bar.show();
				got[8] = bar.isShowing();
				got[9] = bar.getHeight();
				got[10] = bar.getThemedContext();
				got[11] = bar.getNavigationMode();
			}
		});
		assertNotNull(got[0]);
		assertSame(got[0], got[1]);
		assertEquals(ActionBar.DISPLAY_HOME_AS_UP, got[2]);
		assertEquals(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE, got[3]);
		assertEquals("bar title", got[4].toString());
		assertEquals("bar subtitle", got[5].toString());
		assertEquals("window title", got[6].toString());
		assertEquals(false, got[7]);
		assertEquals(true, got[8]);
		assertEquals(0, got[9]);
		assertNotNull(got[10]);
		assertEquals(ActionBar.NAVIGATION_MODE_STANDARD, got[11]);
	}

	public void testTabsCallTheirListeners() {
		final WindowActivity activity = start();
		final List<String> events = new ArrayList<>();
		final Object[] got = new Object[6];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				ActionBar bar = activity.getActionBar();
				ActionBar.TabListener listener = new ActionBar.TabListener() {
					public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
						events.add("selected " + tab.getText() + (ft != null ? "" : " no transaction"));
					}
					public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
						events.add("unselected " + tab.getText());
					}
					public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
						events.add("reselected " + tab.getText());
					}
				};
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
				ActionBar.Tab a = bar.newTab().setText("a").setTabListener(listener);
				ActionBar.Tab b = bar.newTab().setText("b").setTabListener(listener);
				bar.addTab(a);
				bar.addTab(b);
				b.select();
				b.select();
				got[0] = bar.getSelectedNavigationIndex();
				got[1] = bar.getNavigationItemCount();
				bar.removeTabAt(1);
				got[2] = b.getPosition();
				got[3] = bar.getSelectedTab() == a;
				try {
					bar.addTab(bar.newTab().setText("c"));
					got[4] = "no exception";
				} catch (IllegalStateException e) {
					got[4] = "IllegalStateException";
				}
				got[5] = bar.getTabCount();
			}
		});
		assertEquals("[selected a, unselected a, selected b, reselected b, unselected b, selected a]", events.toString());
		assertEquals(1, got[0]);
		assertEquals(2, got[1]);
		assertEquals(ActionBar.Tab.INVALID_POSITION, got[2]);
		assertEquals(true, got[3]);
		assertEquals("IllegalStateException", got[4]);
		assertEquals(1, got[5]);
	}

	public void testListNavigationReportsItsItems() {
		final WindowActivity activity = start();
		final List<String> events = new ArrayList<>();
		final Object[] got = new Object[4];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				ActionBar bar = activity.getActionBar();
				ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new String[] {"x", "y", "z"});
				bar.setListNavigationCallbacks(adapter, new ActionBar.OnNavigationListener() {
					public boolean onNavigationItemSelected(int position, long id) {
						events.add(position + "/" + id);
						return true;
					}
				});
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
				bar.setSelectedNavigationItem(2);
				got[0] = bar.getSelectedNavigationIndex();
				got[1] = bar.getNavigationItemCount();
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
				try {
					bar.setSelectedNavigationItem(0);
					got[2] = "no exception";
				} catch (IllegalStateException e) {
					got[2] = "IllegalStateException";
				}
				got[3] = bar.getSelectedNavigationIndex();
			}
		});
		assertEquals("[0/0, 2/2]", events.toString());
		assertEquals(2, got[0]);
		assertEquals(3, got[1]);
		assertEquals("IllegalStateException", got[2]);
		assertEquals(-1, got[3]);
	}

	public void testLayoutParams() {
		ActionBar.LayoutParams params = new ActionBar.LayoutParams(Gravity.RIGHT);
		assertEquals(Gravity.RIGHT, params.gravity);
		assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.width);
		assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height);
		assertEquals(Gravity.CENTER_VERTICAL | Gravity.START, new ActionBar.LayoutParams(10, 20).gravity);
	}
}
