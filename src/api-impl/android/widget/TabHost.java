package android.widget;

import android.view.View;

public class TabHost extends View {
	public TabHost(android.content.Context context) {
		this(context, null);
	}

	public TabHost(android.content.Context context, android.util.AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TabHost(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public TabHost(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public static class TabSpec {
		private String tag;
		public TabSpec(String tag) {
			this.tag = tag;
		}

		public TabSpec setIndicator(CharSequence label) {
			return this;
		}

		public TabSpec setIndicator(CharSequence label, android.graphics.drawable.Drawable icon) {
			return this;
		}

		public TabSpec setContent(android.content.Intent intent) {
			return this;
		}

		public TabSpec setContent(android.widget.TabHost.TabContentFactory factory) {
			return this;
		}
	}

	public interface TabContentFactory {
		View createTabContent(String tag);
	}

	public TabSpec newTabSpec(String tag) {
		return new TabSpec(tag);
	}

	public void setup() {}

	public void addTab(TabSpec tabSpec) {}

	public void setCurrentTab(int index) {}

	public void setCurrentTabByTag(String tag) {}

	public void setOnTabChangedListener(OnTabChangeListener listener) {}

	public interface OnTabChangeListener {
		void onTabChanged(String tabId);
	}
}
