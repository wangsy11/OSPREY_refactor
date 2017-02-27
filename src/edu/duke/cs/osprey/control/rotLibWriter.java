/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.control;


import edu.duke.cs.osprey.tools.StringParsing;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 *
 * @author sw283
 */
public class rotLibWriter {

    int[] rotLib;
    PrintStream pw;
    ArrayList<String> header = new ArrayList<>();
    ArrayList<String> dihedrals = new ArrayList<>();

    public rotLibWriter(int[] rotLib, PrintStream pw) {
        this.rotLib = rotLib;
        this.pw = pw;

    }

    public static void writeRotLibFile(String fileName, int[] rotLib, String resName) {

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(resName+".txt");
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                    fileOutputStream);
            PrintStream printStream = new PrintStream(bufferedOutputStream);

            rotLibWriter writer = new rotLibWriter(rotLib, printStream);
            
            writer.readRotLib(fileName, resName);
            writer.writeRotLib();
            printStream.close();
            
            
        } catch (IOException e) {
            System.out.println("ERROR: An io exception occurred while writing file " + resName +".txt");
            System.exit(0);
        } catch (Exception e) {
            System.out.println(e.toString());
            System.out.println("ERROR: An exception occurred while writing file");
            System.exit(0);
        }
    }

    private void writeRotLib() {		
        //The actual writing procedure

        pw.println(this.header.get(0).substring(0, 6)+String.valueOf(rotLib.length));
        
        for(int q=1;q<this.header.size();q++)
            pw.println(this.header.get(q));
        
        for(int q=0;q<this.dihedrals.size();q++)
            pw.println(this.dihedrals.get(q));

    }

    private void readRotLib(String rotFilename, String resName) {

        try {
   
            boolean foundTemplate=false;
            //String volFilename = rotFile + ".vol";
            // HANDLE THE NORMAL AAs	
            FileInputStream is = new FileInputStream(EnvironmentVars.getDataDir() + rotFilename);
            BufferedReader bufread = new BufferedReader(new InputStreamReader(is));
            String curLine = null;

            // Skip over comments (lines starting with !)
            curLine = bufread.readLine();
            while (curLine.charAt(0) == '!') {
                curLine = bufread.readLine();
            }
            curLine = bufread.readLine();//skip over number of residues in library

            while (curLine != null) {
                if (curLine.charAt(0) == '!') {
                    curLine = bufread.readLine();
                    continue;
                }

                String aaName = StringParsing.getToken(curLine, 1);
                int numDihedrals = (new Integer(StringParsing.getToken(curLine, 2))).intValue();
                int numRotamers = (new Integer(StringParsing.getToken(curLine, 3))).intValue();
                if (numRotamers < 0) {	
                    numRotamers = 0;
                }

                if (resName.equalsIgnoreCase(aaName)) {
                    
                    foundTemplate = true;
                    //String[numDihedrals+1] a;

                    this.header.add(curLine);

                    for (int q=0;q<numDihedrals;q++) {
                        
                        curLine=bufread.readLine();
                        this.header.add(curLine);

                    }
                    // Read in the actual rotamers
                    int i=0;
                    for (int q=0;q<numRotamers;q++) {
                        
                        curLine=bufread.readLine();
                        if(i<rotLib.length){
                            if(this.rotLib[i]==q){
                                this.dihedrals.add(curLine);
                                i++;
                            }       
                        }
                        
                    }
                    
                    break;
                } 
                
                else {
                    for (int q=0;q<numDihedrals;q++) 
                        curLine=bufread.readLine();

                    for (int q=0;q<numRotamers;q++) 
                        curLine=bufread.readLine();
                }

                curLine = bufread.readLine();
            }
                        
            bufread.close();

            if (!foundTemplate) {
                throw new RuntimeException("ERROR: Have rotamer information for residue type "
                    + resName + " but can't find a template for it");
            }
        }
        

        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("ERROR reading rotamer library: " + e.getMessage());
        }
    }
    
}



