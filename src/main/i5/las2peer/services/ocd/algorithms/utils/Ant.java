package i5.las2peer.services.ocd.algorithms.utils;

import i5.las2peer.services.ocd.algorithms.utils.OcdAlgorithmException;
import i5.las2peer.services.ocd.graphs.Cover;
import i5.las2peer.services.ocd.graphs.CoverCreationType;
import i5.las2peer.services.ocd.graphs.CustomGraph;
import i5.las2peer.services.ocd.graphs.GraphType;
import i5.las2peer.services.ocd.algorithms.utils.MaximalCliqueGraphRepresentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.lang.Double; 
import java.lang.Math;

import org.la4j.matrix.Matrix;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.la4j.matrix.sparse.CCSMatrix;
import org.la4j.vector.Vector;
import org.la4j.vector.Vectors;
import org.la4j.vector.dense.BasicVector;

import y.base.Edge;
import y.base.EdgeCursor;
import y.base.Node;
import y.base.NodeCursor;

/**
* The original version of the overlapping community detection algorithm introduced in 2020
* by Ping Ji, Shanxin Zhang, Zhiping Zhou.
* @author Marlene Damm
*/
//TODO description of the algorithm
public class Ant {
	 
	private Vector lambda; 
	private Set<Ant> neighbors;	  
	  
	public Ant(){}

	/*----------------------------------------------------------------------------------------------------------------------------------------------------------
	 * getters for some of the global attributes
	 *----------------------------------------------------------------------------------------------------------------------------------------------------------
	 */
	public Vector getWeight() {
		return lambda;
	}
	
	public Set<Ant> getNeighbors() {
		return neighbors;
	}
	 
	/*----------------------------------------------------------------------------------------------------------------------------------------------------------
	 * setters for some of the global attributes
	 *----------------------------------------------------------------------------------------------------------------------------------------------------------
	 */
	 public void setWeight(Vector weight) {
		this.lambda = weight; 
	 }
	 
	 public void setNeighbors(Set<Ant> neighbors) {
		this.neighbors = neighbors; 
	 }
	
}