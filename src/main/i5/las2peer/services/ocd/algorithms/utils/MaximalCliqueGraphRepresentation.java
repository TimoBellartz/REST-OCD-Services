package i5.las2peer.services.ocd.algorithms.utils;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import i5.las2peer.services.ocd.graphs.CustomGraph;

import y.base.Node;
import y.base.NodeCursor;



/**
 * The class is used to find all maximal cliques in a graph. It was introduced by 
 * Etsuji Tomita, Akira Tanaka and Haruhisa Takahashi in their paper "The worst-case 
 * time complexity for generating all maximal cliques and computational experiments" in 2006. 
 * 
 * @author Marlene Damm
 *
 */
public class MaximalCliqueGraphRepresentation{
	
	private HashSet<HashSet<Node>> maxCliques;
	private HashSet<Node> maxClq;
	private Node maxNode; 
	
	public MaximalCliqueGraphRepresentation() {
		
	}
	
	/**
	 * Method to find all maximal cliques of a graph.  
	 * @param graph: the graph in which to find the all maximal cliques
	 */
	public HashSet<HashSet<Node>> cliques(CustomGraph graph) {
		Node[] nodes = graph.getNodeArray();
		List<Node> subgr = new ArrayList<Node>(Arrays.asList(nodes));
		List<Node> cand = new ArrayList<Node>(Arrays.asList(nodes));
		maxClq = new HashSet<Node>();
		maxCliques = new HashSet<HashSet<Node>>();
		expand(subgr,cand);
		return maxCliques;
	}
	
	/**
	 * Recursive function to find all the maximal cliques in depth-first search approach with pruning 
	 * to make it more usable on big graphs
	 * @param subgr: set of vertices in which is needed to find a complete subgraph. It is defined as
	 * the set of all vertices with are not neighbors of the current largest complete subgraph. 
	 * @param cand: All the vertices which not have been processed by the algorithm
	 */
	protected void expand(List<Node> subgr, List<Node> cand){
		if(subgr.isEmpty() == true) {// found a maximal connected subgraph
			if(maxClq.size()<3) {// filter out two node "cliques"
				return;
			}
			HashSet<Node> cliques = new HashSet<Node>(maxClq); // deal with the call by value issue
			maxCliques.add(cliques);
		}else{// expand the complete subgraph
			
			// find the node is most connected in the current subset (subgr)
			int maxcount = 0; 
			HashSet<Node> maxOverlap = new HashSet<Node>();
			for(Node v: subgr) {
				NodeCursor neighbors = v.neighbors();
				int count = 0;
				HashSet<Node> overlap = new HashSet<Node>(); 
				
				// find nodes that are neighbors of the current node and the current subset
				for(int i = 1 ; i <neighbors.size(); i++) {
					Node u = neighbors.node();
					
					if(cand.contains(u)) {
						count++;
						overlap.add(u);
					}
				    if(neighbors.ok()== true){
				    	neighbors.cyclicNext();
				    }
				    else {
				    	break;
				    }
				}
				// to find the maximum neighborhood overlap with the current subgraph
				if(count >= maxcount){
					maxNode = v;
				    maxcount = count; 
				    maxOverlap = overlap; 
				}
			}
			
			// process all the nodes which are not neighbors of the most connected node found above
			HashSet<Node> Ext_u = new HashSet<Node>(cand);
			Ext_u.removeAll(maxOverlap);
			
			for(Node q: Ext_u) {
					List<Node> q_neighbors = new ArrayList<Node>();
					
					maxClq.add(q); // current clique (not maximal yet)
					NodeCursor n = q.neighbors();
					
					for(int i = 0 ; i <n.size(); i++) { // find neighbors
						q_neighbors.add(n.node());
						if(n.ok()== true){
							n.cyclicNext();
						}
						else {
							break;
						}
					}
					
					//update the candidate and the subgraph set to the neighbors of q
					List<Node> subgr2 = new ArrayList<Node>(subgr);
					List<Node> cand2 = new ArrayList<Node>(cand);
					subgr2.retainAll(q_neighbors);
					cand2.retainAll(q_neighbors);
					cand2.remove(q);
					
					expand(subgr2,cand2); // process the neighbors of q 
					
					cand.remove(q); // make sure that the node to processed twice
					maxClq.remove(q); // prepare a clique set
			}
		}
	}
}		
		