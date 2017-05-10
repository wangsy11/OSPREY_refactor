package edu.duke.cs.osprey.multistatekstar;

import edu.duke.cs.osprey.control.ConfEnergyCalculator.Async;

import java.math.BigDecimal;
import java.math.BigInteger;

import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;
import edu.duke.cs.osprey.control.ConfSearchFactory;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.pruning.PruningMatrix;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 *
 */
public class PartitionFunctionDiscrete extends PartitionFunctionMinimized {

	public PartitionFunctionDiscrete(
			EnergyMatrix emat, 
			PruningMatrix pmat, 
			PruningMatrix invmat, 
			ConfSearchFactory confSearchFactory,
			Async ecalc
			) {
		super(emat, pmat, invmat, confSearchFactory, ecalc);
	}

	@Override
	public void init(double targetEpsilon) {
		super.init(targetEpsilon);
		energyConfs = null;
	}

	@Override
	public void compute(int maxNumConfs) {

		if (!status.canContinue()) {
			throw new IllegalStateException("can't continue from status " + status);
		}

		ScoredConf conf;
		BigDecimal scoreWeight;
		int stopAtConf = numConfsEvaluated + maxNumConfs;

		while (true) {

			// should we keep going?
			if (!status.canContinue() || numConfsEvaluated >= stopAtConf) {
				break;
			}

			if ((conf = scoreConfs.next()) == null) {
				if(status != Status.Estimated) status = Status.NotEnoughConformations;
				break;
			}

			numConfsEvaluated++;

			scoreWeight = boltzmann.calc(conf.getScore());

			if (scoreWeight.compareTo(BigDecimal.ZERO) == 0) {
				if(status != Status.Estimated) status = Status.NotEnoughFiniteEnergies;
				break;
			}

			if(status == Status.Estimating) {

				numConfsToScore = numConfsToScore.subtract(BigInteger.ONE);

				values.qstar = values.qstar.add(scoreWeight);
				values.qprime = updateQprime(scoreWeight);

				// report progress if needed
				if (isReportingProgress && numConfsEvaluated % 1024 == 0) {
					phase1Output(conf);
				}

				// report confs if needed
				if (confListener != null) {
					confListener.onConf(conf);
				}

				// update status if needed
				double effectiveEpsilon = getEffectiveEpsilon();
				if(Double.isNaN(effectiveEpsilon)) {
					status = Status.NotEnoughFiniteEnergies;
				}
				else if (effectiveEpsilon <= targetEpsilon) {
					status = Status.Estimated;
					if (isReportingProgress) phase1Output(conf);//just to let the user know we reached epsilon
				}
			}
		}
	}

	public void compute(BigDecimal targetScoreWeights) {

		if (!status.canContinue()) {
			throw new IllegalStateException("can't continue from status " + status);
		}

		ScoredConf conf;
		BigDecimal scoreWeight;

		while (true) {

			// should we keep going?
			if (!status.canContinue() || qstarScoreWeights.compareTo(targetScoreWeights) >= 0) {
				break;
			}

			if ((conf = scoreConfs.next()) == null) {
				if(status != Status.Estimated) status = Status.NotEnoughConformations;
				break;
			}

			numConfsEvaluated++;

			scoreWeight = boltzmann.calc(conf.getScore());

			if (scoreWeight.compareTo(BigDecimal.ZERO) == 0) {
				if(status != Status.Estimated) status = Status.NotEnoughFiniteEnergies;
				break;
			}

			if(status == Status.Estimating) {
				// get the boltzmann weight
				qstarScoreWeights = qstarScoreWeights.add(scoreWeight);	

				// update pfunc state
				values.qstar = values.qstar.add(scoreWeight);
				values.qprime = updateQprime(scoreWeight);
				BigDecimal pdiff = targetScoreWeights.subtract(qstarScoreWeights);

				// report progress if needed
				if (isReportingProgress && numConfsEvaluated % 1024 == 0) {
					phase2Output(conf, pdiff);
				}

				// report confs if needed
				if (confListener != null) {
					confListener.onConf(conf);
				}

				// update status if needed
				double effectiveEpsilon = getEffectiveEpsilon();
				if(Double.isNaN(effectiveEpsilon)) {
					status = Status.NotEnoughFiniteEnergies;
				}
				else if (effectiveEpsilon <= targetEpsilon) {
					status = Status.Estimated;
					if (isReportingProgress) phase2Output(conf, pdiff);
				}
			}
		}
	}

	protected BigDecimal updateQprime(BigDecimal val) {
		return val.multiply(new BigDecimal(numConfsToScore.toString()));
	}
	
	protected double getEffectiveEpsilon() {
		return values.getEffectiveEpsilon();
	}
}
