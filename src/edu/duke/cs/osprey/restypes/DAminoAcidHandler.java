/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.restypes;

import edu.duke.cs.osprey.structure.Residue;
import edu.duke.cs.osprey.tools.ObjectIO;
import edu.duke.cs.osprey.tools.Protractor;
import java.util.LinkedHashMap;

/**
 *
 * This class deals with D-amino-acid specific operations
 * D amino acids are named as "DX" + one-letter code, e.g. "DXE" for D-Glu
 * Alternate prot states (e.g., Hid) have special names
 * 
 * @author mhall44
 */
public class DAminoAcidHandler {
    
    
    //D names for alternate prot states
    public static final LinkedHashMap<String,String> altProtStateDNames = new LinkedHashMap<>();

    //reverse lookup (D-->L)
    public static final LinkedHashMap<String,String> altProtStateLNames = new LinkedHashMap<>();
    
    static {
        altProtStateDNames.put("HID", "DHD");
        altProtStateDNames.put("HIE", "DHE");
        altProtStateDNames.put("HIP", "DHP");
        altProtStateDNames.put("CYX", "DCX");
        altProtStateDNames.put("CYM", "DCM");
        altProtStateDNames.put("ASH", "DDH");
        altProtStateDNames.put("GLH", "DEH");
        altProtStateDNames.put("LYN", "DKN");
        
        for(String LName : altProtStateDNames.keySet())
            altProtStateLNames.put(altProtStateDNames.get(LName), LName);
    }
    
    
    
    
    public static void tryRenamingAsD(Residue res){
        //See if the amino acid is named as a standard L-amino acid,
        //but is actually the L equivalent
        //Rename if so
        
        String origName = res.fullName.substring(0,3);
        
        if( isStandardLAminoAcid(origName) ){
            
            if( ! origName.equalsIgnoreCase("GLY")){//Gly is achiral
                //the rest all have the atoms CA, CB, HA, N, whose coords determine the chirality
                
                double coords[][] = new double[][] {
                    res.getCoordsByAtomName("CB"),
                    res.getCoordsByAtomName("CA"),
                    res.getCoordsByAtomName("N"),
                    res.getCoordsByAtomName("HA"),
                };
                
                for(int c=0; c<4; c++){
                    if(coords[c] == null)
                        throw new RuntimeException("ERROR: Can't check if " + res.fullName +
                                " is L or D (one or more of N, CA, CB, HA is missing)");
                }
                
                double ang = Protractor.measureDihedral(coords);
                
                if(ang<0)//D-amino acid!
                    res.fullName = getDName(origName) + res.fullName.substring(3);
            }
        }
    }
    
    
    public static ResidueTemplate makeDTemplate(ResidueTemplate LTemplate){
        //Given the template for a standard L-amino acid,
        //create its D version
        
        ResidueTemplate DTemplate = (ResidueTemplate) ObjectIO.deepCopy(LTemplate);
        String DName = getDName(LTemplate.name);
        
        if(DName==null){
            throw new RuntimeException("ERROR: Trying to make a D version of "
                    + " a template that isn't a standard L-amino acid: "+LTemplate.name);
        }
        
        //Change name to be D
        DTemplate.name = DName;
        Residue DTemplRes = DTemplate.templateRes;
        DTemplRes.fullName = DName + DTemplRes.fullName.substring(3);
        
        //invert coordinates, dihedrals, and pucker (if present)
        if(DTemplRes.coords != null){
            for(int c=0; c<DTemplRes.coords.length; c++)
                DTemplRes.coords[c] *= -1;
        }
    
        if(DTemplate.rotamericDihedrals != null){
            for(double[][][] phiDih : DTemplate.rotamericDihedrals){
                for(double[][] psiDih : phiDih){
                    for(double[] rotDih : psiDih){
                        for(int d=0; d<rotDih.length; d++)
                            rotDih[d] *= -1;
                    }
                }
            }
        }
        
        if(DTemplRes.pucker != null){
            DTemplRes.pucker.setBasePucker( ! DTemplRes.pucker.getBasePucker() );
            DTemplRes.pucker.setCurPucker( ! DTemplRes.pucker.getCurPucker() );
        }
                        
        return DTemplate;
    }
    
    
    static String getDName(String LName){
        //get 3-letter name for the D-amino acid corresponding to L-amino acid LName
        //The following scheme uniquely names every standard amino acid
        //(including alternate protonation forms of His, Cys)
        if(HardCodedResidueInfo.three2one.containsKey(LName))
            return "DX" + HardCodedResidueInfo.getOneLet(LName);
        //we need a couple of special cases for L-templates without their own 1-letter code
        else if(altProtStateDNames.containsKey(LName))
            return altProtStateDNames.get(LName);
        else
            throw new RuntimeException("ERROR: Can't make D form for residue type "+LName);
    }
    
    
    public static boolean isStandardLAminoAcid(String aa3Name){
        //Is this the 3-letter name of a standard amino acid?  (Normal or alternate prot state)
        return ( HardCodedResidueInfo.three2one.containsKey(aa3Name)
                || altProtStateDNames.containsKey(aa3Name) );
    }
    
    
    public static boolean isDAminoAcidName(String AAname){
        //Is this the 3-letter name of a D-amino acid?
        if(AAname.startsWith("DX")){
            if(HardCodedResidueInfo.one2three.containsKey(AAname.substring(2)))
                return true;
        }
        return altProtStateLNames.containsKey(AAname);
    }
    
    public static String getLEquivalent(String AAname){
        //get L equivalent of D amino acid (assumed to be a valid D-amino acid name)
        if(AAname.startsWith("DX")){
            return HardCodedResidueInfo.one2three.get(AAname.substring(2));
        }
        return altProtStateLNames.get(AAname);
    }
}