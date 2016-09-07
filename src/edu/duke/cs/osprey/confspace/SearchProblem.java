/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.confspace;

import java.io.Serializable;
import java.util.ArrayList;

import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.dof.deeper.DEEPerSettings;
import edu.duke.cs.osprey.dof.deeper.perts.Perturbation;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.EnergyMatrixCalculator;
import edu.duke.cs.osprey.ematrix.ReferenceEnergies;
import edu.duke.cs.osprey.ematrix.SimpleEnergyCalculator;
import edu.duke.cs.osprey.ematrix.SimpleEnergyMatrixCalculator;
import edu.duke.cs.osprey.ematrix.epic.EPICMatrix;
import edu.duke.cs.osprey.ematrix.epic.EPICSettings;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.energy.EnergyFunctionGenerator;
import edu.duke.cs.osprey.energy.GpuEnergyFunctionGenerator;
import edu.duke.cs.osprey.kstar.KSTermini;
import edu.duke.cs.osprey.parallelism.ThreadPoolTaskExecutor;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.structure.Residue;
import edu.duke.cs.osprey.tools.ObjectIO;
import edu.duke.cs.osprey.tupexp.ConfETupleExpander;
import edu.duke.cs.osprey.tupexp.LUTESettings;
import edu.duke.cs.osprey.tupexp.TupExpChooser;
import edu.duke.cs.osprey.voxq.VoxelGCalculator;

/**
 *
 * @author mhall44
 */
public class SearchProblem implements Serializable {
    //This object keeps track of the positions and the possible assignments for them, as used in the search algorithms
    //generally these will be residues (or super-residues) and their RCs; subclass SearchProblem to change this
    
    //We keep a ConfSpace together with "annotations" that help us find its GMEC, partition functions, etc.
    //annotations are based on RCs and indicate pairwise energies, pruning information, etc.
    //they also include iterators over RCs and pairs of interest
    
	private static final long serialVersionUID = 2590525329048496524L;

    
    public ConfSpace confSpace;
    
    public EnergyMatrix emat;//energy matrix.  Meanings:
    //-Defines full energy in the rigid case
    //-Defines lower bound in the continuous case
    //-emat + epicm = full energy if using EPIC for search 
    
    public EPICMatrix epicMat = null;//EPIC matrix, to be used if appropriate
    public EPICSettings epicSettings = null;
    public LUTESettings luteSettings = null;
    
    public EnergyMatrix tupExpEMat;//Defines full energy in the continuous, tuple-expander case
    
    public EnergyFunction fullConfE;//full energy for any conformation
    public ArrayList<Residue> shellResidues;//non-flexible residues to be accounted for in energy calculations
    
    public String name;//a human-readable name, which will also be used to name stored energy matrices, etc.
    
    public PruningMatrix pruneMat;
    
    public boolean contSCFlex;
    public boolean useVoxelG = false;//use the free energy of each voxel instead of minimized energy
    VoxelGCalculator gCalc = null;
    
    public PruningMatrix competitorPruneMat;//a pruning matrix performed at pruning interval 0,
    //to decide which RC tuples are valid competitors for pruning
    
        
    
    public boolean useEPIC = false;
    public boolean useTupExpForSearch = false;//use a tuple expansion to approximate the energy as we search
    
    
    public boolean useERef = false;
    public boolean addResEntropy = false;
    
    public int numEmatThreads = 1;
    
    
    public SearchProblem(SearchProblem sp1){//shallow copy
    	confSpace = sp1.confSpace;
    	emat = sp1.emat;
        epicMat = sp1.epicMat;
        epicSettings = sp1.epicSettings;
        luteSettings = sp1.luteSettings;
        tupExpEMat = sp1.tupExpEMat;
        
    	fullConfE = sp1.fullConfE;
    	shellResidues = sp1.shellResidues;
    	name = sp1.name + System.currentTimeMillis();//probably will want to change this to something more meaningful
        
    	pruneMat = sp1.pruneMat;
    	competitorPruneMat = sp1.competitorPruneMat;
        
    	contSCFlex = sp1.contSCFlex;
    	useEPIC = sp1.useEPIC;
    	useTupExpForSearch = sp1.useTupExpForSearch;
        
        useERef = sp1.useERef;
        addResEntropy = sp1.addResEntropy;
    }
    
    
    
