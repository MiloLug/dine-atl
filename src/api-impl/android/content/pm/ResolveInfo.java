package android.content.pm;

import android.content.IntentFilter;
import android.graphics.drawable.Drawable;

public class ResolveInfo {
	public ActivityInfo activityInfo = new ActivityInfo();
	public ServiceInfo serviceInfo = new ServiceInfo();
	public ProviderInfo providerInfo;
	public IntentFilter filter = new IntentFilter();
	public int priority = -500;

	private ComponentInfo componentInfo() {
		return activityInfo != null ? activityInfo : serviceInfo != null ? serviceInfo : providerInfo;
	}

	public Drawable loadIcon(PackageManager pm) {
		Drawable icon = componentInfo().loadIcon(pm);
		if (icon == null) {
			icon = new Drawable();
		}
		return icon;
	}

	public CharSequence loadLabel(PackageManager pm) {
		CharSequence label = componentInfo().loadLabel(pm);
		if (label == null) {
			label = "fixme ResolveInfo.loadLabel";
		}
		return label;
	}

	public static class DisplayNameComparator {

		public DisplayNameComparator(PackageManager pm) {}
	}
}
