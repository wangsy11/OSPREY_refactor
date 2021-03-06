package edu.duke.cs.osprey.confspace;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.duke.cs.osprey.TestBase;
import edu.duke.cs.osprey.astar.conf.ConfAStarTree;
import edu.duke.cs.osprey.astar.conf.RCs;
import edu.duke.cs.osprey.astar.conf.order.AStarOrder;
import edu.duke.cs.osprey.astar.conf.order.DynamicHMeanAStarOrder;
import edu.duke.cs.osprey.astar.conf.scoring.AStarScorer;
import edu.duke.cs.osprey.astar.conf.scoring.PairwiseGScorer;
import edu.duke.cs.osprey.astar.conf.scoring.TraditionalPairwiseHScorer;
import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;

public class TestConfSearchSplitter extends TestBase {
	
	private static SearchProblem search;
	
	@BeforeClass
	public static void before() {
		initDefaultEnvironment();
		
		// make any search problem, doesn't matter
		EnergyMatrixConfig emConfig = new EnergyMatrixConfig();
		emConfig.pdbPath = "test/DAGK/2KDC.P.forOsprey.pdb";
		emConfig.numFlexible = 2; // total confs in tree is 16
		emConfig.addWtRots = true;
		emConfig.doMinimize = false;
		search = makeSearchProblem(emConfig);
	}
	
	private ConfSearch makeTree() {
		
		// make any search tree, doesn't matter
		RCs rcs = new RCs(search.pruneMat);
		AStarOrder order = new DynamicHMeanAStarOrder();
		AStarScorer hscorer = new TraditionalPairwiseHScorer(search.emat, rcs);
		return new ConfAStarTree(order, new PairwiseGScorer(search.emat), hscorer, rcs);
	}
	
	private void assertConfs(ConfSearch.Splitter.Stream stream, int numConfs) {
		assertConfs(stream, makeTree(), numConfs);
	}
	
	private void assertConfs(ConfSearch.Splitter.Stream stream, ConfSearch expectedTree, int numConfs) {
		
		for (int i=0; i<numConfs; i++) {
			ScoredConf expectedConf = expectedTree.nextConf();
			ScoredConf observedConf = stream.next();
			if (observedConf == null) {
				assertThat(observedConf, is(nullValue()));
			} else {
				assertThat(observedConf.getAssignments(), is(expectedConf.getAssignments()));
			}
		}
	}
	
	@Test
	public void testOne() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream = splitter.makeStream();
		
		assertThat(splitter.getBufferSize(), is(0));
		assertConfs(stream, 16);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testTwo() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream1 = splitter.makeStream();
		ConfSearch.Splitter.Stream stream2 = splitter.makeStream();
		
		assertThat(splitter.getBufferSize(), is(0));
		assertConfs(stream1, 16);
		assertThat(splitter.getBufferSize(), is(16));
		assertConfs(stream2, 16);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testOneEnd() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream = splitter.makeStream();
		
		assertThat(splitter.getBufferSize(), is(0));
		assertConfs(stream, 17);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testTwoEnd() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream1 = splitter.makeStream();
		ConfSearch.Splitter.Stream stream2 = splitter.makeStream();
		
		assertThat(splitter.getBufferSize(), is(0));
		assertConfs(stream1, 30);
		assertThat(splitter.getBufferSize(), is(16));
		assertConfs(stream2, 30);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testTwoTrade() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream1 = splitter.makeStream();
		ConfSearch.Splitter.Stream stream2 = splitter.makeStream();
		ConfSearch check1 = makeTree();
		ConfSearch check2 = makeTree();
		
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 4);
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 4);
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 4);
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 4);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testTwoLeapfrog() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream1 = splitter.makeStream();
		ConfSearch.Splitter.Stream stream2 = splitter.makeStream();
		ConfSearch check1 = makeTree();
		ConfSearch check2 = makeTree();
		
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 8);
		assertThat(splitter.getBufferSize(), is(4));
		
		assertConfs(stream1, check1, 8);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 4);
		assertThat(splitter.getBufferSize(), is(0));
	}
	
	@Test
	public void testTwoLeapfrogEnd() {
		
		ConfSearch tree = makeTree();
		ConfSearch.Splitter splitter = new ConfSearch.Splitter(tree);
		ConfSearch.Splitter.Stream stream1 = splitter.makeStream();
		ConfSearch.Splitter.Stream stream2 = splitter.makeStream();
		ConfSearch check1 = makeTree();
		ConfSearch check2 = makeTree();
		
		assertThat(splitter.getBufferSize(), is(0));
		
		assertConfs(stream1, check1, 4);
		assertThat(splitter.getBufferSize(), is(4));
		assertConfs(stream2, check2, 20);
		assertThat(splitter.getBufferSize(), is(12));
	}
}
