package dev.dine.framework.basics;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.View;
import android.widget.FrameLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Android's Looper polls between any two messages, so layout and frames go on while messages are
// due, and messages go on while every frame asks for the next
public class LooperTest extends android.test.InstrumentationTestCase {
	private static final long WAIT_S = 5;

	static class Redrawing extends View {
		Redrawing(Context context) {
			super(context);
		}

		@Override
		public void onDraw(Canvas canvas) {
			invalidate();
		}
	}

	private WindowActivity start() {
		Intent intent = new Intent(getInstrumentation().getTargetContext(), WindowActivity.class);
		return (WindowActivity)getInstrumentation().startActivitySync(intent);
	}

	public void testAChainOfDueMessagesLetsLayoutRun() throws Exception {
		final WindowActivity activity = start();
		getInstrumentation().waitForIdleSync();
		final CountDownLatch laidOut = new CountDownLatch(1);
		final AtomicBoolean stop = new AtomicBoolean();
		final Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			View view;

			public void run() {
				if (view == null) {
					view = new View(activity);
					activity.root.addView(view, new FrameLayout.LayoutParams(40, 30));
				}
				if (view.getWidth() > 0)
					laidOut.countDown();
				else if (!stop.get())
					handler.post(this);
			}
		});
		boolean done = laidOut.await(WAIT_S, TimeUnit.SECONDS);
		stop.set(true);
		assertTrue("no layout pass while a Runnable reposts itself", done);
	}

	// the layout pass a message asked for runs before the queue counts as idle: on Android it is a message
	public void testIdleHandlersRunAfterThePendingLayout() throws Exception {
		final WindowActivity activity = start();
		getInstrumentation().waitForIdleSync();
		final int[] width = {-1};
		final CountDownLatch idle = new CountDownLatch(1);
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				final View view = new View(activity);
				activity.root.addView(view, new FrameLayout.LayoutParams(40, 30));
				Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
					public boolean queueIdle() {
						width[0] = view.getWidth();
						idle.countDown();
						return false;
					}
				});
			}
		});
		assertTrue(idle.await(WAIT_S, TimeUnit.SECONDS));
		assertEquals(40, width[0]);
	}

	public void testAViewThatKeepsInvalidatingLetsMessagesRun() throws Exception {
		final WindowActivity activity = start();
		final Redrawing[] view = new Redrawing[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				view[0] = new Redrawing(activity);
				activity.root.addView(view[0], new FrameLayout.LayoutParams(40, 30));
			}
		});
		getInstrumentation().waitForIdleSync();
		final Handler handler = new Handler(Looper.getMainLooper());
		final CountDownLatch ran = new CountDownLatch(20);
		handler.post(new Runnable() {
			public void run() {
				ran.countDown();
				if (ran.getCount() > 0)
					handler.post(this);
			}
		});
		boolean done = ran.await(WAIT_S, TimeUnit.SECONDS);
		handler.post(new Runnable() {
			public void run() {
				activity.root.removeView(view[0]);
			}
		});
		assertTrue("messages held up by a view that invalidates in onDraw", done);
	}
}
