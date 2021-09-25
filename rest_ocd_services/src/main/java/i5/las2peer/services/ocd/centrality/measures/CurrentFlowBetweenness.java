package i5.las2peer.services.ocd.centrality.measures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.la4j.inversion.GaussJordanInverter;
import org.la4j.inversion.MatrixInverter;
import org.la4j.matrix.Matrix;
import org.la4j.matrix.sparse.CCSMatrix;

import i5.las2peer.services.ocd.centrality.data.CentralityCreationLog;
import i5.las2peer.services.ocd.centrality.data.CentralityCreationType;
import i5.las2peer.services.ocd.centrality.data.CentralityMeasureType;
import i5.las2peer.services.ocd.centrality.utils.CentralityAlgorithm;
import i5.las2peer.services.ocd.centrality.data.CentralityMap;
import i5.las2peer.services.ocd.graphs.CustomGraph;
import i5.las2peer.services.ocd.graphs.GraphType;
import y.base.Edge;
import y.base.EdgeCursor;
import y.base.Node;
import y.base.NodeCursor;

/**
 * Implementation of Current-Flow Betweenness.
 * See: Brandes, Ulrik and Fleischer, Daniel. 2005. Centrality Measures Based on Current Flow.
 * @author Tobias
 *
 */
public class CurrentFlowBetweenness implements CentralityAlgorithm {
	
	public CentralityMap getValues(CustomGraph graph) throws InterruptedException {
		CentralityMap res = new CentralityMap(graph);
		res.setCreationMethod(new CentralityCreationLog(CentralityMeasureType.CURRENT_FLOW_BETWEENNESS, CentralityCreationType.CENTRALITY_MEASURE, this.getParameters(), this.compatibleGraphTypes()));
		
		NodeCursor nc = graph.nodes();
		// If the graph contains no edges
		if(graph.edgeCount() == 0) {
			while(nc.ok()) {
				Node node = nc.node();
				res.setNodeValue(node, 0);
				nc.next();
			}
			return res;
		}
		
		int n = nc.size();
		Matrix L = new CCSMatrix(n, n);
		
		// Create laplacian matrix
		while(nc.ok()) {
			if(Thread.interrupted()) {
				throw new InterruptedException();
			}
			Node node = nc.node();
			int i = node.index();
			L.set(i, i, graph.getWeightedInDegree(node));
			nc.next();
		}
		EdgeCursor ec = graph.edges();
		while(ec.ok()) {
			if(Thread.interrupted()) {
				throw new InterruptedException();
			}
			Edge edge = ec.edge();
			L.set(edge.source().index(), edge.target().index(), -graph.getEdgeWeight(edge));
			ec.next();
		}

		// Remove the first row and column
		L = L.slice(1, 1, n, n);
		
		MatrixInverter gauss = new GaussJordanInverter(L);
		Matrix L_inverse = gauss.inverse();
		
		// Create matrix C
		Matrix C = new CCSMatrix(n, n);
		for(int i = 0; i < n-1; i++) {
			for(int j = 0; j < n-1; j++) {
				C.set(i+1, j+1, L_inverse.get(i, j));
			}
		}
		
		/*
		 * Each (undirected) edge must have an arbitrary but fixed orientation, 
		 * here it points from the node with the smaller index to the one with the higher index.
		 * The edge in the opposite direction is removed.
		 */
		ec.toFirst();
		while(ec.ok()) {
			if(Thread.interrupted()) {
				throw new InterruptedException();
			}
			Edge edge = ec.edge();
			Node s = edge.source();
			Node t = edge.target();
			if(s.index() < t.index()) {
				Edge reverseEdge = t.getEdgeTo(s);
				graph.removeEdge(reverseEdge);
			}
			ec.next();
		}
		
		// Create matrix B
		ec.toFirst();
		int m = ec.size();
		Matrix B = new CCSMatrix(m, n);
		int edgeIndex = 0;
		while(ec.ok()) {
			if(Thread.interrupted()) {
				throw new InterruptedException();
			}
			Edge edge = ec.edge();
			int s = edge.source().index();
			int t = edge.target().index();
			B.set(edgeIndex, s, graph.getEdgeWeight(edge));
			B.set(edgeIndex, t, -graph.getEdgeWeight(edge));
			ec.next();
			edgeIndex++;
		}
		Matrix F = B.multiply(C);
		int normalizationFactor = (n-2)*(n-1);
		Node[] nodeArray = graph.getNodeArray();
		nc.toFirst();
		
		// Calculate centrality value for each node
		while(nc.ok()) {
			if(Thread.interrupted()) {
				throw new InterruptedException();
			}
			Node node = nc.node();	
			double throughputSum = 0.0;		
			for(int sourceIndex = 0; sourceIndex < n; sourceIndex++) {
				for(int targetIndex = sourceIndex+1; targetIndex < n; targetIndex++) {
					if(sourceIndex != node.index() && targetIndex != node.index()) {
						Node s = nodeArray[sourceIndex];
						Node t = nodeArray[targetIndex];		
						ec.toFirst();
						edgeIndex = 0;
						while(ec.ok()) {
							Edge edge = ec.edge();
							if(edge.target() == node || edge.source() == node) {
								throughputSum += Math.abs(F.get(edgeIndex, s.index()) - F.get(edgeIndex, t.index()));
							}
							ec.next();
							edgeIndex++;
						}
					}
				}
			}
			res.setNodeValue(node, 1.0/normalizationFactor * throughputSum/2);
			nc.next();
		}
		return res;
	}

	@Override
	public Set<GraphType> compatibleGraphTypes() {
		Set<GraphType> compatibleTypes = new HashSet<GraphType>();
		compatibleTypes.add(GraphType.WEIGHTED);
		return compatibleTypes;
	}

	@Override
	public CentralityMeasureType getCentralityMeasureType() {
		return CentralityMeasureType.CURRENT_FLOW_BETWEENNESS;
	}
	
	@Override
	public HashMap<String, String> getParameters() {
		return new HashMap<String, String>();
	}
	
	@Override
	public void setParameters(Map<String, String> parameters) throws IllegalArgumentException {
		if(parameters.size() > 0) {
			throw new IllegalArgumentException();
		}
	}
}
