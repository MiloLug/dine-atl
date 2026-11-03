package android.graphics.drawable;

/* A StateListDrawable: its <transition> elements are skipped, so a state change switches at once. */
public class AnimatedStateListDrawable extends StateListDrawable {
	public AnimatedStateListDrawable() {
		super();
	}

	public void addState(int[] stateSet, Drawable drawable, int id) {
		addState(stateSet, drawable);
	}

	public <T extends Drawable & Animatable> void addTransition(int fromId, int toId, T transition, boolean reversible) {}
}
