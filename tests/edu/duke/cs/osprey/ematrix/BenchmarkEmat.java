package edu.duke.cs.osprey.ematrix;

import java.util.ArrayList;

import edu.duke.cs.osprey.TestBase;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.dof.deeper.DEEPerSettings;
import edu.duke.cs.osprey.ematrix.epic.EPICSettings;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.tools.Stopwatch;
import edu.duke.cs.osprey.tupexp.LUTESettings;

public class BenchmarkEmat extends TestBase {
	
	public static void main(String[] args)
	throws Exception {
		
		initDefaultEnvironment();
		
		// don't use energy function-level parallelism
		MultiTermEnergyFunction.setNumThreads(1);
		
		// make a search problem
		System.out.println("Building search problem...");
		
		ResidueFlexibility resFlex = new ResidueFlexibility();
		resFlex.addMutable("39 43", "ALA VAL LEU ILE");
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
		
		System.out.println("\nCalculating reference emat...");
		Stopwatch baseStopwatch = new Stopwatch().start();
		EnergyMatrixCalculator emcalc = new EnergyMatrixCalculator(search.confSpace, search.shellResidues, useERef, addResEntropy);
		emcalc.calcPEM();
		search.emat = emcalc.getEMatrix();
		baseStopwatch.stop();
		System.out.println("finished in " + baseStopwatch.getTime());
		
		ForcefieldParams ffparams = makeDefaultFFParams();
		
		// benchmark cpu
		int[] numThreadsList = { 1, 2, 4 };
		for (int numThreads : numThreadsList) {
			
			System.out.println("\nBenchmarking Emat calculation, " + numThreads + " CPU thread(s)...");
			SimpleEnergyMatrixCalculator ecalc = new SimpleEnergyMatrixCalculator.Cpu(numThreads, ffparams, search.confSpace, search.shellResidues);
			
			Stopwatch taskStopwatch = new Stopwatch().start();
			EnergyMatrix emat = ecalc.calcEnergyMatrix();
			taskStopwatch.stop();
			ecalc.cleanup();
			System.out.println(String.format("Speedup: %.2fx", (float)baseStopwatch.getTimeNs()/taskStopwatch.getTimeNs()));
			checkEmat(search.emat, emat);
		}
		
		// benchmark gpu
		int[] numStreamsList = { 1, 2, 4, 8, 16 };
		for (int numStreams : numStreamsList) {
			
			System.out.println("\nBenchmarking Emat calculation, " + numStreams + " GPU stream(s)...");
			
			@SuppressWarnings("deprecation")
			SimpleEnergyMatrixCalculator ecalc = new SimpleEnergyMatrixCalculator.Cuda(1, numStreams, ffparams, search.confSpace, search.shellResidues);
			
			Stopwatch taskStopwatch = new Stopwatch().start();
			EnergyMatrix emat = ecalc.calcEnergyMatrix();
			taskStopwatch.stop();
			ecalc.cleanup();
			System.out.println(String.format("Speedup: %.2fx", (float)baseStopwatch.getTimeNs()/taskStopwatch.getTimeNs()));
			checkEmat(search.emat, emat);
		}
	}

	private static void checkEmat(EnergyMatrix exp, EnergyMatrix obs) {
		
		for (int pos1=0; pos1<exp.getNumPos(); pos1++) {
			for (int rc1=0; rc1<exp.getNumConfAtPos(pos1); rc1++) {
			
				// singles
				checkEnergy(exp.getOneBody(pos1, rc1), obs.getOneBody(pos1, rc1));
				
				// pairs
				for (int pos2=0; pos2<pos1; pos2++) {
					for (int rc2=0; rc2<exp.getNumConfAtPos(pos2); rc2++) {
						
						checkEnergy(exp.getPairwise(pos1, rc1, pos2, rc2), obs.getPairwise(pos1, rc1, pos2, rc2));
					}
				}
			}
		}
	}

	private static void checkEnergy(double exp, double obs) {
		
		final double Epsilon = 1e-4;
		
		double absErr = Math.abs(exp - obs);
		double relErr = absErr/Math.abs(exp);
		if (relErr > Epsilon) {
			System.out.println(String.format("\tWARNING: low energy precision!  expected: %12.6f  observed: %12.6f  absErr: %e  relErr: %e",
				exp, obs, absErr, relErr
			));
		}
	}
}
