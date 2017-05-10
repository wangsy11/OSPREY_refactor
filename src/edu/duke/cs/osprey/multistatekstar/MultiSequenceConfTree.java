package edu.duke.cs.osprey.multistatekstar;

import java.util.ArrayList;
import java.util.HashMap;

import edu.duke.cs.osprey.astar.ConfTree;
import edu.duke.cs.osprey.astar.FullAStarNode;
import edu.duke.cs.osprey.confspace.HigherTupleFinder;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.pruning.PruningMatrix;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 * 
 * Multi-Sequence conformation tree used to enumerate energies for partial
 * conformations.
 * 
 */

@SuppressWarnings("serial")
public class MultiSequenceConfTree extends ConfTree<FullAStarNode> {

	public boolean energyLBs;//compute either energy lower bound or upper bound
	private MSSearchProblem search;//search problem
	private PruningMatrix pmat;//pruning matrix
	private HashMap<Integer, Integer> index2AbsolutePos;//maps from index to absolute position space
	//map from index to absolutePos. a* only sees index space. map back 
	//to absolute pos only when accessing the energy matrix or pruning matrix
	private RCTuple absoluteTuple;//tuple with positions converted to abs pos

	public MultiSequenceConfTree(MSSearchProblem search, EnergyMatrix emat, PruningMatrix pmat) {
		super(new FullAStarNode.Factory(search.getNumAssignedPos()), search, pmat);
		this.energyLBs = search.settings.energyLBs;
		this.search = search;
		this.emat = emat;
		this.pmat = pmat;
		this.index2AbsolutePos = new HashMap<Integer, Integer>();
		this.absoluteTuple = new RCTuple();
		init();
		setVerbose(false);
	}

	protected Integer[] getPosNums(boolean defined) {
		ArrayList<Integer> ans = search.getPosNums(defined);
		return ans.toArray(new Integer[ans.size()]);
	}

	protected void init() {
		Integer[] allowedPos = getPosNums(true);
		numPos = allowedPos.length;

		//map from index to absolutePos. a* only sees index space. map back 
		//to absolute pos only when accessing the energy matrix or pruning matrix
		for(int i=0;i<numPos;++i) index2AbsolutePos.put(i, allowedPos[i]);
		Integer[] notAllowed = getPosNums(false);
		for(int i=0;i<notAllowed.length;++i) index2AbsolutePos.put(i+numPos, notAllowed[i]);
		assert(index2AbsolutePos.size()==search.confSpace.numPos);

		definedPos = new int[numPos];
		definedRCs = new int[numPos];
		undefinedPos = new int[numPos];
		childConf = new int[numPos];

		// see which RCs are unpruned and thus available for consideration
		// pack them into an efficient int matrix
		unprunedRCsAtPos = new int[search.confSpace.numPos][];
		for (int pos=0;pos<unprunedRCsAtPos.length;++pos) {//store all assigned and unassigned
			ArrayList<Integer> srcRCs = pmat.unprunedRCsAtPos(index2AbsolutePos.get(pos));
			int[] destRCs = new int[srcRCs.size()];
			for (int i=0; i<srcRCs.size(); i++) {
				destRCs[i] = srcRCs.get(i);
			}
			unprunedRCsAtPos[pos] = destRCs;
		}

		//get the appropriate energy matrix to use in this A* search
		if(search.useTupExpForSearch)
			emat = search.tupExpEMat;
		else {
			emat = search.emat;

			if(search.useEPIC){//include EPIC in the search
				useRefinement = true;
				epicMat = search.epicMat;
				confSpace = search.confSpace;
				minPartialConfs = search.epicSettings.minPartialConfs;
			}
		}
	}

	//convert to absolute positions
	private RCTuple setAbsolutePos(RCTuple other) {
		absoluteTuple.set(other);
		for(int i=0;i<absoluteTuple.size();++i) {
			absoluteTuple.pos.set(i, index2AbsolutePos.get(absoluteTuple.pos.get(i)));
		}
		return absoluteTuple;
	}

	protected double scoreNode(int[] partialConf) {
		if(traditionalScore) {
			rcTuple.set(partialConf);
			absoluteTuple = setAbsolutePos(rcTuple);

			//"g-score"
			double score = emat.getConstTerm() + emat.getInternalEnergy(absoluteTuple);//intra+pairwise

			//"h-score"
			//score works by breaking up the full energy into the energy of the defined set of residues ("g-score"),
			//plus contributions associated with each of the undefined res ("h-score")

			for(int pos=0; pos<search.confSpace.numPos;++pos) {
				if(rcTuple.pos.contains(pos)) continue;//skip positions assigned in rc tuple
				double bestE = energyLBs ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
				for(int rc : unprunedRCsAtPos[pos]) {
					double undefE = getUndefinedRCEnergy(pos, rc, rcTuple);
					bestE = energyLBs ? Math.min(bestE, undefE) : Math.max(bestE, undefE);
				}
				score += bestE;
			}
			return score;
		}

		else {
			//other possibilities include MPLP, etc.
			//But I think these are better used as refinements
			//we may even want multiple-level refinement
			throw new UnsupportedOperationException("Advanced A* scoring methods not implemented yet!");
		}
	}

