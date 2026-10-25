package android.atl;

import android.graphics.*;
import android.view.DisplayListCanvas;
import android.view.RenderNode;
import java.util.Arrays;
import java.util.Stack;

/**
 * A Canvas over a gfx-slot canvas: handle is a struct gfx_canvas (gsk: a GtkSnapshot; record: a
 * display list).
 */
public class ATLCanvas extends DisplayListCanvas {
	public long handle;
	private int[] push_history = null;
	private Stack<Matrix> state_stack = new Stack<>();

	private static Paint default_paint = new Paint();

	public ATLCanvas(long handle) {
		this.handle = handle;
		state_stack.push(new Matrix());
	}

	@Override
	public int save() {
		native_save(handle);
		state_stack.push(new Matrix(state_stack.peek()));
		return getSaveCount() - 1;
	}

	@Override
	public void restore() {
		int save_count = getSaveCount();
		if (save_count <= 1)
			throw new IllegalStateException("No more saves to restore");
		if (push_history != null && push_history.length > save_count && push_history[save_count] > 0) {
			native_pop(handle, push_history[save_count]);
			push_history[save_count] = 0;
		}
		state_stack.pop();
		native_restore(handle);
	}

	@Override
	public int getSaveCount() {
		return state_stack.size();
	}

	@Override
	public void translate(float dx, float dy) {
		native_translate(handle, dx, dy);
		state_stack.peek().preTranslate(dx, dy);
	}

	@Override
	public void rotate(float degrees) {
		native_rotate(handle, degrees);
		state_stack.peek().preRotate(degrees);
	}

	@Override
	public void scale(float sx, float sy) {
		native_scale(handle, sx, sy);
		state_stack.peek().preScale(sx, sy);
	}

	@Override
	public void concat(Matrix matrix) {
		if (matrix != null) {
			native_concat(handle, matrix.native_instance);
			state_stack.peek().preConcat(matrix);
		}
	}

	@Override
	public void getMatrix(Matrix matrix) {
		matrix.set(state_stack.peek());
	}

	@Override
	public boolean clipRect(float left, float top, float right, float bottom) {
		native_clipRect(handle, left, top, right, bottom);
		int save_count = getSaveCount();
		if (push_history == null)
			push_history = new int[save_count + 1];
		else if (push_history.length <= save_count)
			push_history = Arrays.copyOf(push_history, save_count + 1);
		push_history[save_count]++;
		return right > left && bottom > top;
	}

	@Override
	public boolean clipPath(Path path) {
		native_clipPath(handle, path.getGskPath(), path.getFillType().fill_rule);
		int save_count = getSaveCount();
		if (push_history == null)
			push_history = new int[save_count + 1];
		else if (push_history.length <= save_count)
			push_history = Arrays.copyOf(push_history, save_count + 1);
		push_history[save_count]++;
		RectF bounds = new RectF();
		path.computeBounds(bounds, true);
		return !bounds.isEmpty();
	}

	@Override
	public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
		if (src == null)
			native_drawBitmap(handle, bitmap.getTexture(), dst.left, dst.top, dst.width(), dst.height(), paint != null ? paint.paint : default_paint.paint);
		else
			native_drawBitmap(handle, bitmap.getTexture(), dst.left, dst.top, dst.width(), dst.height(), src.left, src.top, src.width(), src.height(), paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawPath(Path path, Paint paint) {
		if (path != null)
			native_drawPath(handle, path.getGskPath(), path.getFillType().fill_rule, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawRect(float left, float top, float right, float bottom, Paint paint) {
		// TODO: implement xfermode using gtk_snapshot_push_composite in GTK >= 4.22
		if (paint != null && paint.getXfermode() != null)
			return;
		native_drawRect(handle, left, top, right, bottom, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawText(String text, float x, float y, Paint paint) {
		if (text == null) {
			new Exception("drawText: text is null; stack trace:").printStackTrace();
			return;
		}
		native_drawText(handle, text, x, y, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
		native_drawLine(handle, startX, startY, stopX, stopY, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawLines(float[] points, int offset, int count, Paint paint) {
		if (offset + count < 0 /* overflow */ || offset + count > points.length)
			throw new ArrayIndexOutOfBoundsException();
		native_drawLines(handle, points, offset, count, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
		native_drawRoundRect(handle, left, top, right, bottom, rx, ry, paint != null ? paint.paint : default_paint.paint);
	}

	@Override
	public void drawCircle(float cx, float cy, float radius, Paint paint) {
		drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, radius, radius, paint);
	}

	@Override
	public void drawOval(float left, float top, float right, float bottom, Paint paint) {
		drawRoundRect(left, top, right, bottom, (right - left) / 2, (bottom - top) / 2, paint);
	}

	@Override
	public void drawRenderNode(RenderNode node) {
		native_drawRenderNode(handle, node.getGskNode());
	}

	protected native void native_drawBitmap(long handle, long texture, int x, int y, int width, int height, long paint);
	protected native void native_drawBitmap(long handle, long texture, int x, int y, int width, int height, int src_x, int src_y, int src_width, int src_height, long paint);
	protected native void native_drawRect(long handle, float left, float top, float right, float bottom, long paint);
	protected native void native_drawPath(long handle, long path, int fill_rule, long paint);
	protected native void native_translate(long handle, float dx, float dy);
	protected native void native_rotate(long handle, float degrees);
	protected native void native_save(long handle);
	protected native void native_restore(long handle);
	protected native void native_drawLine(long handle, float startX, float startY, float stopX, float stopY, long paint);
	protected native void native_drawLines(long handle, float[] points, int offset, int count, long paint);
	protected native void native_drawText(long handle, String text, float x, float y, long paint);
	protected native void native_drawRoundRect(long handle, float left, float top, float right, float bottom, float rx, float ry, long paint);
	protected native void native_scale(long handle, float sx, float sy);
	protected native void native_concat(long handle, long matrix);
	protected native void native_clipRect(long handle, float left, float top, float right, float bottom);
	protected native void native_clipPath(long handle, long path, int fill_rule);
	protected native void native_pop(long handle, int pop_count);
	protected native void native_drawRenderNode(long handle, long render_node);
}
