package android.graphics;

/* The region's rects as they were when the iterator was made, as SkRegion::Iterator's copy. */
public class RegionIterator {
	private final int[] rects;
	private int next;

	public RegionIterator(Region region) {
		rects = region.atlRects();
	}

	public final boolean next(Rect r) {
		if (r == null)
			throw new NullPointerException("The Rect must be provided");
		if (next >= rects.length)
			return false;
		r.set(rects[next], rects[next + 1], rects[next + 2], rects[next + 3]);
		next += 4;
		return true;
	}
}
