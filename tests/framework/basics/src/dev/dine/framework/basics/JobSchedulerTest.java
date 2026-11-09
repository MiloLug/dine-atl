package dev.dine.framework.basics;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

// jobs start and stop on the main thread: an empty runOnMainSync is the barrier (waitForIdleSync
// waits for nothing, ATL-70)
@SuppressWarnings("deprecation")
public class JobSchedulerTest extends android.test.InstrumentationTestCase {
	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	private JobScheduler scheduler() throws Exception {
		return (JobScheduler) context().getSystemService(JobScheduler.class);
	}

	private JobInfo.Builder job(int id) {
		return new JobInfo.Builder(id, new ComponentName(context(), TestJobService.class));
	}

	private void barrier() {
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {}
		});
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		TestJobService.reset();
	}

	// a test that failed midway leaves no job behind for the next one
	@Override
	protected void tearDown() throws Exception {
		for (int id = 1; id <= 7; id++)
			scheduler().cancel(id);
		barrier();
		super.tearDown();
	}

	public void testCancelStopsARunningJob() throws Exception {
		scheduler().schedule(job(1).build());
		barrier();
		assertEquals(Arrays.asList("start"), TestJobService.events);
		scheduler().cancel(1);
		barrier();
		assertEquals(Arrays.asList("start", "stop"), TestJobService.events);
	}

	public void testCancelKeepsAPendingJobFromStarting() throws Exception {
		scheduler().schedule(job(2).setMinimumLatency(300).build());
		scheduler().cancel(2);
		Thread.sleep(800);
		barrier();
		assertEquals(Arrays.asList(), TestJobService.events);
	}

	public void testCancelEndsAReschedule() throws Exception {
		TestJobService.rescheduleNext = true;
		scheduler().schedule(job(3).setBackoffCriteria(500, 0).build()); // 0: BACKOFF_POLICY_LINEAR, which ATL lacks
		barrier();
		assertEquals(Arrays.asList("start"), TestJobService.events);
		scheduler().cancel(3);
		Thread.sleep(1200);
		barrier();
		assertEquals(Arrays.asList("start"), TestJobService.events);
	}

	// the old job's late jobFinished must not drop the new job of the same id
	public void testFinishOfACancelledJobKeepsItsSuccessor() throws Exception {
		scheduler().schedule(job(4).build());
		barrier();
		final JobParameters old = TestJobService.lastParams;
		scheduler().cancel(4);
		barrier();
		scheduler().schedule(job(4).setMinimumLatency(300).build());
		getInstrumentation().runOnMainSync(new Runnable() {
			public void run() {
				TestJobService.instance.jobFinished(old, false);
			}
		});
		Thread.sleep(800);
		barrier();
		assertEquals(Arrays.asList("start", "stop", "start"), TestJobService.events);
	}

	public void testCancelLeavesADoneJob() throws Exception {
		TestJobService.doneAtStart = true;
		scheduler().schedule(job(5).build());
		barrier();
		scheduler().cancel(5);
		barrier();
		assertEquals(Arrays.asList("start"), TestJobService.events);
	}

	// the reschedule's start says done: cancel has nothing to stop
	public void testCancelLeavesADoneRepeat() throws Exception {
		TestJobService.rescheduleNext = true;
		TestJobService.doneAtStart = true;
		scheduler().schedule(job(6).setBackoffCriteria(300, 0).build()); // 0: BACKOFF_POLICY_LINEAR
		Thread.sleep(800);
		barrier();
		scheduler().cancel(6);
		barrier();
		assertEquals(Arrays.asList("start", "start"), TestJobService.events);
	}

	// cancel from another thread while main is between the start's pending check and the job
	// counting as running: the started job must still get its stop
	public void testCancelDuringStartStopsTheJob() throws Exception {
		scheduler().schedule(new JobInfo.Builder(7, new ComponentName(context(), CreateGateJobService.class)).build());
		try {
			assertTrue("the start never reached onCreate", CreateGateJobService.creating.await(5, TimeUnit.SECONDS));
			scheduler().cancel(7);
		} finally {
			CreateGateJobService.release.countDown();
		}
		barrier();
		assertEquals(Arrays.asList("start", "stop"), TestJobService.events);
	}
}
