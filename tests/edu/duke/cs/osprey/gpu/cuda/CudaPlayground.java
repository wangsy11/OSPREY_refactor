package edu.duke.cs.osprey.gpu.cuda;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleMatrix1D;
import edu.duke.cs.osprey.TestBase;
import edu.duke.cs.osprey.astar.conf.ConfAStarTree;
import edu.duke.cs.osprey.astar.conf.RCs;
import edu.duke.cs.osprey.astar.conf.order.AStarOrder;
import edu.duke.cs.osprey.astar.conf.order.StaticScoreHMeanAStarOrder;
import edu.duke.cs.osprey.astar.conf.scoring.AStarScorer;
import edu.duke.cs.osprey.astar.conf.scoring.MPLPPairwiseHScorer;
import edu.duke.cs.osprey.astar.conf.scoring.PairwiseGScorer;
import edu.duke.cs.osprey.astar.conf.scoring.mplp.NodeUpdater;
import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;
import edu.duke.cs.osprey.confspace.ParameterizedMoleculeCopy;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.dof.FreeDihedral;
import edu.duke.cs.osprey.dof.deeper.DEEPerSettings;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.SimpleEnergyCalculator;
import edu.duke.cs.osprey.ematrix.SimpleEnergyMatrixCalculator;
import edu.duke.cs.osprey.ematrix.epic.EPICSettings;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.energy.ForcefieldInteractionsGenerator;
import edu.duke.cs.osprey.energy.GpuEnergyFunctionGenerator;
import edu.duke.cs.osprey.energy.forcefield.BigForcefieldEnergy;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldInteractions;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.energy.forcefield.GpuForcefieldEnergy;
import edu.duke.cs.osprey.gpu.BufferTools;
import edu.duke.cs.osprey.gpu.cuda.kernels.CCDKernelCuda;
import edu.duke.cs.osprey.minimization.LineSearcher;
import edu.duke.cs.osprey.minimization.Minimizer;
import edu.duke.cs.osprey.minimization.MoleculeModifierAndScorer;
import edu.duke.cs.osprey.minimization.ObjectiveFunction;
import edu.duke.cs.osprey.minimization.SimpleCCDMinimizer;
import edu.duke.cs.osprey.minimization.SurfingLineSearcher;
import edu.duke.cs.osprey.parallelism.ThreadPoolTaskExecutor;
import edu.duke.cs.osprey.parallelism.TimingThread;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.tools.Factory;
import edu.duke.cs.osprey.tools.ObjectIO;
import edu.duke.cs.osprey.tools.Stopwatch;
import edu.duke.cs.osprey.tupexp.LUTESettings;

@SuppressWarnings("unused")
public class CudaPlayground extends TestBase {
	
	// TODO: split into accuracy unit tests and benchmarks
	
	public static void main(String[] args)
	throws Exception {
		
		initDefaultEnvironment();
		
		// NOTE: samples and such here:
		// https://github.com/jcuda/jcuda-samples/tree/master/JCudaSamples/src/main/java/jcuda
		
		// info on dynamic parallelism:
		// http://docs.nvidia.com/cuda/cuda-c-programming-guide/#cuda-dynamic-parallelism
		
		//forcefield();
		ccd();
	}
	
