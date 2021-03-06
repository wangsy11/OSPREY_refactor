package edu.duke.cs.osprey.astar.conf;

import java.util.ArrayList;
import java.util.Collections;

public class ConfIndex {
	
	private int numPos;
	private ConfAStarNode node;
	private ArrayList<ConfAStarNode.Link> links;
    private int numDefined;
    private int numUndefined;
    private int[] definedPos;
    private int[] definedRCs;
    private int[] undefinedPos;
	
	public ConfIndex(int numPos) {
		this.numPos = numPos;
		this.node = null;
		this.links = new ArrayList<>();
        this.numDefined = 0;
        this.numUndefined = 0;
        this.definedPos = new int[numPos];
        this.definedRCs = new int[numPos];
        this.undefinedPos = new int[numPos];
	}
	
	public ConfIndex(ConfIndex other, int nextPos, int nextRc) {
		this(other.numPos);
		index(other, nextPos, nextRc);
	}
	
	public void index(ConfAStarNode node) {
		
		// is this node already indexed?
		if (this.node == node) {
			return;
		}
		this.node = node;
		
		// split conformation into defined and undefined positions
		
		// sort the link chain in position order
		links.clear();
		ConfAStarNode.Link link = node.getLink();
		while (!(link.isRoot())) {
			links.add(link);
			link = link.getParent();
		}
		Collections.sort(links);
		
		// get the first link, if any
		int i = 0;
		if (links.isEmpty()) {
			link = null;
		} else {
			link = links.get(i);
		}
		
		// split the positions
		numDefined = 0;
		numUndefined = 0;
		for (int pos=0; pos<numPos; pos++) {
			
			// does this pos match the next link?
			if (link != null && pos == link.getPos()) {
				
				// defined pos
				definedPos[numDefined] = pos;
				definedRCs[numDefined] = link.getRC();
				numDefined++;
				
				// advance to the next link, if any
				i++;
				if (i < links.size()) {
					link = links.get(i);
				} else {
					link = null;
				}
				
			} else {
				
				// undefined pos
				undefinedPos[numUndefined] = pos;
				numUndefined++;
			}
		}
	}
	
	public void index(ConfIndex other, int nextPos, int nextRc) {
		
		// the next pos should be undefined (and not defined)
		assert (other.isUndefined(nextPos));
		assert (!other.isDefined(nextPos));
		
		// copy from the other index
		numPos = other.numPos;
		numDefined = other.numDefined + 1;
		numUndefined = other.numUndefined - 1;
		
		// update defined side
		boolean isInserted = false;
		for (int i=0; i<other.numDefined; i++) {
			int pos = other.definedPos[i];
			int rc = other.definedRCs[i];
			if (nextPos > pos) {
				definedPos[i] = pos;
				definedRCs[i] = rc;
			} else {
				
				if (!isInserted) {
					definedPos[i] = nextPos;
					definedRCs[i] = nextRc;
					isInserted = true;
				}
				
				definedPos[i+1] = pos;
				definedRCs[i+1] = rc;
			}
		}
		if (!isInserted) {
			definedPos[other.numDefined] = nextPos;
			definedRCs[other.numDefined] = nextRc;
		}
		
		// update undefined side
		int j = 0;
		for (int i=0; i<other.numUndefined; i++) {
			int pos = other.undefinedPos[i];
			if (pos != nextPos) {
				undefinedPos[j++] = pos;
			}
		}
		
		// init defaults for things we won't copy
		node = null;
	}
	
	public ConfAStarNode getNode() {
		return node;
	}
	
	public int getNumPos() {
		return numPos;
	}
	
	public int getNumDefined() {
		return numDefined;
	}
	public void setNumDefined(int val) {
		numDefined = val;
	}
	
	public int getNumUndefined() {
		return numUndefined;
	}
	public void setNumUndefined(int val) {
		numUndefined = val;
	}
	
	public int[] getDefinedPos() {
		return definedPos;
	}
	
	public int[] getDefinedRCs() {
		return definedRCs;
	}
	
	public int[] getUndefinedPos() {
		return undefinedPos;
	}

	public boolean isDefined(int pos) {
		for (int i=0; i<numDefined; i++) {
			if (definedPos[i] == pos) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isUndefined(int pos) {
		for (int i=0; i<numUndefined; i++) {
			if (undefinedPos[i] == pos) {
				return true;
			}
		}
		return false;
	}
}
