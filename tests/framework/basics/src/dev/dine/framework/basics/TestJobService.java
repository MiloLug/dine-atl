package dev.dine.framework.basics;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// one instance per app: the scheduler keeps a service once it made it
public class TestJobService extends JobService {
	static final List<String> events = Collections.synchronizedList(new ArrayList<String>());
	static volatile JobService instance;
	static volatile JobParameters lastParams;
	// the next onStartJob asks for a reschedule, once
	static volatile boolean rescheduleNext;
	// onStartJob returns false: the job is done
	static volatile boolean doneAtStart;

	static void reset() {
		events.clear();
		lastParams = null;
		rescheduleNext = false;
		doneAtStart = false;
	}

	@Override
	public boolean onStartJob(JobParameters params) {
		instance = this;
		lastParams = params;
		events.add("start");
		if (rescheduleNext) {
			rescheduleNext = false;
			jobFinished(params, true);
			return false;
		}
		return !doneAtStart;
	}

	// ATL's JobService leaves Service.onBind abstract; AOSP's is final
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		events.add("stop");
		return false;
	}
}