	private static SearchProblem makeSearch()
	throws IOException {
		
		// make a search problem
		System.out.println("Building search problem...");
		
		ResidueFlexibility resFlex = new ResidueFlexibility();
		resFlex.addMutable("39 43", "ALA");
		resFlex.addFlexible("40 41 42 44 45");
		boolean doMinimize = true;
		boolean addWt = true;
		boolean useEpic = false;
		boolean useTupleExpansion = false;
		boolean useEllipses = false;
		boolean useERef = false;
		boolean addResEntropy = false;
		boolean addWtRots = false;
		ArrayList<String[]> moveableStrands = new ArrayList<String[]>();
		ArrayList<String[]> freeBBZones = new ArrayList<String[]>();
		SearchProblem search = new SearchProblem(
			"test", "test/1CC8/1CC8.ss.pdb", 
			resFlex.flexResList, resFlex.allowedAAs, addWt, doMinimize, useEpic, new EPICSettings(), useTupleExpansion, new LUTESettings(),
			new DEEPerSettings(), moveableStrands, freeBBZones, useEllipses, useERef, addResEntropy, addWtRots, null,
			false, new ArrayList<>()
		);
		
		// calc the energy matrix
		File ematFile = new File("/tmp/benchmarkMinimization.emat.dat");
		if (ematFile.exists()) {
			search.emat = (EnergyMatrix)ObjectIO.readObject(ematFile.getAbsolutePath(), false);
		} else {
			search.emat = new SimpleEnergyMatrixCalculator.Cpu(2, EnvironmentVars.curEFcnGenerator.ffParams, search.confSpace, search.shellResidues).calcEnergyMatrix();
			ObjectIO.writeObject(search.emat, ematFile.getAbsolutePath());
		}
		
		return search;
	}
	
	private static RCTuple getConf(SearchProblem search, int i) {
		
		// get the ith conformation
		search.pruneMat = new PruningMatrix(search.confSpace, 1000);
		RCs rcs = new RCs(search.pruneMat);
		AStarOrder order = new StaticScoreHMeanAStarOrder();
		AStarScorer hscorer = new MPLPPairwiseHScorer(new NodeUpdater(), search.emat, 4, 0.0001);
		ConfAStarTree tree = new ConfAStarTree(order, new PairwiseGScorer(search.emat), hscorer, rcs);
		ScoredConf conf = null;
		i++;
		for (int j=0; j<i; j++) {
			conf = tree.nextConf();
		}
		return new RCTuple(conf.getAssignments());
	}
	
	private static void forcefield()
	throws Exception {
		
		SearchProblem search = makeSearch();
		RCTuple tuple = getConf(search, 0);
		ForcefieldParams ffparams = EnvironmentVars.curEFcnGenerator.ffParams;
		
		final int numRuns = 10000;
		final int d = 0;
		final boolean doBenchmarks = true;
		
		// init cpu side
		ParameterizedMoleculeCopy cpuMol = new ParameterizedMoleculeCopy(search.confSpace);
		EnergyFunction cpuEfunc = EnvironmentVars.curEFcnGenerator.fullConfEnergy(search.confSpace, search.shellResidues, cpuMol.getCopiedMolecule());
		MoleculeModifierAndScorer cpuMof = new MoleculeModifierAndScorer(cpuEfunc, search.confSpace, tuple, cpuMol);
		
		DoubleMatrix1D x = DoubleFactory1D.dense.make(cpuMof.getNumDOFs());
		ObjectiveFunction.DofBounds dofBounds = new ObjectiveFunction.DofBounds(cpuMof.getConstraints());
		
		dofBounds.getCenter(x);
		double cpuEnergy = cpuMof.getValue(x);
		double cpuEnergyD = cpuMof.getValForDOF(d, x.get(d));
		
		Stopwatch cpuStopwatch = null;
		if (doBenchmarks) {
			
			// benchmark
			System.out.println("\nbenchmarking CPU...");
			cpuStopwatch = new Stopwatch().start();
			for (int i=0; i<numRuns; i++) {
				cpuMof.getValForDOF(d, x.get(d));
			}
			System.out.println(String.format("finished in %s, %.1f ops\n",
				cpuStopwatch.stop().getTime(1),
				numRuns/cpuStopwatch.getTimeS()
			));
		}
		
		System.out.println();
		
		// init cuda side
		ParameterizedMoleculeCopy cudaMol = new ParameterizedMoleculeCopy(search.confSpace);
		GpuEnergyFunctionGenerator cudaEgen = new GpuEnergyFunctionGenerator(ffparams, new GpuStreamPool(1));
		GpuForcefieldEnergy cudaEfunc = cudaEgen.fullConfEnergy(search.confSpace, search.shellResidues, cudaMol.getCopiedMolecule());
		MoleculeModifierAndScorer cudaMof = new MoleculeModifierAndScorer(cudaEfunc, search.confSpace, tuple, cudaMol);
		
		// full efunc
		System.out.println("atom pairs: " + cudaEfunc.getKernel().getSubset().getNumAtomPairs());
		double gpuEnergy = cudaMof.getValue(x);
		checkEnergy(cpuEnergy, gpuEnergy);
		
		// one dof
		System.out.println("d " + d + " atom pairs: " + ((GpuForcefieldEnergy)cudaMof.getEfunc(d)).getSubset().getNumAtomPairs());
		double gpuEnergyD = cudaMof.getValForDOF(d, x.get(d));
		checkEnergy(cpuEnergyD, gpuEnergyD);
		
		Stopwatch gpuStopwatch = null;
		if (doBenchmarks) {
			
			// benchmark
			// 1024 threads
			// 16 blocks: ~14.7k ops
			System.out.println("\nbenchmarking GPU dof...");
			gpuStopwatch = new Stopwatch().start();
			for (int i=0; i<numRuns; i++) {
				cudaMof.getValForDOF(d, x.get(d));
			}
			System.out.println(String.format("finished in %s, %.1f ops, speedup: %.1fx\n",
				gpuStopwatch.stop().getTime(1),
				numRuns/gpuStopwatch.getTimeS(),
				(double)cpuStopwatch.getTimeNs()/gpuStopwatch.getTimeNs()
			));
		}
		
		// cleanup
		cudaEfunc.cleanup();
		cudaEgen.cleanup();
	}