    public SearchProblem(String name, String PDBFile, ArrayList<String> flexibleRes, ArrayList<ArrayList<String>> allowedAAs, boolean addWT,
            boolean contSCFlex, boolean useEPIC, EPICSettings epicSettings, boolean useTupExp, LUTESettings luteSettings, DEEPerSettings dset, 
            ArrayList<String[]> moveableStrands, ArrayList<String[]> freeBBZones, boolean useEllipses, boolean useERef,
            boolean addResEntropy, boolean addWTRots, KSTermini termini, boolean useVoxelG){
        
        confSpace = new ConfSpace(PDBFile, flexibleRes, allowedAAs, addWT, contSCFlex, dset, moveableStrands, freeBBZones, useEllipses, addWTRots, termini);
        this.name = name;
        
        
        this.contSCFlex = contSCFlex;
        this.useTupExpForSearch = useTupExp;
        this.useEPIC = useEPIC;
        this.epicSettings = epicSettings;
        this.luteSettings = luteSettings;
        
        this.useERef = useERef;
        this.addResEntropy = addResEntropy;
        this.useVoxelG = useVoxelG;
        
        //energy function setup
        EnergyFunctionGenerator eGen = EnvironmentVars.curEFcnGenerator;
        decideShellResidues(eGen.distCutoff);
        fullConfE = eGen.fullConfEnergy(confSpace,shellResidues);
    }
    
    
    
    private void decideShellResidues(double distCutoff){
        //Decide what non-flexible residues need to be accounted for in energy calculations
        
        ArrayList<Residue> flexibleResidues = new ArrayList<>();//array list for these so they stay in order
        for(PositionConfSpace pcs : confSpace.posFlex)
            flexibleResidues.add(pcs.res);
        
        //we'll decide what shell residues to include by using a simple distance cutoff with
        //the current conformation,
        //rather than doing a conformational search for the minimal distance (with respect to conformations)
        //of a shell residue to any flexible residues
        //the distance cutoff can be increased to accommodate this if desired.
        shellResidues = new ArrayList<>();
        
        for(Residue nonFlexRes : confSpace.m.residues){
            if(!flexibleResidues.contains(nonFlexRes)){//residue is not flexible
                
                for(Residue flexRes : flexibleResidues){
                    double dist = flexRes.distanceTo(nonFlexRes);
                    if(dist<=distCutoff){
                        shellResidues.add(nonFlexRes);//close enough to flexRes that we should add it
                        break;
                    }
                }
            }
        }
    }
    
    
    public double minimizedEnergy(int[] conf){
        //Minimized energy of the conformation
        //whose RCs are listed for all flexible positions in conf
        if(useVoxelG)//use free instead of minimized energy
            return gCalc.calcG(conf);
        
        double E = confSpace.minimizeEnergy(conf, fullConfE, null);
        
        if(useERef)
            E -= emat.geteRefMat().confERef(conf);
        
        if(addResEntropy)
            E += confSpace.getConfResEntropy(conf);            
        
        return E;
    }
    
    public void outputMinimizedStruct(int[] conf, String PDBFileName){
        //Output minimized conformation to specified file
        //RCs are listed for all flexible positions in conf
        //Note: eRef not included (just minimizing w/i voxel)
        confSpace.minimizeEnergy(conf, fullConfE, PDBFileName);
    }
    
    
    public double approxMinimizedEnergy(int[] conf){
        //EPIC or other approximation for the minimized energy of the conformation
        //whose RCs are listed for all flexible positions in conf
        
        if( useTupExpForSearch ){//use tup-exp E-matrix directly
            return tupExpEMat.confE(conf);
        }
        else if( useEPIC ){//EPIC w/o tup-exp
            return EPICMinimizedEnergy(conf);
        }
        
        else
            throw new RuntimeException("ERROR: Asking searchSpace to approximate minimized energy but using a non-approximating method");
    }
    
    
    public double voxelFreeEnergy(int[] conf){
        if(gCalc==null)
            throw new RuntimeException("ERROR: Free energy calculator is null (probably no EPIC matrix loaded)");
            
        return gCalc.calcG(conf);
    }
    
    public double EPICMinimizedEnergy(int[] conf){
        //approximate energy using EPIC
        if(useVoxelG)
            return voxelFreeEnergy(conf);
        
        double bound = emat.confE(conf);//emat contains the pairwise lower bounds
        double contPart = epicMat.minContE(conf);
        //EPIC handles the continuous part (energy - pairwise lower bounds)

        return bound+contPart;
    }
    
    
    
