package edu.duke.cs.osprey.minimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.duke.cs.osprey.confspace.ConfSearch.EnergiedConf;
import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.ParameterizedMoleculeCopy;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.parallelism.TaskExecutor;
import edu.duke.cs.osprey.parallelism.ThreadPoolTaskExecutor;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.tools.Factory;
import edu.duke.cs.osprey.tools.ObjectPool;
import edu.duke.cs.osprey.tools.Progress;

public abstract class ConfMinimizer {
	
	public static class Async {
		
		private static class TaskStuff {
			public ParameterizedMoleculeCopy pmol;
			public EnergyFunction efunc;
			public Minimizer.Reusable minimizer;
		}
		
		private class Task implements Runnable {
			
			public ScoredConf conf;
			public EnergiedConf minimizedConf;
			
			@Override
			public void run() {
				minimizedConf = minimizeSync(conf);
			}
		}
		
		public static interface Listener {
			void onMinimized(EnergiedConf econf);
		}
	
		private ConfSpace confSpace;
		private TaskExecutor tasks;
		private Factory<? extends Minimizer,MoleculeModifierAndScorer> minimizers;
		private ObjectPool<TaskStuff> taskStuffPool;
	
		public Async(Factory<? extends EnergyFunction,Molecule> efuncs, ConfSpace confSpace, TaskExecutor tasks, Factory<? extends Minimizer,MoleculeModifierAndScorer> minimizers) {
			
			this.confSpace = confSpace;
			this.tasks = tasks;
			this.minimizers = minimizers;
			
			// make a pool for molecules and energy functions
			// to keep concurrent tasks from racing each other
			taskStuffPool = new ObjectPool<>(new Factory<TaskStuff,Void>() {
				@Override
				public TaskStuff make(Void context) {
					
					TaskStuff out = new TaskStuff();
					out.pmol = new ParameterizedMoleculeCopy(confSpace);
					out.efunc = efuncs.make(out.pmol.getCopiedMolecule());
					out.minimizer = null;
					return out;
				}
			});
			
			// pre-allocate the pool
			taskStuffPool.allocate(tasks.getParallelism());
		}
		
		public EnergiedConf minimizeSync(ScoredConf conf) {
			
			TaskStuff stuff;
			synchronized (taskStuffPool) {
				stuff = taskStuffPool.checkout();
			}
			try {
				
				// set the molecule to the conf
				RCTuple tuple = new RCTuple(conf.getAssignments());
				MoleculeModifierAndScorer mof = new MoleculeModifierAndScorer(stuff.efunc, confSpace, tuple, stuff.pmol);
				
				// get (or reuse) the minimizer
				Minimizer minimizer;
				if (stuff.minimizer == null) {
					minimizer = minimizers.make(mof);
				} else {
					stuff.minimizer.init(mof);
					minimizer = stuff.minimizer;
				}
				
				// minimize the conf
				Minimizer.Result result = minimizer.minimize();
				
				// cleanup
				if (minimizer instanceof Minimizer.Reusable) {
					stuff.minimizer = (Minimizer.Reusable)minimizer;
				} else if (minimizer instanceof Minimizer.NeedsCleanup) {
					((Minimizer.NeedsCleanup)minimizer).cleanup();
				}
				
				return new EnergiedConf(conf, result.energy);
			
			} finally {
				synchronized (taskStuffPool) {
					taskStuffPool.release(stuff);
				}
			}
		}
		
		public void minimizeAsync(ScoredConf conf, Listener listener) {
			
			if (listener == null) {
				throw new IllegalArgumentException("listener can't be null");
			}
			
			// make the task to handle the minimization
			Task task = new Task();
			task.conf = conf;
			
			// submit the task with a chaining task listener
			tasks.submit(task, new TaskExecutor.TaskListener() {
				@Override
				public void onFinished(Runnable taskBase) {
					listener.onMinimized(task.minimizedConf);
				}
			});
		}
		
		public void waitForFinish() {
			tasks.waitForFinish();
		}
		
		public int getParallelism() {
			return tasks.getParallelism();
		}
		
		public void cleanup() {
			
			// make sure all the tasks are finished before cleaning up
			// tasks that aren't finished yet won't cleanup properly and leak memory!
			try {
				tasks.waitForFinish();
			} catch (Throwable t) {
				// a task failed, but someone else should already know about it
				// this could be being called in an error handler, so we should still try to cleanup
			}
			
			synchronized (taskStuffPool) {
				
				// make sure everything has been returned to the pool
				if (taskStuffPool.available() < taskStuffPool.size()) {
					System.err.println(String.format(
						"molecule pool in inconsistent state (only %d/%d molecules available)."
						+ "Some items will not be cleaned up. this is a bug",
						taskStuffPool.available(), taskStuffPool.size()
					));
				}
				
				// cleanup the task stuff if needed
				for (TaskStuff stuff : taskStuffPool) {
					
					if (stuff.efunc instanceof EnergyFunction.NeedsCleanup) {
						((EnergyFunction.NeedsCleanup)stuff.efunc).cleanup();
					}
					if (stuff.minimizer instanceof Minimizer.NeedsCleanup) {
						((Minimizer.NeedsCleanup)stuff.minimizer).cleanup();
					}
				}
				taskStuffPool.clear();
			}
		}
	}
	
	private ThreadPoolTaskExecutor tasks;
	private Async asyncMinimizer;
	private boolean reportProgress;
	
	protected ConfMinimizer() {
		tasks = null;
		asyncMinimizer = null;
		reportProgress = false;
	}
	
	protected void init(int numThreads, Factory<? extends EnergyFunction,Molecule> efuncs, Factory<? extends Minimizer,MoleculeModifierAndScorer> minimizers, ConfSpace confSpace) {
		
		if (numThreads <= 0) {
			throw new IllegalArgumentException("numThreads must be > 0");
		}
		
		// start the thread pool
		tasks = new ThreadPoolTaskExecutor();
		tasks.start(numThreads);
		
		// make the minimizer
		asyncMinimizer = new Async(efuncs, confSpace, tasks, minimizers);
	}
	
	public void setReportProgress(boolean val) {
		reportProgress = val;
	}
	
	public Async getAsync() {
		return asyncMinimizer;
	}
	
	/**
	 * NOTE: don't call this in a loop, you'll loose all the parallelism
	 * but it's here if you need one-off minimizations
	 */
	public EnergiedConf minimize(ScoredConf conf) {
		return minimize(Arrays.asList(conf)).get(0);
	}
	
	public List<EnergiedConf> minimize(List<ScoredConf> confs) {
		
		// allocate space to hold the minimized values
		List<EnergiedConf> econfs = new ArrayList<>(confs.size());
		for (int i=0; i<confs.size(); i++) {
			econfs.add(null);
		}
		
		// track progress if desired
		final Progress progress;
		if (reportProgress) {
			progress = new Progress(confs.size());
		} else {
			progress = null;
		}
		
		// minimize them all
		for (int i=0; i<confs.size(); i++) {
			final int fi = i;
			asyncMinimizer.minimizeAsync(confs.get(i), new Async.Listener() {
				@Override
				public void onMinimized(EnergiedConf econf) {
					econfs.set(fi, econf);
					if (progress != null) {
						progress.incrementProgress();
					}
				}
			});
		}
		asyncMinimizer.waitForFinish();
		
		return econfs;
	}
	
	public void cleanup() {
		
		// NOTE: async minimizer waits for the tasks to finish, so do that before stopping the tasks
		asyncMinimizer.cleanup();
		tasks.stop();
	}
}
