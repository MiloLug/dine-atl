package dev.dine.framework.windows;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

// AlertDialog's content: Builder's view, choice lists and adapter, ProgressDialog
@SuppressWarnings("deprecation")
public class AlertContentTest extends android.test.InstrumentationTestCase {
	private PlainActivity activity;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Intent intent = new Intent(getInstrumentation().getTargetContext(), PlainActivity.class);
		activity = (PlainActivity)getInstrumentation().startActivitySync(intent);
	}

	private interface Make {
		AlertDialog make();
	}

	private AlertDialog shown(final Make make) {
		final AlertDialog[] dialog = new AlertDialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = make.make();
				dialog[0].show();
			}
		});
		getInstrumentation().waitForIdleSync();
		return dialog[0];
	}

	private void dismiss(final AlertDialog dialog) {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.dismiss();
			}
		});
	}

	// the window's layout pass runs after idle on records: poll as CTS's PollingCheck does
	private static void waitForLayout(View view) throws InterruptedException {
		for (int i = 0; i < 250 && view.getWidth() == 0; i++)
			Thread.sleep(20);
	}

	private TextView text(String text) {
		TextView view = new TextView(activity);
		view.setText(text);
		return view;
	}

	// Builder.setView's view is the alert's content, set before or after create
	public void testViewIsLaidOutInTheAlert() throws InterruptedException {
		final TextView content = text("content");
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				AlertDialog made = new AlertDialog.Builder(activity).setTitle("t").setPositiveButton("OK", null).create();
				made.setView(content);
				return made;
			}
		});
		waitForLayout(content);
		assertTrue("attached", content.isAttachedToWindow());
		assertTrue("laid out " + content.getWidth() + "x" + content.getHeight(), content.getWidth() > 0 && content.getHeight() > 0);
		dismiss(dialog);
	}

	// onCreate installs the content once: a dialog shown again keeps it, as AOSP's dispatchOnCreate
	public void testShownAgainKeepsItsView() throws InterruptedException {
		final TextView content = text("again");
		final AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity).setTitle("again").setView(content).create();
			}
		});
		final android.view.ViewParent[] parent = new android.view.ViewParent[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				parent[0] = content.getParent();
				dialog.dismiss();
			}
		});
		getInstrumentation().waitForIdleSync();
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog.show();
			}
		});
		getInstrumentation().waitForIdleSync();
		assertSame("the same parent", parent[0], content.getParent());
		assertTrue("attached", content.isAttachedToWindow());
		dismiss(dialog);
	}

	// the panel sets the width: the content stays on the screen, whatever the theme's least width
	public void testContentFitsTheScreen() throws InterruptedException {
		final TextView content = text("a line of content");
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity).setTitle("wide").setView(content).create();
			}
		});
		waitForLayout(content);
		int[] location = new int[2];
		content.getLocationOnScreen(location);
		int screen = activity.getWindow().getDecorView().getWidth();
		assertTrue("left " + location[0], location[0] > 0);
		assertTrue("right " + (location[0] + content.getWidth()) + " of " + screen, location[0] + content.getWidth() < screen);
		dismiss(dialog);
	}

	// rows past the screen scroll: the content under them stays on it
	public void testLongListKeepsTheContentOnScreen() throws InterruptedException {
		final CharSequence[] items = new CharSequence[60];
		for (int i = 0; i < items.length; i++)
			items[i] = "row " + i;
		final TextView content = text("under the rows");
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity).setTitle("long").setSingleChoiceItems(items, 0, null)
				    .setView(content).setPositiveButton("OK", null).create();
			}
		});
		waitForLayout(content);
		int[] location = new int[2];
		content.getLocationOnScreen(location);
		int screen = activity.getWindow().getDecorView().getHeight();
		assertTrue("height " + content.getHeight(), content.getHeight() > 0);
		assertTrue("top " + location[1], location[1] >= 0);
		assertTrue("bottom " + (location[1] + content.getHeight()) + " of " + screen, location[1] + content.getHeight() <= screen);
		dismiss(dialog);
	}

	public void testSingleChoiceList() {
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity)
				    .setSingleChoiceItems(new CharSequence[] {"a", "b", "c"}, 1, null)
				    .create();
			}
		});
		ListView list = dialog.getListView();
		assertNotNull(list);
		assertEquals(3, list.getCount());
		assertEquals(1, list.getCheckedItemPosition());
		dismiss(dialog);
	}

	public void testMultiChoiceList() {
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity)
				    .setMultiChoiceItems(new CharSequence[] {"a", "b", "c"}, new boolean[] {true, false, true},
				                         (DialogInterface.OnMultiChoiceClickListener)null)
				    .create();
			}
		});
		SparseBooleanArray checked = dialog.getListView().getCheckedItemPositions();
		assertTrue(checked.get(0));
		assertFalse(checked.get(1));
		assertTrue(checked.get(2));
		dismiss(dialog);
	}

	// setAdapter's rows are Android's: its ListView is in the alert
	public void testAdapterList() throws InterruptedException {
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, new String[] {"x", "y"});
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity).setTitle("list").setAdapter(adapter, null).create();
			}
		});
		ListView list = dialog.getListView();
		assertNotNull(list);
		assertSame(adapter, list.getAdapter());
		waitForLayout(list);
		assertTrue("attached", list.isAttachedToWindow());
		dismiss(dialog);
	}

	public void testCustomTitleIsContent() {
		final TextView title = text("custom");
		AlertDialog dialog = shown(new Make() {
			public AlertDialog make() {
				return new AlertDialog.Builder(activity).setCustomTitle(title).setMessage("m").create();
			}
		});
		assertTrue("attached", title.isAttachedToWindow());
		dismiss(dialog);
	}

	public void testProgressDialog() {
		final ProgressDialog[] dialog = new ProgressDialog[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				dialog[0] = ProgressDialog.show(activity, "t", "m", true);
				dialog[0].setMax(4);
				dialog[0].setProgress(3);
				dialog[0].incrementProgressBy(5);
			}
		});
		assertTrue(dialog[0].isShowing());
		assertTrue(dialog[0].isIndeterminate());
		assertEquals(4, dialog[0].getMax());
		assertEquals(4, dialog[0].getProgress());
		dismiss(dialog[0]);
		getInstrumentation().waitForIdleSync();
		assertFalse(dialog[0].isShowing());
	}
}