	protected double getUndefinedRCEnergy(int pos1, int rc1, RCTuple definedTuple) {
		//Provide a lower bound on what the given rc at the given level can contribute to the energy
		//assume partialConf and definedTuple

		double rcContrib = emat.getOneBody(index2AbsolutePos.get(pos1), rc1);

		//for this kind of lower bound, we need to split up the energy into the defined-tuple energy
		//plus "contributions" for each undefined residue
		//so we'll say the "contribution" consists of any interactions that include that residue
		//but do not include higher-numbered undefined residues
		for(int pos2 = 0; pos2 < search.confSpace.numPos; pos2++){

			if(definedTuple.pos.contains(pos2) || pos2 < pos1) {//defined or lower numbered residues

				double posBestE = energyLBs ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;//best pairwise energy

				for(int rc2 : unprunedRCsAtPos[pos2]) {

					double interactionE = emat.getPairwise(index2AbsolutePos.get(pos1), rc1, index2AbsolutePos.get(pos2), rc2);
					double higherOrderE = higherOrderContrib(pos1, rc1, pos2, rc2, definedTuple);
					interactionE += higherOrderE;

					posBestE = energyLBs ? Math.min(posBestE, interactionE) : Math.max(posBestE, interactionE);
				}

				rcContrib += posBestE;
			}
		}

		return rcContrib;
	}

	protected double higherOrderContrib(int pos1, int rc1, int pos2, int rc2, RCTuple definedTuple) {
		//higher-order contribution for a given RC pair, when scoring a partial conf

		HigherTupleFinder<Double> htf = emat.getHigherOrderTerms(index2AbsolutePos.get(pos1), rc1, index2AbsolutePos.get(pos2), rc2);

		if(htf==null)
			return 0;//no higher-order interactions
		else {
			RCTuple curPair = new RCTuple(index2AbsolutePos.get(pos1), rc1, index2AbsolutePos.get(pos2), rc2);
			absoluteTuple = setAbsolutePos(definedTuple);
			return higherOrderContrib(htf, curPair, absoluteTuple);
		}
	}

	@SuppressWarnings("unchecked")
	double higherOrderContrib(HigherTupleFinder<Double> htf, RCTuple startingTuple,
			RCTuple definedTuple) {
		//recursive function to get bound on higher-than-pairwise terms
		//this is the contribution to the bound due to higher-order interactions
		//of the RC tuple startingTuple (corresponding to htf)

		double contrib = 0;

		//to avoid double-counting, we are just counting interactions of starting tuple
		//with residues before the "earliest" one (startingLevel) in startingTuple
		//"earliest" means lowest-numbered, except non-mutating res come before mutating
		int startingLevel = startingTuple.pos.get( startingTuple.pos.size()-1 );

		for(int iPos : htf.getInteractingPos()){//position has higher-order interaction with tup
			if(posComesBefore(iPos,startingLevel,definedTuple)) {//interaction in right order
				//(want to avoid double-counting)

				double posBestE = energyLBs ? Double.POSITIVE_INFINITY : 
					Double.NEGATIVE_INFINITY;//best value of contribution from tup-iPos interaction

				for(int rc : unprunedRCsAtPos[iPos]) {

					RCTuple augTuple = startingTuple.addRC(iPos, rc);

					double interactionE = htf.getInteraction(iPos, rc);

					//see if need to go up to highers order again...
					@SuppressWarnings("rawtypes")
					HigherTupleFinder htf2 = htf.getHigherInteractions(iPos, rc);
					if(htf2!=null){
						interactionE += higherOrderContrib(htf2, augTuple, definedTuple);
					}

					posBestE = energyLBs ? Math.min(posBestE, interactionE) : Math.max(posBestE, interactionE);
				}

				contrib += posBestE;//add up contributions from different interacting positions iPos
			}
		}

		return contrib;
	}

	protected boolean posComesBefore(int pos1, int pos2, RCTuple definedTuple){
		//for purposes of contributions to traditional conf score, 
		//we go through defined and then through undefined positions (in partialConf);
		//within each of these groups we go in order of position number
		if(definedTuple.pos.contains(pos2)){//pos2 defined
			return (pos1<pos2 && definedTuple.pos.contains(pos1));//pos1 must be defined to come before pos2
		}
		else//pos1 comes before pos2 if it's defined, or if pos1<pos2
			return (pos1<pos2 || definedTuple.pos.contains(pos1));
	}

	protected double scoreConfDifferential(FullAStarNode parentNode, int childPos, int childRc) {
		assertSplitPositions();

		// OPTIMIZATION: this function gets hit a LOT!
		// so even really pedantic optimizations can make an impact

		// get the full conf, start with the parent first
		int[] conf = parentNode.getNodeAssignments();

		// but if this is actually a child node, switch to the child conf
		if (childPos >= 0) {

			// parent shouldn't be assigned here
			assert (conf[childPos] < 0);

			// make the child conf
			System.arraycopy(conf, 0, childConf, 0, numPos);
			childConf[childPos] = childRc;
			conf = childConf;
		}

		double ans = scoreNode(conf);
		return ans;
	}

}
