package edu.duke.cs.osprey.kstar.pfunc.impl;

import java.io.Serializable;
import java.util.ArrayList;
import edu.duke.cs.osprey.confspace.ConfSearch;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.kstar.KSConf;
import edu.duke.cs.osprey.kstar.RCEnergyContribs;
import edu.duke.cs.osprey.kstar.pfunc.PFAbstract;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 *
 */
@SuppressWarnings("serial")
public class PFTrad extends PFAbstract implements Serializable {

	protected ConfSearch confSearch = null;

	// temp for benchmarking
	protected long startTime;

	public PFTrad() { 
		super();
	}

	public PFTrad( int strand, ArrayList<String> sequence, ArrayList<Integer> flexResIndexes, 
			String checkPointPath, String searchProblemName, 
			ConfigFileParser cfp, SearchProblem panSeqSP ) {

		super( strand, sequence, flexResIndexes, checkPointPath, searchProblemName, cfp, panSeqSP );
	}


	public void start() {

		setRunState(RunState.STARTED);

		initPStar();

		// first conf was merely to set p*
		confSearch = getConfTree(false);

		startTime = System.currentTimeMillis();
	}


	protected void iterate() throws Exception {

		int conf[];

		if( (conf = confSearch.nextConf()) != null ) {

			if( minimizedConfsSet.contains(conf) ) return;

			KSConf ksConf = new KSConf(conf, getConfBound(confSearch, conf, false));

			accumulate(ksConf);
		}

		else {
			// no more conformations, and we have not reached target epsilon
			if( eAppx == EApproxReached.FALSE )
				eAppx = EApproxReached.NOT_POSSIBLE;
		}
	}


	@Override
	protected void computeSlice() {

		try {

			iterate();

		} catch(Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}


	protected void compute() {

		try {

			while( eAppx == EApproxReached.FALSE ) {

				iterate();

			}

		} catch(Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}


	protected void accumulate( KSConf conf ) {

		double energy = 0, boundError = 0;
		
		if( isFullyDefined() ) {
			
			MultiTermEnergyFunction mef = sp.decomposedMinimizedEnergy(conf.getConfArray());
			energy = mef.getPreCompE();

			boundError = conf.getEnergyBound()-energy;
			
			if(useTripleBounds && boundError <= tripleThresh) {
				RCEnergyContribs rce = new RCEnergyContribs(this, mef, conf.getConfArray());
				double pbe = rce.getPercentBoundError(3);
				ArrayList<Integer> pce = rce.getPosCausingError(3);
				System.out.println("% bound error due to top 3 RCs: " + pbe + ". offending rotamers: " + pce);
			}
		}

		else
			energy = conf.getEnergyBound();

		/*
		energy = isFullyDefined() ? sp.minimizedEnergy(conf.getConfArray()) : conf.getEnergyBound();
		MultiTermEnergyFunction mef = sp.decomposedMinimizedEnergy(conf.getConfArray());
		double energy2 = mef.getPreCompE();
		if(energy - energy2 > 0.01) throw new RuntimeException("ERROR: multi-term get energy function is broken. energy: " + energy + " energy2: " + energy2);
		 */

		conf.setEnergy(energy);

		updateQStar( conf );

		Et = conf.getEnergyBound();
		updateQPrime();

		// negative values of effective esilon are disallowed
		if( (effectiveEpsilon = computeEffectiveEpsilon()) < 0 ) {

			eAppx = EApproxReached.NOT_POSSIBLE;

			return;
		}

		long currentTime = System.currentTimeMillis();

		if( !PFAbstract.suppressOutput ) {
			if( !printedHeader ) printHeader();

			System.out.println(boundError + "\t" + energy + "\t" + effectiveEpsilon + "\t" 
					+ getNumMinimized4Output() + "\t" + getNumUnEnumerated() + "\t"+ (currentTime-startTime)/1000);
		}

		eAppx = effectiveEpsilon <= targetEpsilon || maxKSConfsReached() ? EApproxReached.TRUE: EApproxReached.FALSE;

		// for partial sequences when doing KAstar
		if( !isFullyDefined() && eAppx == EApproxReached.TRUE ) adjustQStar();
	}


	protected void printHeader() {

		System.out.println("boundError" + "\t" + "minE" + "\t" + "epsilon" + "\t" + "#min" +
				"\t" + "#un-enum" + "\t" + "time(sec)");

		printedHeader = true;
	}


	public String getImpl() {
		return "trad";
	}


	public void cleanup() {
		super.cleanup();
		confSearch = null;
	}

}