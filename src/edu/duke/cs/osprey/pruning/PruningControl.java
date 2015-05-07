/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.pruning;

import edu.duke.cs.osprey.confspace.SearchSpace;

/**
 *
 * @author mhall44
 */
public class PruningControl {
    /*This class provides the high-level control of pruning: what series of pruning methods to use, etc.
     It thus provides a simple interface that K* or GMEC calculations can use for pruning
     */
    
    
    
    SearchSpace searchSpace;
    double Ew;//relative energy threshold for pruning (includes I for iMinDEE)
    boolean typeDep;//type-dependent pruning
    double boundsThresh;//absolute threshold (for Bounds pruning)
    int algOption;//1-4, for different levels of pruning
    boolean useFlags;//Use pair pruning
    boolean useTriples;//Use triple pruning
    boolean preDACS;//do pruning appropriate for all-conf-space pruning before DACS

    
    
    
    public PruningControl(SearchSpace searchSpace, double Ew, boolean typeDep, 
            double boundsThresh, int algOption, boolean useFlags, boolean useTriples, boolean preDACS) {
        this.searchSpace = searchSpace;
        this.Ew = Ew;
        this.typeDep = typeDep;
        this.boundsThresh = boundsThresh;
        this.algOption = algOption;
        this.useFlags = useFlags;
        this.useTriples = useTriples;
        this.preDACS = preDACS;
    }

    
    
    
    
    public void prune(){
        
        long startTime = System.currentTimeMillis();
        
        Pruner dee = new Pruner(searchSpace,typeDep,boundsThresh,Ew);
        
        //now go through the various types of pruning that we support
        //see KSParser
        
        //possibly start with steric pruning?  
        
        
        boolean done = false;
        
        //numbers pruned so far
        int numPrunedRot = 0;
        int numPrunedPairs = 0;
        
        for (int numRuns=0; !done; numRuns++){ //repeat the pruning cycle until no more rotamers are pruned	

            System.out.println("Starting DEE cycle run: "+numRuns);
				
		//		if (doMinimize && !localUseMinDEEPruningEw) //precompute the interval terms in the MinDEE criterion
		//			rs.doCompMinDEEIntervals(mp.numberMutable, mp.strandMut, prunedRotAtRes, scaleInt, maxIntScale);
			
            //Depending on the chosen algorithm option, apply the corresponding pruning criteria;			
            dee.prune("GOLDSTEIN");
            
            /*
            if ((algOption>=3)) //simple Goldstein pairs
                    dee.prune("GOLDSTEIN PAIRS MB");

            if ((useFlags)||(algOption>=3))
                    dee.prune("BOUNDING FLAGS");

            dee.prune("CONFSPLIT1");
            
            
            dee.prune("BOUNDS");
            */
            
            //check how many rotamers/pairs are pruned now
            int newNumPrunedRot = searchSpace.pruneMat.countPrunedRCs();
            int newNumPrunedPairs = 0;
            if ((useFlags)||(algOption>=3)) //pairs pruning is performed
                    newNumPrunedPairs = searchSpace.pruneMat.countPrunedPairs();

            
            if( (newNumPrunedRot==numPrunedRot) && (newNumPrunedPairs==numPrunedPairs) && (!preDACS) ) { 
            //no more rotamers pruned, so perform the computationally-expensive 2-sp split-Pruner and pairs

                    if ((algOption>=3)){ //simple Goldstein pairs
                        if ((algOption>=3)) //simple Goldstein pairs
                            dee.prune("GOLDSTEIN PAIRS FULL");
                    }
                    
                    /*
                    if ((algOption>=2)){ //2-sp conf splitting
                        dee.prune("CONFSPLIT2");
                    }

                    if( useTriples ){
                        dee.prune("GOLDSTEIN TRIPLES");
                    }

                    if(algOption >= 4){
                        dee.prune("INDIRECT PAIRS");
                        dee.prune("INDIRECT");
                    }
                    */

                    //check if 2-sp split-Pruner and pairs pruned new rotamers
                    newNumPrunedRot = searchSpace.pruneMat.countPrunedRCs();
                    newNumPrunedPairs = 0;
                    if ((useFlags)||(algOption>=3)) //pairs pruning is performed
                        newNumPrunedPairs = searchSpace.pruneMat.countPrunedPairs();
            }
            
            int numPrunedRotThisRun = newNumPrunedRot - numPrunedRot;
            int numPrunedPairsThisRun = newNumPrunedPairs - numPrunedPairs;
            
            
            System.out.println("Num pruned rot this run: "+numPrunedRotThisRun);
            System.out.println("Num pruned pairs this run: "+numPrunedPairsThisRun);
            System.out.println();
            
            if(numPrunedRotThisRun==0 && numPrunedPairsThisRun==0)
                done = true;
            
            numPrunedRot = newNumPrunedRot;
            numPrunedPairs = newNumPrunedPairs;
        }

        long pruneTime = System.currentTimeMillis() - startTime;
        
        System.out.println("Pruning time: " + pruneTime + " ms" );
    }
    
    
}
