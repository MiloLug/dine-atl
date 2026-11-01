package android.graphics;

import android.annotation.UnsupportedAppUsage;
import android.util.Log;

/*
 * Path is a native struct gfx_path (src/shared/path.h). Copies and display lists share it; an edit
 * of a shared one edits a copy, so every edit native returns the path to keep.
 */
public class Path {

	public enum FillType {
		WINDING(false, false),
		EVEN_ODD(true, false),
		INVERSE_WINDING(false, true),
		INVERSE_EVEN_ODD(true, true);

		public final boolean even_odd;
		final boolean inverse;

		private FillType(boolean even_odd, boolean inverse) {
			this.even_odd = even_odd;
			this.inverse = inverse;
		}
	}

	public enum Direction {
		CW,
		CCW,
	}

	public enum Op {
		DIFFERENCE(Region.Op.DIFFERENCE),
		INTERSECT(Region.Op.INTERSECT),
		UNION(Region.Op.UNION),
		XOR(Region.Op.XOR),
		REVERSE_DIFFERENCE(Region.Op.REVERSE_DIFFERENCE);

		private Region.Op region_op;

		private Op(Region.Op region_op) {
			this.region_op = region_op;
		}
	}

	private long path;
	private FillType fillType = FillType.WINDING;

	public Path() {
		path = native_new();
	}

	public Path(Path src) {
		if (src == null) {
			path = native_new();
			return;
		}
		path = native_ref(src.path);
		fillType = src.fillType;
	}

	@UnsupportedAppUsage
	public long getNativePath() {
		return path;
	}

	public void reset() {
		path = native_reset(path);
	}

	public void rewind() {
		path = native_reset(path);
	}

	public void set(Path src) {
		if (src == this)
			return;
		path = native_add_path(native_reset(path), src.path, 0);
		fillType = src.fillType;
	}

	public void close() {
		path = native_close(path);
	}

	public void setFillType(FillType fillType) {
		this.fillType = fillType;
	}

	public FillType getFillType() {
		return fillType;
	}

	public boolean isInverseFillType() {
		return fillType.inverse;
	}

	public void toggleInverseFillType() {
		fillType = FillType.values()[fillType.ordinal() ^ 2];
	}

	public void moveTo(float x, float y) {
		path = native_move_to(path, x, y, false);
	}

	public void rMoveTo(float dx, float dy) {
		path = native_move_to(path, dx, dy, true);
	}

	public void lineTo(float x, float y) {
		path = native_line_to(path, x, y, false);
	}

	public void rLineTo(float dx, float dy) {
		path = native_line_to(path, dx, dy, true);
	}

	public void quadTo(float x1, float y1, float x2, float y2) {
		path = native_quad_to(path, x1, y1, x2, y2, false);
	}

