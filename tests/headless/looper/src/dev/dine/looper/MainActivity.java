package dev.dine.looper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

/* Runs each loop-slot path once, then finishes the only activity, which stops the main loop. The
 * worker and main wakes each reach a loop in an untimed wait, so a missed one hangs the test. A
 * wait that returns early spins instead: each wait's wall and CPU time are logged. */
public class MainActivity extends Activity {
	private static final String TAG = "looper";

	private HandlerThread worker;
	private Handler main;
	private Handler work;
	private long waitStart, waitCpu;

	// the calling thread's times, before its loop waits
	private void waitBegins() {
		waitStart = SystemClock.uptimeMillis();
		waitCpu = Debug.threadCpuTimeNanos();
	}

	private void logWait(String kind) {
		Log.i(TAG, "looper " + kind + " wait " + (SystemClock.uptimeMillis() - waitStart) + " ms cpu "
		                   + (Debug.threadCpuTimeNanos() - waitCpu) / 1000000 + " ms");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		worker = new HandlerThread("looper-worker");
		worker.start();
		main = new Handler(Looper.getMainLooper());
		work = new Handler(worker.getLooper());
		postWhenWorkerIdles();
	}

	private void postWhenWorkerIdles() {
		if (!worker.getLooper().isIdling()) {
			main.postDelayed(new Runnable() {
				public void run() {
					postWhenWorkerIdles();
				}
			}, 10);
			return;
		}
		Log.i(TAG, "looper worker idling");
		work.post(new Runnable() {
			public void run() {
				Log.i(TAG, "looper worker woken");
				waitBegins();
				work.postDelayed(new Runnable() {
					public void run() {
						logWait("timed");
						Log.i(TAG, "looper worker timeout");
						// main has nothing queued by now: only a wake from this thread runs it
						main.post(new Runnable() {
							public void run() {
								waitBegins();
								work.postDelayed(new Runnable() {
									public void run() {
										main.post(new Runnable() {
											public void run() {
												stopWorkerAndFinish();
											}
										});
									}
								}, 50);
							}
						});
					}
				}, 50);
			}
		});
	}

	private void stopWorkerAndFinish() {
		logWait("untimed");
		Log.i(TAG, "looper main woken");
		worker.quitSafely();
		try {
			worker.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		Log.i(TAG, "looper worker quit");
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "looper done");
	}
}
