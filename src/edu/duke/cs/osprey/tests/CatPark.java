/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.tests;

import edu.duke.cs.osprey.bbfree.BBFreeDOF;
import edu.duke.cs.osprey.confspace.RC;
import edu.duke.cs.osprey.confspace.SearchProblem;
import edu.duke.cs.osprey.control.ConfigFileParser;
import edu.duke.cs.osprey.control.EnvironmentVars;
import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.dof.deeper.RamachandranChecker;
import edu.duke.cs.osprey.restypes.HardCodedResidueInfo;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.PDBFileReader;
import edu.duke.cs.osprey.structure.PDBFileWriter;
import edu.duke.cs.osprey.structure.Residue;
import edu.duke.cs.osprey.tools.VectorAlgebra;
import java.io.File;
import java.util.ArrayList;

/**
 *
 * Playground for CATS
 * Using it to make a figure for the paper
 * Meant to run in 2O9S_L2 directory
 * http://www.catster.com/lifestyle/why-there-is-no-such-thing-as-a-cat-park
 * 
 * @author mhall44
 */
public class CatPark {
    
    public static void main(String args[]){
        
        //for pep plane fig.  Changing res1 psi to show constraints still the same
        /*EnvironmentVars.assignTemplatesToStruct = false;
        Molecule m = PDBFileReader.readPDBFile("diglycine.pdb");
        Residue res1 = m.residues.get(0);
        Residue res2 = m.residues.get(1);
        DihedralRotation dr = new DihedralRotation(res1.getCoordsByAtomName("CA"),
                res1.getCoordsByAtomName("C"), 30);
        dr.transform(res1.coords, res1.getAtomIndexByName("O"));//only O of res1 moves
        dr.transform(res2.coords);//all of res2 moves
        PDBFileWriter.writePDBFile(m, "diglycine.rot.pdb");
        System.exit(0);*/
        
        //For RMSDs
        //EnvironmentVars.assignTemplatesToStruct = false;
        //double rmsds[] = segmentRMSD("/Users/mhall44/Mark/bbfree/recomb/figs/w54/W554.1block.GMEC.pdb",
        //        "/Users/mhall44/Mark/bbfree/recomb/figs/w54/W554.rigidBB.GMEC.pdb");
        // OR 
        //calcRMSDs(args);        
        //System.exit(0);
        
        ConfigFileParser cfp = new ConfigFileParser(args);//args 1, 3+ are configuration files
	cfp.loadData();
        
        SearchProblem sp = cfp.getSearchProblem();
        
        //all RCs have the same BB flex, so just pick one
        //also all BB dofs should have the same bounds, +/- bbBound
        double bbBound = Double.NaN;
        RC rc = sp.confSpace.posFlex.get(0).RCs.get(0);
        
        ArrayList<BBFreeDOF> bbDOFs = new ArrayList<>();
        for(int dofCount=0; dofCount<rc.DOFs.size(); dofCount++){
            DegreeOfFreedom dof = rc.DOFs.get(dofCount);
            if(dof instanceof BBFreeDOF){
                bbDOFs.add((BBFreeDOF)dof);
                bbBound = rc.DOFmax.get(dofCount);
            }
        }
        
        System.out.println("bbBound: "+bbBound);
        System.out.println("Number of CATS DOFs: "+bbDOFs.size());
        
        
        //to do :
        //1. show confs at corners of 2-D projection
        PDBFileWriter.writePDBFile(sp.confSpace.m, "proj_00.pdb");
        bbDOFs.get(0).apply(bbBound);
        PDBFileWriter.writePDBFile(sp.confSpace.m, "proj_10.pdb");
        bbDOFs.get(1).apply(bbBound);
        PDBFileWriter.writePDBFile(sp.confSpace.m, "proj_11.pdb");
        
        //2. Show the boundaries of the voxel in (phi,psi) space for residue (middle of loop)
        Residue midRes = sp.confSpace.m.getResByPDBResNumber("856");
        System.out.println("Going around the border of the 2-D projection of the voxel. Columns: bbdof 1, bbdof 2, "
                + "(phi,psi,N x coord, CA x coord) for res 856");
        
        
        double voxCorners[][] = new double[][] {
            new double[] {-bbBound,-bbBound},
            new double[] {-bbBound,bbBound},
            new double[] {bbBound,bbBound},
            new double[] {bbBound,-bbBound}
        };
        
        for(int edge=0; edge<4; edge++){
            for(double step=0; step<1; step+=0.01){
                for(int d=0; d<2; d++){
                    double val = (1-step)*voxCorners[edge][d] + step*voxCorners[(edge+1)%4][d];
                    System.out.print(val+" ");
                    bbDOFs.get(d).apply(val);
                }
                double phiPsi[] = RamachandranChecker.getPhiPsi(midRes);
                double xn = midRes.getCoordsByAtomName("N")[0];
                double can = midRes.getCoordsByAtomName("CA")[0];
                System.out.println(phiPsi[0] + " " + phiPsi[1]+" " +xn + " " + can);
            }
        }
        
        for(BBFreeDOF bbDOF : bbDOFs)
            bbDOF.apply(bbBound);
        PDBFileWriter.writePDBFile(sp.confSpace.m, "corner+.pdb");
        
        for(BBFreeDOF bbDOF : bbDOFs)
            bbDOF.apply(-bbBound);
        PDBFileWriter.writePDBFile(sp.confSpace.m, "corner-.pdb");
    }
    

    
    public static double[] segmentRMSD(String struct1, String struct2){
        //Compares two structures with backbones differing in a segment, e.g. CATS and rigid bb 
        //Returns {backbone RMSD, max residue backbone RMSD} for the flexible backbone segment
        Molecule m1 = PDBFileReader.readPDBFile(struct1);
        Molecule m2 = PDBFileReader.readPDBFile(struct2);
        
        int numRes = m1.residues.size();
        if(numRes != m2.residues.size())
            throw new RuntimeException("ERROR: Molecules have different numbers of residues!");
        
        double msd = 0;
        double maxResMSD = 0;
        int atCount = 0;
        
        for(int resNum=0; resNum<numRes; resNum++){
            Residue res1 = m1.residues.get(resNum);
            Residue res2 = m2.residues.get(resNum);
            double resMSD = 0;
            int resAtCount = 0;
            
            for(String name : HardCodedResidueInfo.possibleBBAtoms){
                double atCoords1[] = res1.getCoordsByAtomName(name);
                if(atCoords1 != null){
                    double atCoords2[] = res2.getCoordsByAtomName(name);
                    if(atCoords2==null)
                        throw new RuntimeException("ERROR: Molecules have different backbone atoms");
                    
                    double dist = VectorAlgebra.distance(atCoords1, atCoords2);
                    resMSD += dist*dist;
                    resAtCount ++;
                }
            }
            
            if(resMSD/resAtCount>1e-6){//nonnegligible backbone movement...consider part of flexible segment
                msd += resMSD;
                atCount += resAtCount;
                resMSD /= resAtCount;
                maxResMSD = Math.max(resMSD, maxResMSD);
            }
        }
        
        msd /= atCount;
        return new double[] {Math.sqrt(msd),Math.sqrt(maxResMSD)};
    }
    
    
    static void calcRMSDs(String args[]){
        //tabulate RMSDs for specified runs
        EnvironmentVars.assignTemplatesToStruct = false;
        
        String systems[] = new String[] {
            "1AHO_N",
            "1C75_N",
            "1CC8_L",
            "1F94_M",
            "1FK5_N",
            "1I27_N",
            "1IQZ_M",
            "1l6w_N",
            "1l7a_N",
            "1l7l_L",
            "1l7m_N",
            "1l8n_L",
            "1l8r_L",
            "1l9l_N",
            "1l9x_M",
            "1lb3_N",
            "1M1Q_L",
            "1MJ4_M",
            "2CS7_L2",
            "2O9S_L2",
            "2P5K_L2",
            "2QSK_L2",
            "2R2Z_L2",
            "2RH2_M2",
            "2RIL_M2",
            "2WJ5_M2",
            "2ZXY_M2",
            "3A38_M2"
        };
        
        for(String sysName : systems){
            //first file might not exist, check if it does
            String fileName1 = makeSysFileName(args[0],sysName);
            File file1 = new File(fileName1);
            if(file1.exists()){
                String fileName2 = makeSysFileName(args[1],sysName);
                double rmsds[] = segmentRMSD(fileName1, fileName2);
                System.out.println(sysName+" "+rmsds[0]+" "+rmsds[1]);
            }
            else
                System.out.println(sysName);
        }
    }
    
    private static String makeSysFileName(String folder, String sysName){
        if(sysName.endsWith("2"))
            return folder+"/"+sysName+"/"+sysName+".GMEC.pdb";
        else
            return folder+"/"+sysName+"/"+sysName.substring(0,4)+".GMEC.pdb";
    }
}
