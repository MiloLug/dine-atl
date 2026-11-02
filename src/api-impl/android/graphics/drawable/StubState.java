package android.graphics.drawable;

import android.content.res.Resources;
import java.util.function.Supplier;

// the state of a drawable that keeps nothing to share: a copy is a new one like it
final class StubState extends Drawable.ConstantState {
	private final Supplier<Drawable> factory;

	StubState(Supplier<Drawable> factory) {
		this.factory = factory;
	}

	@Override
	public Drawable newDrawable() {
		return factory.get();
	}

	@Override
	public Drawable newDrawable(Resources res) {
		return factory.get();
	}

	@Override
	public int getChangingConfigurations() {
		return 0;
	}
}