    public double lowerBound(int[] conf){
        //Argument: RC assignments for all the flexible residues (RCs defined in resFlex)
        //return lower bound on energy for the conformational space defined by these assignments
        //based on precomputed energy matrix (including EPIC if indicated)
        
        double bound = emat.confE(conf);//the energy recorded by the matrix is 
        //the pairwise lower bounds
        
        return bound;
    }
    
    
    
    
    
    //LOADING AND PRECOMPUTATION OF ENERGY MATRIX-TYPE OBJECTS (regular energy matrix, tup-exp and EPIC matrices)
    public void loadEnergyMatrix(){
        loadMatrix(MatrixType.EMAT);
    }
    
    public void loadTupExpEMatrix(){
        loadMatrix(MatrixType.TUPEXPEMAT);
    }
    
    public void loadEPICMatrix(){
        loadMatrix(MatrixType.EPICMAT);
        
        if(useVoxelG)
            gCalc = new VoxelGCalculator(this);
    }
    
    
    enum MatrixType {
        EMAT, TUPEXPEMAT, EPICMAT;
    }
    
    
    //load the specified matrix; if the right file isn't available then compute and store it
    private void loadMatrix(MatrixType type){
        
        String matrixFileName = name + "." + type.name() + ".dat";
        //matrix file names are determined by the name of the search problem
        
        if(!loadMatrixFromFile( type, matrixFileName )){
            TupleMatrix<?> matrix = calcMatrix(type);
            ObjectIO.writeObject( matrix, matrixFileName );
            loadMatrixFromFile( type, matrixFileName );
        }
    }
    
    
    //compute the matrix of the specified type
    private TupleMatrix<?> calcMatrix(MatrixType type){
    
        // TODO: the search problem shouldn't concern itself with energy matrices and how to compute them
        
        if(type == MatrixType.EMAT){
        	
        	// HACKHACK: need to find out if we're using deeper, which isn't supported by the concurrent molecule code yet
        	// we'd need to implement the copy() and setMolecule() methods on Perturbation DOFs to enable compatibility
			boolean usingDEEPer = false;
			for (DegreeOfFreedom dof : confSpace.confDOFs) {
				if (dof instanceof Perturbation) {
					usingDEEPer = true;
					break;
				}
			}
			if (usingDEEPer) {
				System.out.println("\n\nWARNING: DEEPer perturbations detected, concurrent energy matrix calculations disabled due to temporary incompatibility\n");
			}
        	
            // if we're using MPI or DEEPer, use the old energy matrix calculator
            if (EnvironmentVars.useMPI || usingDEEPer) {
                
                // see if the user tried to use threads too for some reason and try to be helpful
                if (EnvironmentVars.useMPI && numEmatThreads > 1) {
                    System.out.println("\n\nWARNING: multiple threads and MPI both configured for emat calculation."
                        + " Ignoring thread settings and using only MPI.\n");
                }
                
                EnergyMatrixCalculator emCalc = new EnergyMatrixCalculator(confSpace, shellResidues, useERef, addResEntropy);
                emCalc.calcPEM();
                return emCalc.getEMatrix();
            
            } else {
            
                // otherwise, use the new multi-threaded calculator (which doesn't support MPI)
                
                // where do energy functions come from?
                EnergyFunctionGenerator egen = EnvironmentVars.curEFcnGenerator;
                if (egen instanceof GpuEnergyFunctionGenerator) {
                    System.out.println("\n\nWARNING: using the GPU to compute energy matrices is a Bad Idea."
                        + " It's very slow at small residue-pair forcefields compared to the CPU."
                        + " You'll probably be better off using multi-threaded CPU parallelism instead.\n");
                }
                
                // how many threads should we use?
                ThreadPoolTaskExecutor tasks = null;
                if (numEmatThreads > 1) {
                    tasks = new ThreadPoolTaskExecutor();
                    tasks.start(numEmatThreads);
                }
                
                // calculate the emat! Yeah!
                SimpleEnergyCalculator ecalc = new SimpleEnergyCalculator(egen, confSpace, shellResidues);
                EnergyMatrix emat = new SimpleEnergyMatrixCalculator(ecalc).calcEnergyMatrix(tasks);
                
                // cleanup
                if (tasks != null) {
                	tasks.stop();
                }
                
                // need to subtract reference energies?
                if (useERef) {
                    System.out.println("Computing reference energies...");
                    emat.seteRefMat(new ReferenceEnergies(confSpace));
                }
                
                // need to add entropies?
                if (addResEntropy) {
                    System.out.println("Computing residue entropies...");
                	for (int pos=0; pos<emat.getNumPos(); pos++) {
                		for (int rc=0; rc<emat.getNumConfAtPos(pos); rc++) {
                			double energy = emat.getOneBody(pos, rc);
                			energy += confSpace.getRCResEntropy(pos, rc);
                			emat.setOneBody(pos, rc, energy);
                		}
                	}
                }
                
                return emat;
            }
        }
        else if(type == MatrixType.EPICMAT){
            EnergyMatrixCalculator emCalc = new EnergyMatrixCalculator(confSpace,shellResidues,
                    pruneMat,epicSettings);
            emCalc.calcPEM();
            return emCalc.getEPICMatrix();
        }
        else {
            //need to calculate a tuple-expansion matrix
            
            ConfETupleExpander expander = new ConfETupleExpander(this);//make a tuple expander
            
            
            TupleEnumerator tupEnum = new TupleEnumerator(pruneMat,emat,confSpace.numPos);
            TupExpChooser chooser = new TupExpChooser(expander, tupEnum);//make a chooser to choose what tuples will be in the expansion
            
            double curResid = chooser.calcPairwiseExpansion();//start simple...
            
            if(curResid > luteSettings.goalResid){//go to triples if needed
                System.out.println("EXPANDING PAIRWISE EXPANSION WITH STRONGLY PAIR-INTERACTING TRIPLES (2 PARTNERS)...");
                curResid = chooser.calcExpansionResTriples(2);
            }
            if(curResid > luteSettings.goalResid){//go to 5 partners if still need better resid...
                System.out.println("EXPANDING EXPANSION WITH STRONGLY PAIR-INTERACTING TRIPLES (5 PARTNERS)...");
                curResid = chooser.calcExpansionResTriples(5);
            }
            if(curResid > luteSettings.goalResid){
                System.out.println("WARNING: Desired LUTE residual threshold "+
                        luteSettings.goalResid+" not reached; best="+curResid);
            }
            
            return expander.getEnergyMatrix();//get the final energy matrix from the chosen expansion
        }
    }

    
    
