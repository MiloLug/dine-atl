package android.graphics;

import android.atl.ATLCanvas;
import android.graphics.drawable.Drawable;
import android.view.DisplayListCanvas;
import android.view.RenderNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* A recording of Canvas calls, replayed on any canvas: sharp at any scale, its text and blend modes
 * kept. Each call keeps copies of what it was given (a mutable bitmap too, as SkPicture's images
 * do); a drawable replays as it draws then, at the bounds it had. */
public class Picture {
	private interface Op {
		void replay(Canvas canvas);
	}

	private static Paint copy(Paint paint) {
		return paint != null ? new Paint(paint) : null;
	}

	/* Every call that reaches a gfx canvas goes through one of ATLCanvas's methods, so overriding
	 * them records whatever another canvas would draw; handle 0 keeps only the Java state. */
	private static class Recorder extends ATLCanvas {
		final List<Op> ops = new ArrayList<>();
		boolean ended;

		Recorder(int width, int height) {
			super(0, width, height);
		}

		private void add(Op op) {
			if (!ended)
				ops.add(op);
		}

		@Override
		public int save() {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.save();
				}
			});
			return super.save();
		}

		@Override
		public int atlSaveLayer(RectF bounds, final int alpha, final int mode) {
			final RectF b = bounds != null ? new RectF(bounds) : null;
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.atlSaveLayer(b, alpha, mode);
				}
			});
			return super.atlSaveLayer(bounds, alpha, mode);
		}

		@Override
		public void restore() {
			super.restore(); // throws for a restore without a save, before it is kept
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.restore();
				}
			});
		}

		@Override
		public void translate(final float dx, final float dy) {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.translate(dx, dy);
				}
			});
			super.translate(dx, dy);
		}

		@Override
		public void rotate(final float degrees) {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.rotate(degrees);
				}
			});
			super.rotate(degrees);
		}

		@Override
		public void scale(final float sx, final float sy) {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.scale(sx, sy);
				}
			});
			super.scale(sx, sy);
		}

		@Override
		public void concat(Matrix matrix) {
			if (matrix == null)
				return;
			final Matrix m = new Matrix(matrix);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.concat(m);
				}
			});
			super.concat(matrix);
		}

		@Override
		public boolean clipRect(final float left, final float top, final float right, final float bottom, final Region.Op op) {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.clipRect(left, top, right, bottom, op);
				}
			});
			return super.clipRect(left, top, right, bottom, op);
		}

		@Override
		public boolean clipPath(Path path, final Region.Op op) {
			final Path p = new Path(path);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.clipPath(p, op);
				}
			});
			return super.clipPath(path, op);
		}

		@Override
		public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
			final Bitmap b = bitmap.isMutable() ? bitmap.copy(bitmap.getConfig(), false) : bitmap;
			final Rect s = src != null ? new Rect(src) : null, d = new Rect(dst);
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawBitmap(b, s, d, p);
				}
			});
		}

		@Override
		public void drawPath(Path path, Paint paint) {
			if (path == null)
				return;
			final Path copy = new Path(path);
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawPath(copy, p);
				}
			});
		}

		@Override
		public void drawArc(final float left, final float top, final float right, final float bottom, final float startAngle, final float sweepAngle, final boolean useCenter, Paint paint) {
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, p);
				}
			});
		}

		@Override
		public void drawRect(final float left, final float top, final float right, final float bottom, Paint paint) {
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawRect(left, top, right, bottom, p);
				}
			});
		}

		@Override
		public void drawText(final String text, final float x, final float y, Paint paint) {
			if (text == null)
				return;
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawText(text, x, y, p);
				}
			});
		}

		@Override
		public void drawLine(final float startX, final float startY, final float stopX, final float stopY, Paint paint) {
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawLine(startX, startY, stopX, stopY, p);
				}
			});
		}

		@Override
		public void drawLines(float[] points, int offset, int count, Paint paint) {
			if (offset + count < 0 /* overflow */ || offset + count > points.length)
				throw new ArrayIndexOutOfBoundsException();
			final float[] pts = Arrays.copyOfRange(points, offset, offset + count);
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawLines(pts, 0, pts.length, p);
				}
			});
		}

		@Override
		public void drawRoundRect(final float left, final float top, final float right, final float bottom, final float rx, final float ry, Paint paint) {
			final Paint p = copy(paint);
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.drawRoundRect(left, top, right, bottom, rx, ry, p);
				}
			});
		}

		@Override
		public void drawRenderNode(final RenderNode node) {
			add(new Op() {
				public void replay(Canvas canvas) {
					if (canvas instanceof DisplayListCanvas)
						((DisplayListCanvas)canvas).drawRenderNode(node);
				}
			});
		}

		@Override
		public void atlDrawDrawable(final Drawable drawable, final int width, final int height) {
			add(new Op() {
				public void replay(Canvas canvas) {
					canvas.atlDrawDrawable(drawable, width, height);
				}
			});
		}
	}

	private Recorder recorder;
	private List<Op> ops = new ArrayList<>();
	private int width;
	private int height;

	public Picture() {}

	public Picture(Picture src) {
		src.endRecording();
		ops = src.ops;
		width = src.width;
		height = src.height;
	}

	public Canvas beginRecording(int width, int height) {
		this.width = width;
		this.height = height;
		recorder = new Recorder(width, height);
		return new Canvas(recorder, width, height);
	}

	public void endRecording() {
		if (recorder != null) {
			recorder.ended = true;
			ops = recorder.ops;
			recorder = null;
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean requiresHardwareAcceleration() {
		return false;
	}

	public void draw(Canvas canvas) {
		endRecording();
		int count = canvas.save();
		for (Op op : ops)
			op.replay(canvas);
		canvas.restoreToCount(count);
	}
}
