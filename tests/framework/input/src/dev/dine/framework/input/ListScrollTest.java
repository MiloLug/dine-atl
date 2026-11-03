package dev.dine.framework.input;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// a scroll moves the rows: none is laid out again, as on Android
@SuppressWarnings("deprecation")
public class ListScrollTest extends android.test.InstrumentationTestCase {
	private static class Row extends FrameLayout {
		int layouts;

		Row(Context context) {
			super(context);
			addView(new TextView(context));
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			super.onLayout(changed, l, t, r, b);
			layouts++;
		}
	}

	private static class Rows extends BaseAdapter {
		private final Context context;

		Rows(Context context) {
			this.context = context;
		}

		public int getCount() {
			return 50;
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			Row row = convertView instanceof Row ? (Row)convertView : new Row(context);
			row.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, 20));
			((TextView)row.getChildAt(0)).setText("item " + position);
			return row;
		}
	}

	private void onMain(Runnable runnable) {
		Input.onMain(getInstrumentation(), runnable);
	}

	private static void lay_out(AbsListView list) {
		int exactly100 = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY);
		list.measure(exactly100, exactly100);
		list.layout(0, 0, 100, 100);
	}

	public void testListViewMovesItsRows() {
		final Context context = getInstrumentation().getTargetContext();
		final int[] got = new int[5];
		final List<Integer> relayouts = new ArrayList<>();
		onMain(new Runnable() {
			public void run() {
				ListView list = new ListView(context);
				list.setAdapter(new Rows(context));
				lay_out(list);
				got[4] = list.getChildAt(1).getTop() - list.getChildAt(0).getTop(); // a row and its divider
				Row first = (Row)list.getChildAt(1);
				int before = first.layouts;
				int top = first.getTop();
				list.scrollListBy(10);
				got[0] = first.getTop() - top;
				relayouts.add(first.layouts - before);
				list.scrollListBy(50);
				got[1] = list.getFirstVisiblePosition();
				got[2] = list.getChildCount();
				got[3] = list.getChildAt(0).getBottom() > 0 ? 1 : 0;
			}
		});
		assertEquals(-10, got[0]);
		assertEquals("[0]", relayouts.toString());
		assertEquals((60 - 1) / got[4], got[1]); // the rows wholly above the top, as AOSP detaches them
		assertTrue("rows " + got[2], got[2] >= 5);
		assertEquals(1, got[3]);
	}

	// a scroll moves the rows in AbsListView; a selection near the end, with offsetChildrenTopAndBottom
	public void testGridViewMovesItsRows() {
		final Context context = getInstrumentation().getTargetContext();
		final int[] got = new int[5];
		onMain(new Runnable() {
			public void run() {
				GridView grid = new GridView(context);
				grid.setNumColumns(2);
				grid.setAdapter(new Rows(context));
				lay_out(grid);
				got[2] = grid.getChildAt(2).getTop() - grid.getChildAt(0).getTop(); // a row and its spacing
				View first = grid.getChildAt(0);
				int top = first.getTop();
				grid.scrollListBy(10);
				got[0] = first.getTop() - top;
				grid.scrollListBy(50);
				got[1] = grid.getFirstVisiblePosition();
				grid.setSelection(49);
				lay_out(grid);
				got[3] = grid.getChildAt(grid.getChildCount() - 1).getBottom();
				got[4] = grid.getHeight() - grid.getListPaddingBottom();
			}
		});
		assertEquals(-10, got[0]);
		assertEquals((60 - 1) / got[2] * 2, got[1]);
		assertEquals("the last row ends at the bottom", got[4], got[3]);
	}
}
