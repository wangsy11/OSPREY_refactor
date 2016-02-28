/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.energy;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;

import edu.duke.cs.osprey.parallelism.ThreadParallelism;

/**
 *
 * @author Mark Hallen (mhall44@duke.edu)
 * @author Adegoke Ojewole (ao68@duke.edu)
 */
//This an energy function consisting of terms that are other energy functions
//For example, it can be AMBER energy + dihedral energy
//or ( energy of a pair of residues ) - (energy of residue 1) - (energy of residue 2)
//Total energy = sum_i (coeff[i] * energy i)

public class MultiTermEnergyFunction implements EnergyFunction {

	ArrayList<EnergyFunction> terms = new ArrayList<>();
	ArrayList<Double> coeffs = new ArrayList<>();
	ArrayList<Double> partialE = new ArrayList<>();
	ArrayList<Integer> indexes = new ArrayList<>();


	//Constructor with no terms
	public MultiTermEnergyFunction(){

	}

	public void addTerm(EnergyFunction ef){
		terms.add(ef);
		coeffs.add(1.);
		partialE.add(0.0);
		indexes.add(indexes.size());
	}

	//add a term to this with a given coefficient
	public void addTermWithCoeff(EnergyFunction ef, double coeff){
		terms.add(ef);
		coeffs.add(coeff);
		partialE.add(0.0);
		indexes.add(indexes.size());
	}


	@Override
	public double getEnergy(){

		double E = 0;

		if(terms.size()!=coeffs.size()){
			throw new RuntimeException("ERROR: MultiTermEnergyFunction has "+terms.size()
			+" terms but "+coeffs.size()+" coefficients");
		}

		if(ThreadParallelism.getNumThreads() == 1) {
			for(int termNum=0; termNum<terms.size(); termNum++){
				double termE = terms.get(termNum).getEnergy();
				E += coeffs.get(termNum)*termE;
			}
		} else {
			indexes.parallelStream().forEach((term) -> partialE.set(term, terms.get(term).getEnergy()*coeffs.get(term)));
			for(int term = 0; term < indexes.size(); ++term) E += partialE.get(term);
		}


		if(Double.isNaN(E) || Double.isInfinite(E))//This can happen if there are positive and negative terms
			//with infinite energy...we assume this to be an impossible conformation
			//and thus return inifinity
			return Double.POSITIVE_INFINITY;

		return E;
	}

	public ArrayList<EnergyFunction> getTerms() {
		return terms;
	}

	public ArrayList<Double> getCoeffs() {
		return coeffs;
	}


}

