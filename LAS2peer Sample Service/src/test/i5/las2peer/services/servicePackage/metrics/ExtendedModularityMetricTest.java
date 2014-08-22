package i5.las2peer.services.servicePackage.metrics;

import static org.junit.Assert.assertEquals;
import i5.las2peer.services.servicePackage.adapters.AdapterException;
import i5.las2peer.services.servicePackage.algorithms.OcdAlgorithm;
import i5.las2peer.services.servicePackage.algorithms.SpeakerListenerLabelPropagationAlgorithm;
import i5.las2peer.services.servicePackage.algorithms.utils.OcdAlgorithmException;
import i5.las2peer.services.servicePackage.graph.Cover;
import i5.las2peer.services.servicePackage.graph.CustomGraph;
import i5.las2peer.services.servicePackage.graph.GraphProcessor;
import i5.las2peer.services.servicePackage.graph.GraphType;
import i5.las2peer.services.servicePackage.testsUtil.OcdTestGraphFactory;

import java.io.FileNotFoundException;
import java.util.HashSet;

import org.junit.Test;
import org.la4j.matrix.Matrix;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.la4j.matrix.sparse.CCSMatrix;

import y.base.Node;

public class ExtendedModularityMetricTest {

	/*
	 * Assures that modularity is 0 if only one community exists.
	 */
	@Test
	public void testExtendedModularityWithOneCommunity() throws AdapterException, FileNotFoundException {
		CustomGraph graph = OcdTestGraphFactory.getSawmillGraph();
		Matrix memberships = new Basic2DMatrix(graph.nodeCount(), 1);
		for(int i=0; i<memberships.rows(); i++) {
			memberships.set(i, 0, 1);
		}
		Cover cover = new Cover(graph, memberships);
		ExtendedModularity metric = new ExtendedModularity();
		metric.measure(cover);
		assertEquals(0, cover.getMetric(MetricType.EXTENDED_MODULARITY).getValue(), 0.0001);
		System.out.println("1 Community");
		System.out.println(cover.toString());
	}
	
	@Test
	public void testExtendedModularityOnSawmillSLPA() throws OcdAlgorithmException, AdapterException, FileNotFoundException {
		CustomGraph graph = OcdTestGraphFactory.getSawmillGraph();
		OcdAlgorithm algo = new SpeakerListenerLabelPropagationAlgorithm();
		Cover cover = algo.detectOverlappingCommunities(graph);
		ExtendedModularity metric = new ExtendedModularity();
		metric.measure(cover);
		System.out.println("Sawmill SLPA");
		System.out.println(cover.toString());
	}
	
	@Test
	public void testExtendedModularityOnDirectedAperiodicTwoCommunities() throws OcdAlgorithmException {
		CustomGraph graph = OcdTestGraphFactory.getDirectedAperiodicTwoCommunitiesGraph();
		Matrix memberships = new CCSMatrix(graph.nodeCount(), 3);
		memberships.set(0, 0, 1);
		memberships.set(1, 0, 0.7);
		memberships.set(1, 1, 0.3);
		memberships.set(2, 0, 1);
		memberships.set(3, 0, 1);
		memberships.set(4, 0, 0.8);
		memberships.set(4, 1, 0.2);
		memberships.set(5, 1, 0.4);
		memberships.set(5, 2, 0.6);
		memberships.set(6, 2, 1);
		memberships.set(7, 2, 1);
		memberships.set(8, 2, 1);
		memberships.set(9, 1, 0.1);
		memberships.set(9, 2, 0.9);
		memberships.set(10, 0, 0.4);
		memberships.set(10, 1, 0.4);
		memberships.set(10, 2, 0.2);
		Cover cover = new Cover(graph, memberships);
		ExtendedModularity metric = new ExtendedModularity();
		metric.measure(cover);
		assertEquals(0.581, cover.getMetric(MetricType.EXTENDED_MODULARITY).getValue(), 0.01);
		System.out.println(cover);
	}
	
	@Test
	public void testEqualsZero() throws OcdAlgorithmException {
		for(int m_prime = 0; m_prime < 10; m_prime += 10) {
			CustomGraph graph = new CustomGraph();
			Matrix memberships = new CCSMatrix(2*m_prime + 2, m_prime + 1);
			Node nodeA = graph.createNode();
			Node nodeB = graph.createNode();
			graph.createEdge(nodeA, nodeB);
			memberships.set(nodeA.index(), 0, 1);
			memberships.set(nodeB.index(), 0, 1);
			for(int i=0; i<m_prime; i++) {
				Node aNeighbor = graph.createNode();
				graph.createEdge(nodeA, aNeighbor);
				memberships.set(aNeighbor.index(), 1+i, 1);
				Node bNeighbor = graph.createNode();
				graph.createEdge(nodeB, bNeighbor);
				memberships.set(bNeighbor.index(), 1+i, 1);
			}
			graph.addType(GraphType.DIRECTED);
			GraphProcessor processor = new GraphProcessor();
			processor.makeCompatible(graph, new HashSet<GraphType>());
			Cover cover = new Cover(graph, memberships);
			ExtendedModularity metric = new ExtendedModularity();
			metric.measure(cover);
			assertEquals(0, cover.getMetric(MetricType.EXTENDED_MODULARITY).getValue(), 0.0001);
			System.out.println("m'=" + m_prime +": " + cover.getMetric(MetricType.EXTENDED_MODULARITY).getValue());
		}
	}

}