	public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
		path = native_quad_to(path, dx1, dy1, dx2, dy2, true);
	}

	public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
		path = native_cubic_to(path, x1, y1, x2, y2, x3, y3, false);
	}

	public void rCubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
		path = native_cubic_to(path, x1, y1, x2, y2, x3, y3, true);
	}

	public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
		arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, forceMoveTo);
	}

	public void arcTo(RectF oval, float startAngle, float sweepAngle) {
		arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, false);
	}

	public void arcTo(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean forceMoveTo) {
		path = native_arc_to(path, left, top, right, bottom, startAngle, sweepAngle, forceMoveTo);
	}

	public void addArc(RectF oval, float startAngle, float sweepAngle) {
		addArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle);
	}

	public void addArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle) {
		path = native_add_arc(path, left, top, right, bottom, startAngle, sweepAngle);
	}

	public void addPath(Path src, Matrix matrix) {
		path = native_add_path(path, src.path, matrix != null ? matrix.ni() : 0);
	}

	public void addPath(Path src, float dx, float dy) {
		Matrix matrix = new Matrix();
		matrix.setTranslate(dx, dy);
		addPath(src, matrix);
	}

	public void addPath(Path src) {
		path = native_add_path(path, src.path, 0);
	}

	public void addRect(RectF rect, Direction direction) {
		addRect(rect.left, rect.top, rect.right, rect.bottom, direction);
	}

	public void addRect(float left, float top, float right, float bottom, Direction direction) {
		path = native_add_rect(path, left, top, right, bottom, direction == Direction.CCW);
	}

	public void addRoundRect(float left, float top, float right, float bottom, float[] radii, Direction direction) {
		if (radii.length < 8)
			throw new ArrayIndexOutOfBoundsException("radii[] needs 8 values");
		path = native_add_round_rect(path, left, top, right, bottom, radii, direction == Direction.CCW);
	}

	public void addRoundRect(RectF rect, float[] radii, Direction direction) {
		addRoundRect(rect.left, rect.top, rect.right, rect.bottom, radii, direction);
	}

	public void addRoundRect(RectF rect, float rx, float ry, Direction direction) {
		addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, direction);
	}

	public void addRoundRect(float left, float top, float right, float bottom, float rx, float ry, Direction direction) {
		addRoundRect(left, top, right, bottom, new float[] {rx, ry, rx, ry, rx, ry, rx, ry}, direction);
	}

	public void addOval(float left, float top, float right, float bottom, Direction direction) {
		path = native_add_oval(path, left, top, right, bottom, direction == Direction.CCW);
	}

	public void addOval(RectF rect, Direction direction) {
		addOval(rect.left, rect.top, rect.right, rect.bottom, direction);
	}

	public void addCircle(float x, float y, float radius, Direction direction) {
		addOval(x - radius, y - radius, x + radius, y + radius, direction);
	}

	public void transform(Matrix matrix) {
		path = native_transform(path, matrix.ni());
	}

	public void transform(Matrix matrix, Path dst) {
		if (dst == null || dst == this) {
			transform(matrix);
			return;
		}
		dst.set(this);
		dst.transform(matrix);
	}

	public void offset(float dx, float dy) {
		Matrix matrix = new Matrix();
		matrix.setTranslate(dx, dy);
		transform(matrix);
	}

	public void offset(float dx, float dy, Path dst) {
		Matrix matrix = new Matrix();
		matrix.setTranslate(dx, dy);
		transform(matrix, dst);
	}

	public void setLastPoint(float x, float y) {
		path = native_set_last_point(path, x, y);
	}

	// exact is unused, as on Android: the bounds of the control points
	public void computeBounds(RectF bounds, boolean exact) {
		native_get_bounds(path, bounds);
	}

	public boolean isEmpty() {
		return native_is_empty(path);
	}

	public boolean isRect(RectF rect) {
		return native_is_rect(path, rect != null ? rect : new RectF());
	}

	public boolean op(Path path, Op op) {
		return op(this, path, op);
	}

	public boolean op(Path path1, Path path2, Op op) {
		RectF bounds1 = new RectF();
		RectF bounds2 = new RectF();
		path1.computeBounds(bounds1, false);
		path2.computeBounds(bounds2, false);
		// special case for composeUI ShapeContainingUtil.isInPath which puts a tiny rectangle in path2 to represent a point
		if (op == Op.INTERSECT && bounds2.width() < 1 && bounds2.height() < 1) {
			if (path1.contains(bounds2.centerX(), bounds2.centerY()))
				set(path2);
			else
				reset();
			return true;
		} else if (op == Op.INTERSECT && bounds1.width() < 1 && bounds1.height() < 1) {
			if (path2.contains(bounds1.centerX(), bounds1.centerY()))
				set(path1);
			else
				reset();
			return true;
		}
		Region region1 = new Region();
		Region region2 = new Region();
		region1.setPath(path1, null);
		region2.setPath(path2, null);
		region1.op(region2, op.region_op);
		region1.getBoundaryPath(this);
		return true;
	}

	private boolean contains(float x, float y) {
		return native_contains(path, fillType.even_odd, x, y) ^ fillType.inverse;
	}

	public void incReserve(int additionalPoints) {}

	public boolean isConvex() {
		Log.w("Path", "STUB: isConvex");
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable {
		try {
			native_unref(path);
		} finally {
			super.finalize();
		}
	}

	private static native long native_new();
	private static native long native_ref(long path);
	private static native void native_unref(long path);
	private static native long native_reset(long path);
	private static native long native_close(long path);
	private static native long native_move_to(long path, float x, float y, boolean relative);
	private static native long native_line_to(long path, float x, float y, boolean relative);
	private static native long native_quad_to(long path, float x1, float y1, float x2, float y2, boolean relative);
	private static native long native_cubic_to(long path, float x1, float y1, float x2, float y2, float x3, float y3, boolean relative);
	private static native long native_arc_to(long path, float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean forceMoveTo);
	private static native long native_add_arc(long path, float left, float top, float right, float bottom, float startAngle, float sweepAngle);
	private static native long native_add_path(long path, long src, long matrix);
	private static native long native_add_rect(long path, float left, float top, float right, float bottom, boolean ccw);
	private static native long native_add_oval(long path, float left, float top, float right, float bottom, boolean ccw);
	private static native long native_add_round_rect(long path, float left, float top, float right, float bottom, float[] radii, boolean ccw);
	private static native long native_transform(long path, long matrix);
	private static native long native_set_last_point(long path, float x, float y);
	private static native void native_get_bounds(long path, RectF rect);
	private static native boolean native_contains(long path, boolean even_odd, float x, float y);
	private static native boolean native_is_empty(long path);
	private static native boolean native_is_rect(long path, RectF rect);
}
