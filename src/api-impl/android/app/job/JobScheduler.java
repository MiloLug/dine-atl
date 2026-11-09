package android.app.job;

import android.app.ContextImpl;
import android.atl.ATLLoadedApp;
import android.content.Context;
import android.os.Handler;
import android.util.Slog;
import java.util.*;

public class JobScheduler {
	private static final String TAG = "JobScheduler";

	private final Context context;

	// ContextImpl makes it before its app is set: the app's state is read on use
	public JobScheduler(Context context) {
		this.context = context;
	}

	private Map<Integer, JobInfo> pendingJobs() {
		return context.get_atl_loaded_app().pending_jobs;
	}

	private IdentityHashMap<Class<? extends JobService>, JobService> runningServices() {
		return context.get_atl_loaded_app().job_services;
	}

	/**
	 * Retrieve all jobs that have been scheduled by the calling application.
	 *
	 * @return a list of all of the app's scheduled jobs.  This includes jobs that are
	 *     currently started as well as those that are still waiting to run.
	 */
	public List<JobInfo> getAllPendingJobs() {
		return new ArrayList<>(pendingJobs().values());
	};

	public int enqueue(JobInfo job, JobWorkItem work) {
		return 1; //RESULT_SUCCESS
	}

	public int schedule(JobInfo job) {
		Slog.i(TAG, "JobScheduler.schedule() called with job: " + job);
		if (pendingJobs().putIfAbsent(job.getId(), job) != null)
			return 1; //RESULT_SUCCESS
		new Handler(context.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				if (pendingJobs().get(job.getId()) != job) // cancelled or replaced
					return;
				try {
					Class<? extends JobService> cls = serviceClass(job);
					if (!runningServices().containsKey(cls)) {
						JobService service = cls.getConstructor().newInstance();
						ATLLoadedApp atlLoadedApp = context.get_atl_loaded_app();
						service.attachBaseContext(new ContextImpl(atlLoadedApp.createDefaultResources(), atlLoadedApp,
						                                          context.atl_process_app(), atlLoadedApp.pkg.applicationInfo.theme));
						service.onCreate();
						runningServices().put(cls, service);
					}
					job.running = true;
					boolean result = runningServices().get(cls).onStartJob(new JobParameters(job));
					if (!result)
						job.running = false;
					Slog.i(TAG, "onStartJob() returned " + result);
				} catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
			}
		}, job.minLatencyMillis);
		return 1; //RESULT_SUCCESS
	}

	private Class<? extends JobService> serviceClass(JobInfo job) throws ClassNotFoundException {
		return context.get_atl_loaded_app().loadClass(job.getService().getClassName()).asSubclass(JobService.class);
	}

	public void cancel(int id) {
		JobInfo job = pendingJobs().remove(id);
		if (job != null) {
			// on main, behind a start already under way
			new Handler(context.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					if (!job.running)
						return;
					JobService service;
					try {
						service = runningServices().get(serviceClass(job));
					} catch (ClassNotFoundException e) {
						return;
					}
					if (service != null) {
						JobParameters params = new JobParameters(job);
						service.onStopJob(params);
						job.running = false;
					}
				}
			});
		}
	}
}
