package android.app;

import android.content.Context;
import android.content.res.Configuration;

public class UiModeManager {
	private final Context context;

	public UiModeManager(Context context) {
		this.context = context;
	}

	public int getCurrentModeType() {
		return context.get_atl_loaded_app().config.uiMode & Configuration.UI_MODE_TYPE_MASK;
	}

	public int getNightMode() {
		return context.get_atl_loaded_app().config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
	}
}
