/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.control;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import edu.duke.cs.osprey.confspace.ConfSearch.EnergiedConf;
import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;
import edu.duke.cs.osprey.confspace.SearchProblem;

/**
 *
 * Prints information about a conformation
 * 
 * @author mhall44
 */
public class ConfPrinter {
	
    private static final int LabelSize = 30;
    private static final String LabelFormat = "\t%-" + LabelSize + "s";
    
    SearchProblem searchSpace;
    Writer confFileHandle;
    String confFileName;
    int numConfs;
    double minEnergy;
    
    boolean printEPICEnergy;
    
    ConfPrinter(SearchProblem searchProb, String confFileName, boolean printEPICEnergy){
        //open (for writing) a file to record conformations in
        searchSpace = searchProb;
        this.confFileName = confFileName;
        this.numConfs = 0;
        this.minEnergy = Double.POSITIVE_INFINITY;
        this.printEPICEnergy = printEPICEnergy;
        
        try {
            // NOTE: don't use buffered writers here
            // we want to flush writes to disk ASAP so we keep as much info as
            // possible after a failure that aborts the program
            confFileHandle = new FileWriter(confFileName);
        }
        catch(Exception e){
            throw new RuntimeException("ERROR OPENING CONF FILE.  NAME: "
                    + confFileName + e.getMessage());
        }
    }
    
    public String getConfReport(int[] conf) {
        StringBuilder buf = new StringBuilder();
        
        buf.append(String.format(LabelFormat, "RCs (residue-based numbers)"));
        for (int rc : conf) {
            buf.append(String.format(" %3d", rc));
        }
        buf.append("\n");

        buf.append(String.format(LabelFormat, "Residue types"));
        for (int pos=0; pos<searchSpace.confSpace.numPos; pos++) {
            String resType = searchSpace.confSpace.posFlex.get(pos).RCs.get(conf[pos]).AAType;
            buf.append(String.format(" %3s", resType));
        }
        buf.append("\n");

        buf.append(String.format(LabelFormat, "Rotamer numbers"));
        for (int pos=0; pos<searchSpace.confSpace.numPos; pos++) {
            int rotNum = searchSpace.confSpace.posFlex.get(pos).RCs.get(conf[pos]).rotNum;
            buf.append(String.format(" %3d", rotNum));
        }
        buf.append("\n");
        
        return buf.toString();
    }
    
    public String getConfReport(EnergiedConf conf) {
        return getConfReport(conf, null);
    }
    
    public String getConfReport(EnergiedConf conf, EnergyWindow window) {
        StringBuilder buf = new StringBuilder();
        
        buf.append(getConfReport(conf.getAssignments()));
        
        buf.append(String.format(LabelFormat + " %.6f", "Energy", conf.getEnergy()));
        if (window != null) {
            buf.append(String.format(" (best so far: %.6f)", window.getMin()));
        }
        buf.append("\n");
        
        buf.append(String.format(LabelFormat + " %.6f (gap: %.6f", "Score", conf.getScore(), Math.abs(conf.getScore() - conf.getEnergy())));
        if (window != null) {
            buf.append(String.format(", remaining: %.6f", window.getMax() - conf.getScore()));
        }
        buf.append(")\n");
        
        // TODO: should conf printer really know what EPIC is?
        // useful to see EPIC energy (confE is regular E, lowerBound is tup-exp)
        if (printEPICEnergy) {
            buf.append(String.format(LabelFormat + "%.6f\n", "EPIC", searchSpace.EPICMinimizedEnergy(conf.getAssignments())));
        }
        
        return buf.toString();
    }
    
    public String getConfReport(ScoredConf conf) {
    	return getConfReport(conf, null);
    }
    
    public String getConfReport(ScoredConf conf, EnergyWindow window) {
        StringBuilder buf = new StringBuilder();
        buf.append(getConfReport(conf.getAssignments()));
        
        if (window == null) {
        	buf.append(String.format(LabelFormat + " %.6f\n", "Score", conf.getScore()));
        } else {
        	buf.append(String.format(LabelFormat + " %.6f (remaining: %.6f)\n", "Score", conf.getScore(), window.getMax() - conf.getScore()));
        }
        
        return buf.toString();
    }
    
    public void printConf(EnergiedConf conf){
        
        try {
        	
            confFileHandle.write((numConfs++) + " CONF: ");
            for(int rc : conf.getAssignments()){
                confFileHandle.write(rc + " ");
            }

            confFileHandle.write("RESTYPES: ");
            for(int pos=0; pos<searchSpace.confSpace.numPos; pos++){
                String resType = searchSpace.confSpace.posFlex.get(pos).RCs.get(conf.getAssignments()[pos]).AAType;
                confFileHandle.write(resType + " ");
            }

            confFileHandle.write("ROTS: ");
            for(int pos=0; pos<searchSpace.confSpace.numPos; pos++){
                int rotNum = searchSpace.confSpace.posFlex.get(pos).RCs.get(conf.getAssignments()[pos]).rotNum;
                confFileHandle.write( rotNum + " " );
            }

            minEnergy = Math.min(minEnergy, conf.getEnergy());

            Double epicEnergy = null;
            if(printEPICEnergy) { //useful to see EPIC energy (confE is regular E, lowerBound is tup-exp)
            	epicEnergy = searchSpace.EPICMinimizedEnergy(conf.getAssignments());
            }
            
            confFileHandle.write("Score: "+conf.getScore()+" Energy: "+conf.getEnergy()+" Best so far: "+minEnergy);
            if (epicEnergy != null) {
            	confFileHandle.write(" EPIC energy: " + searchSpace.EPICMinimizedEnergy(conf.getAssignments()));
            }
            confFileHandle.write("\n");
            
            // flush writes to disk immediately so we save as much info as possible after a failure
            confFileHandle.flush();
        }
        catch(IOException e){
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
    
    void closeConfFile(){
        //close it
        
        try {
            confFileHandle.close();
        }
        catch(Exception e){
            throw new RuntimeException("ERROR CLOSING CONF FILE.  NAME: "
                    + confFileName + e.getMessage());
        }
    }
}
