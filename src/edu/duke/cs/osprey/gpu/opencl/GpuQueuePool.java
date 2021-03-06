package edu.duke.cs.osprey.gpu.opencl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GpuQueuePool {
	
	private int numQueuesPerGpu;
	private List<List<GpuQueue>> queuesByGpu;
	private List<GpuQueue> queues;
	private boolean[] checkedOut;
	
	public GpuQueuePool() {
		this(1);
	}
	
	public GpuQueuePool(int queuesPerGpu) {
		this(Gpus.get().getGpus().size(), queuesPerGpu);
	}
	
	public GpuQueuePool(int numGpus, int queuesPerGpu) {
		this(numGpus, queuesPerGpu, false);
	}
	
	public GpuQueuePool(int numGpus, int numQueuesPerGpu, boolean useProfiling) {
		
		this.numQueuesPerGpu = numQueuesPerGpu;
		
		// make sure we don't try to use too many gpus
		List<Gpu> gpus = Gpus.get().getGpus();
		numGpus = Math.min(numGpus, gpus.size());
		
		// make n queues for each gpu
		queuesByGpu = new ArrayList<>(numGpus);
		for (int i=0; i<numGpus; i++) {
			Gpu gpu = gpus.get(i);
			List<GpuQueue> queuesAtGpu = new ArrayList<>();
			for (int j=0; j<numQueuesPerGpu; j++) {
				queuesAtGpu.add(new GpuQueue(gpu, useProfiling, false));
			}
			queuesByGpu.add(queuesAtGpu);
		}
		
		// flatten the queues into a list
		// visit the first queue on each gpu first, then the second, etc...
		queues = new ArrayList<>(numGpus*numQueuesPerGpu);
		for (int i=0; i<numQueuesPerGpu; i++) {
			for (int j=0; j<numGpus; j++) {
				queues.add(queuesByGpu.get(j).get(i));
			}
		}
		
		// initially, all queues are available
		checkedOut = new boolean[queues.size()];
		Arrays.fill(checkedOut, false);
		
		System.out.println(String.format("GpuQueuePool: using %d command queue(s) across %d gpu(s)", queues.size(), numGpus));
	}
	
	public int getNumGpus() {
		return queuesByGpu.size();
	}
	
	public int getNumQueuesPerGpu() {
		return numQueuesPerGpu;
	}
	
	public int getNumQueues() {
		return queues.size();
	}
	
	public synchronized GpuQueue checkout() {
		
		// find an available queue
		for (int i=0; i<queues.size(); i++) {
			if (!checkedOut[i]) {
				checkedOut[i] = true;
				return queues.get(i);
			}
		}
		
		throw new IllegalStateException(String.format("no more queues to checkout, %d already used", queues.size()));
	}
	
	public synchronized void release(GpuQueue queue) {
		
		for (int i=0; i<queues.size(); i++) {
			if (queues.get(i) == queue) {
				checkedOut[i] = false;
			}
		}
	}

	public void cleanup() {
		for (GpuQueue queue : queues) {
			queue.cleanup();
		}
		queuesByGpu.clear();
	}
}