	private static void ccd()
	throws IOException {
		
		SearchProblem search = makeSearch();
		RCTuple tuple = getConf(search, 0);
		ForcefieldParams ffparams = EnvironmentVars.curEFcnGenerator.ffParams;
		
		final int NumRuns = 10;
		final boolean doBenchmarks = true;
		
		// init cpu side
		ParameterizedMoleculeCopy cpuMol = new ParameterizedMoleculeCopy(search.confSpace);
		EnergyFunction cpuEfunc = EnvironmentVars.curEFcnGenerator.fullConfEnergy(search.confSpace, search.shellResidues, cpuMol.getCopiedMolecule());
		MoleculeModifierAndScorer cpuMof = new MoleculeModifierAndScorer(cpuEfunc, search.confSpace, tuple, cpuMol);
		
		DoubleMatrix1D x = DoubleFactory1D.dense.make(cpuMof.getNumDOFs());
		ObjectiveFunction.DofBounds dofBounds = new ObjectiveFunction.DofBounds(cpuMof.getConstraints());
		dofBounds.getCenter(x);
		
		SimpleCCDMinimizer cpuMinimizer = new SimpleCCDMinimizer();
		cpuMinimizer.init(cpuMof);
		
		// restore coords
		cpuMof.setDOFs(x);
		
		Minimizer.Result cpuResult = cpuMinimizer.minimize();
		
		Stopwatch cpuStopwatch = null;
		if (doBenchmarks) {
			
			// benchmark cpu side: ~2 ops
			System.out.println("\nbenchmarking cpu...");
			cpuStopwatch = new Stopwatch().start();
			for (int i=0; i<NumRuns; i++) {
				cpuMof.setDOFs(x);
				cpuMinimizer.minimize();
			}
			System.out.println(String.format("finished in %8s, ops: %5.0f\n",
				cpuStopwatch.stop().getTime(TimeUnit.MILLISECONDS),
				NumRuns/cpuStopwatch.getTimeS()
			));
		}
		
		// init cuda side
		ParameterizedMoleculeCopy cudaMol = new ParameterizedMoleculeCopy(search.confSpace);
		GpuEnergyFunctionGenerator cudaEgen = new GpuEnergyFunctionGenerator(ffparams, new GpuStreamPool(1));
		GpuForcefieldEnergy cudaEfunc = cudaEgen.fullConfEnergy(search.confSpace, search.shellResidues, cudaMol.getCopiedMolecule());
		MoleculeModifierAndScorer cudaMof = new MoleculeModifierAndScorer(cudaEfunc, search.confSpace, tuple, cudaMol);
		
		SimpleCCDMinimizer cudaMinimizer = new SimpleCCDMinimizer();
		cudaMinimizer.init(cudaMof);
		
		// restore coords
		cudaMof.setDOFs(x);
		
		// check accuracy
		Minimizer.Result cudaResult = cudaMinimizer.minimize();
		System.out.println(String.format("max xd dist: %8.6f", maxxddist(cpuResult.dofValues, cudaResult.dofValues)));
		checkEnergy(cpuResult.energy, cpuMof.getValue(cpuResult.dofValues));
		
		Stopwatch cudaOriginalStopwatch = null;
		if (doBenchmarks) {
			
			// benchmark cuda side: ~15 ops
			System.out.println("\nbenchmarking cuda original...");
			cudaOriginalStopwatch = new Stopwatch().start();
			for (int i=0; i<NumRuns; i++) {
				cudaMof.setDOFs(x);
				cudaMinimizer.minimize();
			}
			System.out.println(String.format("finished in %8s, ops: %5.0f, speedup over cpu: %.1fx\n",
				cudaOriginalStopwatch.stop().getTime(TimeUnit.MILLISECONDS),
				NumRuns/cudaOriginalStopwatch.getTimeS(),
				(float)cpuStopwatch.getTimeNs()/cudaOriginalStopwatch.getTimeNs()
			));
		}
		
		// cleanup
		cudaEfunc.cleanup();
		cudaEgen.cleanup();
		
		// collects the dofs
		List<FreeDihedral> dofs = new ArrayList<>();
		for (DegreeOfFreedom dof : cudaMof.getDOFs()) {
			dofs.add((FreeDihedral)dof);
		}
		
		// make the kernel directly
		GpuStreamPool cudaPool = new GpuStreamPool(1);
		GpuStream stream = cudaPool.checkout();
		ForcefieldInteractionsGenerator intergen = new ForcefieldInteractionsGenerator();
		ForcefieldInteractions interactions = intergen.makeFullConf(search.confSpace, search.shellResidues, cudaMol.getCopiedMolecule());
		BigForcefieldEnergy bigff = new BigForcefieldEnergy(ffparams, interactions, BufferTools.Type.Direct);
		MoleculeModifierAndScorer bigMof = new MoleculeModifierAndScorer(bigff, search.confSpace, tuple, cudaMol);
		CCDKernelCuda kernel = new CCDKernelCuda(stream);
		kernel.init(bigMof);
		
		// restore coords
		cudaMof.setDOFs(x);
		kernel.uploadCoordsAsync();
		
		// check accuracy
		Minimizer.Result result = kernel.runSync(x, dofBounds);
		System.out.println(String.format("max xd dist: %8.6f", maxxddist(cpuResult.dofValues, result.dofValues)));
		checkEnergy(cpuResult.energy, result.energy);
		
		Stopwatch cudaOneBlockStopwatch = null;
		if (doBenchmarks) {
			
			// benchmark: ~5 ops
			System.out.println("\nbenchmarking cuda one-block...");
			cudaOneBlockStopwatch = new Stopwatch().start();
			for (int i=0; i<NumRuns; i++) {
				kernel.uploadCoordsAsync();
				kernel.runSync(x, dofBounds);
			}
			System.out.println(String.format("finished in %8s, ops: %5.0f, speedup over cpu: %.1fx, speedup over original: %.1fx\n",
				cudaOneBlockStopwatch.stop().getTime(TimeUnit.MILLISECONDS),
				NumRuns/cudaOneBlockStopwatch.getTimeS(),
				(float)cpuStopwatch.getTimeNs()/cudaOneBlockStopwatch.getTimeNs(),
				(float)cudaOriginalStopwatch.getTimeNs()/cudaOneBlockStopwatch.getTimeNs()
			));
		}
		
		// cleanup
		cudaPool.release(stream);
		kernel.cleanup();
		cudaPool.cleanup();
		
		if (doBenchmarks) {
		
			class StreamThread extends TimingThread {
				
				private GpuStreamPool streams;
				private GpuStream stream;
				private CCDKernelCuda ffkernel;
				
				public StreamThread(int i, GpuStreamPool streams) {
					super("stream-" + i);
					this.streams = streams;
				}
				
				@Override
				protected void init()
				throws Exception {
					stream = streams.checkout();
					ForcefieldInteractionsGenerator intergen = new ForcefieldInteractionsGenerator();
					ForcefieldInteractions interactions = intergen.makeFullConf(search.confSpace, search.shellResidues, cudaMol.getCopiedMolecule());
					BigForcefieldEnergy bigff = new BigForcefieldEnergy(ffparams, interactions, BufferTools.Type.Direct);
					MoleculeModifierAndScorer bigMof = new MoleculeModifierAndScorer(bigff, search.confSpace, tuple, cudaMol);
					ffkernel = new CCDKernelCuda(stream);
					ffkernel.init(bigMof);
				}

				@Override
				protected void warmup() {
					for (int i=0; i<2; i++) {
						ffkernel.uploadCoordsAsync();
						ffkernel.runSync(x, dofBounds);
					}
				}

				@Override
				protected void time() {
					for (int i=0; i<NumRuns; i++) {
						ffkernel.uploadCoordsAsync();
						ffkernel.runSync(x, dofBounds);
					}
				}
				
				@Override
				protected void cleanup() {
					ffkernel.cleanup();
					streams.release(stream);
				}
			}
			
			int[] numStreamsList = { 1, 4, 8, 12, 16, 20, 24, 28, 32 };
			// 512 threads
			// 16 streams: 75.5, 82.3, 70.1 ops
			// 20 streams: 67.5, 81.2, 60.4 ops
			// 24 streams: 83.0, 61.8, 74.6 ops
			// 28 streams: 63.7, 74.8, 78.0 ops
			// 32 streams: 74.7, 82.2, 78.3 ops
			// wow, lots of volatility... not clear what's best
			
			for (int numStreams : numStreamsList) {
				
				int numGpus = 1;
				cudaPool = new GpuStreamPool(numGpus, divUp(numStreams, numGpus));
				
				List<TimingThread> threads = new ArrayList<>();
				for (int i=0; i<numStreams; i++) {
					threads.add(new StreamThread(i, cudaPool));
				}
				System.out.println("benchmarking " + numStreams + " GPU streams...");
				Stopwatch gpuStreamsStopwatch = TimingThread.timeThreads(threads);
				System.out.println(String.format("finished in %s, %.1f ops, speedup over cpu: %.1fx, speedup over 1 stream: %.1fx",
					gpuStreamsStopwatch.getTime(1),
					numStreams*NumRuns/gpuStreamsStopwatch.getTimeS(),
					(double)numStreams*cpuStopwatch.getTimeNs()/gpuStreamsStopwatch.getTimeNs(),
					(double)numStreams*cudaOneBlockStopwatch.getTimeNs()/gpuStreamsStopwatch.getTimeNs()
				));
				
				cudaPool.cleanup();
			}
		}
	}
	
	private static double maxxddist(DoubleMatrix1D a, DoubleMatrix1D b) {
		double maxDist = 0;
		assert (a.size() == b.size());
		int numDofs = a.size();
		for (int d=0; d<numDofs; d++) {
			maxDist = Math.max(maxDist, Math.abs(a.get(d) - b.get(d)));
		}
		return maxDist;
	}

	private static void checkEnergy(double cpuEnergy, double gpuEnergy) {
		System.out.println(String.format("cpu: %12.6f   gpu: %12.6f   err: %.12f", cpuEnergy, gpuEnergy, Math.abs(cpuEnergy - gpuEnergy)));
	}
	
	private static int divUp(int a, int b) {
		return (a + b - 1)/b;
	}
}
