package edu.duke.cs.osprey.parallelism;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTaskExecutor extends TaskExecutor {
	
	private class FailedTask implements Runnable {
		
		@Override
		public void run() {
			// don't need to do anything
		}
	}
	
	private class FinishState extends Signal {
		
		private boolean submissionsOpen;
		private int numSubmitted;
		private int numWin;
		private int numFail;
		
		public FinishState() {
			reset();
		}
		
		@Override
		public synchronized void reset() {
			super.reset();
			submissionsOpen = true;
			numSubmitted = 0;
			numWin = 0;
			numFail = 0;
		}
		
		public synchronized void recordSubmission() {
			if (!submissionsOpen) {
				reset();
			}
			numSubmitted++;
		}
		
		@Override
		public synchronized void waitForSignal() {
			submissionsOpen = false;
			super.waitForSignal();
		}
	
		public synchronized void recordResult(boolean isFail) {
			
			// record the result
			if (isFail) {
				numFail++;
			} else {
				numWin++;
			}
			
			// if this the last task, send the signal
			boolean isLastTask = numWin + numFail == numSubmitted;
			if (isLastTask) {
				sendSignal();
			}
		}
	}
	
	private class TaskThread extends WorkQueueThread<Runnable> {
		
		private TaskThread(int index, BlockingQueue<Runnable> queue) {
			super("TaskThread-" + index, queue);
		}
		
		@Override
		public void doWork(Runnable task)
		throws InterruptedException {
			
			// run the task
			try {
				task.run();
			} catch (Throwable t) {
				t.printStackTrace(System.err);
				task = new FailedTask();
			}
			
			// signal task finished
			boolean wasAdded = false;
			while (!wasAdded) {
				wasAdded = outgoingQueue.offer(task, 1, TimeUnit.SECONDS);
			}
		}
	}
	
	private class ListenerThread extends WorkQueueThread<Runnable> {
		
		public ListenerThread(BlockingQueue<Runnable> queue) {
			super("TaskThread-listener", queue);
		}
	
		@Override
		public void doWork(Runnable task)
		throws InterruptedException {
				
			// what happened to the task
			boolean isFail = task instanceof FailedTask;
			
			// should we call a listener?
			if (!isFail && task instanceof ListeningTask) {
				try {
					ListeningTask listeningTask = (ListeningTask)task;
					listeningTask.listener.onFinished(listeningTask.task);
				} catch (Throwable t) {
					t.printStackTrace(System.err);
					isFail = true;
				}
			}
			
			// record the result
			finishState.recordResult(isFail);
		}
	}
	
	private static class ListeningTask implements Runnable {
		
		public Runnable task;
		public TaskExecutor.TaskListener listener;
		
		public ListeningTask(Runnable task, TaskExecutor.TaskListener listener) {
			this.task = task;
			this.listener = listener;
		}
		
		@Override
		public void run() {
			task.run();
		}
	}
	
	private BlockingQueue<Runnable> incomingQueue;
	private BlockingQueue<Runnable> outgoingQueue;
	private List<TaskThread> threads;
	private ListenerThread listenerThread;
	private FinishState finishState;
	
	public ThreadPoolTaskExecutor() {
		incomingQueue = null;
		outgoingQueue = null;
		threads = null;
		listenerThread = null;
		finishState = new FinishState();
	}
	
	public void start(int numThreads) {
		
		// make the queues about twice the number of threads,
		// so the worker threads probably never have to block waiting for the next task
		incomingQueue = new ArrayBlockingQueue<>(numThreads*2);
		outgoingQueue = new ArrayBlockingQueue<>(numThreads*2);
		
		// init state
		finishState.reset();
		
		// start all the threads
		threads = new ArrayList<>(numThreads);
		for (int i=0; i<numThreads; i++) {
			threads.add(new TaskThread(i, incomingQueue));
		}
		for (TaskThread thread : threads) {
			thread.start();
		}
		
		// start the listener thread
		listenerThread = new ListenerThread(outgoingQueue);
		listenerThread.start();
	}
	
	@Override
	public void submit(Runnable task) {
		
		if (incomingQueue == null) {
			throw new IllegalStateException("thread pool is not started, can't submit tasks");
		}
		
		// send the task, and wait for space in the queue if needed
		// TODO: try to cut this down to one synchronization instead of two?
		try {
			finishState.recordSubmission(); // sync
			boolean wasAdded = false;
			while (!wasAdded) {
				wasAdded = incomingQueue.offer(task, 1, TimeUnit.SECONDS); // sync
			}
		} catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	@Override
	public void submit(Runnable task, TaskListener listener) {
		submit(new ListeningTask(task, listener));
	}
	
	@Override
	public void waitForFinish() {
		finishState.waitForSignal();
	}
	
	public void stop() {
		for (TaskThread thread : threads) {
			thread.askToStop();
		}
		listenerThread.askToStop();
	}
	
	public void stopAndWait(int timeoutMs)
	throws InterruptedException {
		stop();
		for (TaskThread thread : threads) {
			thread.join(timeoutMs);
		}
		listenerThread.join(timeoutMs);
	}
}