    boolean loadMatrixFromFile(MatrixType type, String matrixFileName){
        //try loading the specified matrix from a file
        //return true if successful, false if not, in which case we'll have to compute it
        //also if the matrix's pruning interval is too low, it may be missing some RCs
        //that are unpruned at our current pruningInterval, so we have to recompute
        Object matrixFromFile = ObjectIO.readObject(matrixFileName, true);
        
        if(type == MatrixType.EMAT)
            emat = (EnergyMatrix) matrixFromFile;
        else if(type == MatrixType.EPICMAT)
            epicMat = (EPICMatrix) matrixFromFile;
        else //tup-exp
            tupExpEMat = (EnergyMatrix) matrixFromFile;
        
        if(matrixFromFile==null)//unsuccessful loading leaves null emat
            return false;
        
        
        //check pruning interval.  Current interval is in pruneMat if we have pruned already;
        //if not then we need a matrix with infinite pruning interval (valid for all RCs).
        double matrixPruningInterval = ((TupleMatrix<?>)matrixFromFile).getPruningInterval();
        
        if( matrixPruningInterval == Double.POSITIVE_INFINITY )//definitely valid
            return true;
        else {
            //excludes some RCs...check against pruneMat pruning interval
            if(pruneMat==null){
                throw new RuntimeException("ERROR: Trying to load pruning-dependent tuple matrix"
                        + "(EPIC or tup-exp) but haven't pruned yet");
            }
            
            return ( matrixPruningInterval >= pruneMat.getPruningInterval() );
        }
    }
    
    
    
    public boolean searchNeedsHigherOrderTerms(){
        //Will a conformation search using this SearchProblem need to use
        //higher-than-pairwise terms?
        if(useTupExpForSearch)
            return tupExpEMat.hasHigherOrderTerms();
        else
            return emat.hasHigherOrderTerms();
    }
   
}
