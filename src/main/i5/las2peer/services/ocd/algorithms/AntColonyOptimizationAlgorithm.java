package i5.las2peer.services.ocd.algorithms;

import i5.las2peer.services.ocd.algorithms.utils.OcdAlgorithmException;
import i5.las2peer.services.ocd.graphs.Cover;
import i5.las2peer.services.ocd.graphs.CoverCreationType;
import i5.las2peer.services.ocd.graphs.CustomGraph;
import i5.las2peer.services.ocd.graphs.GraphType;
import i5.las2peer.services.ocd.algorithms.utils.MaximalCliqueGraphRepresentation;
import i5.las2peer.services.ocd.algorithms.utils.Ant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import java.lang.Double; 
import java.lang.Math;

import org.la4j.matrix.Matrix;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.la4j.matrix.sparse.CCSMatrix;
import org.la4j.vector.Vector;
import org.la4j.vector.Vectors;
import org.la4j.vector.dense.BasicVector;
import org.la4j.vector.sparse.CompressedVector;

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
public class AntColonyOptimizationAlgorithm implements OcdAlgorithm {
	
	
	private static int maxIterations = 1000;
	
	/**
	 * number of ants/subproblems to solve 
	 */
	private static int M = 10;
	  
	/**
	 * Positive integer associated with M. Helps to find uniformly distributed weight vector. Should be at least as large as M.  
	 */
	private static int H = M; 
	
	/**
	 * Number of groups of ants. The value should be in between 0 and M. 
	 */
	private static int K = 5; 
	
	/**
	 * number of nodes in the graph
	 */
	private static int nodeNr;
	  
	/**
	 * Rate of the pheromone persistence
	 */
	private static double rho = 0.5;

	/**
	 * Threshold determines the edges which are in the clique graph. It should be in between 0 and 1 with
	 * 1 being no edges in the clique graph and 0 being no edge will be left out. Setting this threshold 
	 * to 0 will slow down the performance. Since good thresholds are not stated in the paper the threshold 
	 * should be proven experimentally. 
	 */
	private double threshold = 0.2; 
	
	/**
	 * Contains pheromones matrix of each group of ants to get hold of the current pheromones in the graph. 
	 * Each cell of the matrix stands for an edge. The higher the pheromone concentration the more likely it will be 
	 * that an ant visits this edge.   
	 */
	private List<Matrix> pheromones; 
	
	/**
	 * initial pheromone level
	 */
	private static int initialPheromones = 100; 
	
	/**
	 * Number of objective functions used in this algorithm. The proposed algorithm by Ji et al uses 2 objective functions. So we recommend to this parameter to be 2. 
	 */
	private int objectFkt = 2;
	
	/**
	 * saves all best found community solutions
	 */
	private List<Vector> EP;
	
	/**
	 * Heuristic information matrix: shows how similar to nodes. Nodes which are more similar are more likely to be in 
	 * the same community. The values are between 0 and 1 which 0 being not connected and 1 being very similar. 
	 * 
	 */
	private Matrix heuristic; 
	
	/**
	* The number of nearest neighbors considered in a neighborhood
	*/
	private static int nearNbors = 2; 
	
	/**
	 * Indicates the influence of the pheromone information matrix to the solution construction. The higher alpha the bigger is the influence.
	 */
	private static double alpha = 0.2; 
	
	/**
	 * Indicates the influence of the heuristic information matrix to the solution construction. The higher beta the bigger is the influence. 
	 */
	private static double beta = 0.2; 
	
	/**
	 * reference point for the minimal solution found so far 
	 */
	private static Vector refPoint;
	
	/**
	 * threshold to filter out path randomly. used in solution construction and between 0 and 1
	 */
	private static double R = 0.5;
	
	
	/*
	 * PARAMETER NAMES
	 */
	protected static final String MAX_ITERATIONS = "maximum iterations";
	
	protected static final String NUMBER_OF_ANTS = "number of ants/subproblems";
			
	protected static final String EVAPORATION_FACTOR = "evaportation factor";
	
	protected static final String MCR_THRESHOLD = "Threshold to filter out edges";
	
	protected static final String NUMMER_OF_NEIGHBORS = "Number of nearest neighbors to be considered in a neighborhood";
	
	public AntColonyOptimizationAlgorithm() {}
	
	/**
	 * Executes the algorithm on a connected graph.
	 * Implementations of this method should allow to be interrupted.
	 * I.e. they should periodically check the thread for interrupts (/TODO)
	 * and throw an InterruptedException if an interrupt was detected.
	 * @param graph An at least weakly connected graph whose community structure will be detected.
	 * @return A cover for the input graph containing the community structure.
	 * @throws OcdAlgorithmException If the execution failed.
	 * @throws InterruptedException If the executing thread was interrupted.
	 */
	@Override
	public Cover detectOverlappingCommunities(CustomGraph graph) 
			throws OcdAlgorithmException, InterruptedException {
		CustomGraph MCR = representationScheme(graph);
		initialization(MCR);
		
		
		//TODO add Ant colony Optimization here
		
		return new Cover(graph);
		
	}
	
	/**
	 * The Representation Scheme of this algorithm is a Maximal Clique Scheme. This is done to have a less complex community search process, since cliques in the 
	 * original graph tend to be in the same community anyways. After the search for maximal cliques, inter-clique edges are filtered out if the cliques are loosly
	 * connected.
	 * @param graph to make an Maximal Clique Graph from
	 * @return encoded input graph
	 */
	protected CustomGraph representationScheme(CustomGraph graph) {
		// maximal clique search 
		MaximalCliqueGraphRepresentation MCR = new MaximalCliqueGraphRepresentation();
		HashMap<Integer,HashSet<Node>> maxClq = MCR.cliques(graph);
				
		// determining the link strength in between the cliques
		Matrix lkstrgth = linkStrength(graph, maxClq);
				
		//creating the encoding
		nodeNr = maxClq.size(); 
		CustomGraph encoding = new CustomGraph(); 
		for(int i = 0; i < nodeNr; i++) {//creating clique nodes
				encoding.createNode(); 
		}
		Node[] nodes = encoding.getNodeArray();
			for(Node n1: nodes) { // creating clique edges 
				int i1 = n1.index();
				for(Node n2: nodes) {
					int i2 = n2.index();
					double ls = lkstrgth.get(i1, i2);
					if(ls>=threshold) { // leaving out weak edges
						Edge e = encoding.createEdge(n1, n2);
						encoding.setEdgeWeight(e, ls);
					}
				}
			}
		return encoding; 
				
	}
	
	/**
	 * Initialization of ants, the weight vector, pheromone information matrix, heuristic information matrix and initial solutions
	 * "MOEA/D: A Multiobjective Evolutionary Algorithm Based on Decomposition" by Qingfu Zhang et al. for reference.
	 * @param graph
	 * @param nodes
	 * @throws InterruptedException 
	 */
	protected void initialization(CustomGraph graph) throws InterruptedException {
		EP = new ArrayList<Vector>(); 
		
		//Initializing Ants
		List<Ant> ants = new ArrayList<Ant>(); 
		for(int i = 0; i < M; i++) {
			Ant a = new Ant(i);
			ants.add(a);
		}
		
		// initializing the values to choose from 
		List<Double> H_values = new ArrayList<Double>();
		for(int i = 0; i <= H; i++) {
			double hlp = i/H;
			H_values.add(i,hlp);
		}
		
		//initialization of the weight vectors of the subproblems/ants
		Random rand = new Random();
		List<Vector> lambdas = new ArrayList<Vector>(); 
		for(int i = 0; i < M; i++) {
			double rVal = H_values.get(rand.nextInt(H));
			double[] hlp = {rVal,1-rVal};
			Vector v = new BasicVector(hlp);
			ants.get(i).setWeight(v);
			lambdas.add(v);
		}
		
		// find the T closest neighbors
		Map<Double,Integer> euclDist = new HashMap<Double,Integer>();
		//List<ArrayList<Integer>> neighborhood = new ArrayList<ArrayList<Integer>>();
		
		for(Ant a1: ants) {
			Vector lda1 = a1.getWeight(); 
			int j = 0; 
			for(Ant a2: ants) { // calculate the euclidian distance for two vectors   
				Vector lda2 = a2.getWeight();
				double eucl = Math.sqrt(Math.pow(lda2.get(0) - lda1.get(1), 2) + Math.pow(lda2.get(1) - lda1.get(1), 2));
				if(j < nearNbors) { // if not nearNbors solutions have been found
					euclDist.put(eucl,j);
				} else {
					Iterator<Double> it = euclDist.keySet().iterator(); 
					while(it.hasNext()) { // compare the entries found so far to the current vector
						double comp_eucl = it.next();
						if(comp_eucl>eucl) { // as soon as the euclidian distance of the current vector is smaller then the entry in the table -> replace that entry
							euclDist.remove(comp_eucl);
							euclDist.put(eucl,j); 
							break;
						}
					}
				}
				j++;
			}
			Iterator<Double> it = euclDist.keySet().iterator(); //convert HashMap into ArrayList 
			ArrayList<Integer> tmp = new ArrayList<Integer>();
			while(it.hasNext()) {
				tmp.add(euclDist.get(it.next()));
			}
			//neighborhood.add(tmp); 
			a1.setNeighbors(tmp);
		}
		
		
		//initialization of help weight vectors of the group generation
		List<Vector> hlp_lambdas = new ArrayList<Vector>(); 
		for(int i = 0; i <= K; i++) {
			double rVal = H_values.get(rand.nextInt(H));
			double[] hlp = {rVal,1-rVal};
			Vector v = new BasicVector(hlp);
			hlp_lambdas.add(v);
		}
		
		// grouping the ants in K groups
		for(Ant a1: ants) {
			Vector lda1 = a1.getWeight(); 
			int minID = 0; 
			double minEucl = Math.sqrt(Math.pow(hlp_lambdas.get(0).get(0) - lda1.get(1), 2) + Math.pow(hlp_lambdas.get(0).get(1) - lda1.get(1), 2)); 
			int j = 0; 
			for(Vector v: hlp_lambdas) { // calculate the euclidian distance for two vectors  
				double eucl = Math.sqrt(Math.pow(v.get(0) - lda1.get(1), 2) + Math.pow(v.get(1) - lda1.get(1), 2));
				if(j == 0) { // if not nearNbors solutions have been found
					continue;
				} else {
					if(minEucl > eucl) {
						minID = j;
						minEucl = eucl; 
					}
				}
				j++;
			}
			a1.setGroup(minID);
		}
		
		// fill in the heuristic information matrix
		heuristic = new Basic2DMatrix(nodeNr,nodeNr);  
		Matrix neighbors = graph.getNeighbourhoodMatrix();
		for(int i = 0; i < nodeNr-1; i++) {
			Vector nbor1 = neighbors.getRow(i);
			double nborsum1 = nbor1.sum(); 
			double mu1 = nborsum1/nodeNr; // mean
			double std1 = (nborsum1*Math.pow(1-mu1, 2)+(nodeNr-nborsum1)*Math.pow(mu1, 2))/nodeNr;
			std1 = Math.sqrt(std1); // standard deviation
			
			nbor1.subtract(mu1);  
			for(int j = i+1; j < nodeNr; j++) {
				Vector nbor2 = neighbors.getRow(j);
				double nborsum2 = nbor2.sum(); 
				double mu2 = nborsum1/nodeNr; // mean
				double std2 = (nborsum2*Math.pow(1-mu1, 2)+(nodeNr-nborsum2)*Math.pow(mu1, 2))/nodeNr; 
				std2 = Math.sqrt(std2); // standard deviation
				
				// compute covariance
				nbor2.subtract(mu2);
				double cov = 0;
				for(int k = 0; k < nodeNr; k++) {
					cov += nbor1.get(k)*nbor2.get(k);
				}
				
				double pearson = -cov/(nodeNr*std1*std2); // negative pearson correlation coefficient
				double h = 1/(1+Math.pow(Math.E, pearson)); // heuristic information value for nodes i, j 
				if(h < 0) {
					h = 0; 
				}
				heuristic.set(i, j, h);
				heuristic.set(j, i, h); 
				
			}
		}
		
		//initialize the pheromone matrices 
		pheromones = new ArrayList<Matrix>(); 
		double[] p = new double[nodeNr]; 
		Arrays.fill(p, initialPheromones);
		Matrix pheromone = new Basic2DMatrix();
		for(int i = 0; i < K; i++) {
			pheromones.add(pheromone);
		}
		 
		//initial solution randomized
		for(Ant a: ants) {
			Vector v = new BasicVector(nodeNr);
			for(int i = 0; i < nodeNr; i++) {
				v.set(i, rand.nextInt(2));
			}
			a.setSolution(v);
		}
		
		//Reference Point & Fitness Values of the current solution
		setRefPoint(graph, ants);
	}
	//TODO ref point
	protected void setRefPoint(CustomGraph graph, List<Ant> ants) {
		double minCR = cutRatio(graph, ants.get(0).getSolution());
		double minNRA = negativeRatioAssociation(graph, ants.get(0).getSolution()); 
		for(Ant a: ants) {
			Vector fitness = new BasicVector(2); 
			Vector sol = a.getSolution(); 
			double NRA = negativeRatioAssociation(graph, sol);
			double CR = cutRatio(graph, sol);
			fitness.set(0, NRA);
			fitness.set(1, CR);
			if(NRA > minNRA) { // NRA is a negative metric
				minNRA = NRA; 
			}
			if(CR < minCR){
				minCR = CR;
			}
		}
		refPoint = new BasicVector(2);
		refPoint.set(0, minNRA);
		refPoint.set(1, minCR);
	}
	
	/** Measures the link strength in between the maximal cliques. 
	 * 
	 * @param graph
	 * @param maxClq output of the MaximalCliqueGraphRepresentation
	 * @return Matrix of link strength in  between the nodes
	 */
	protected Matrix linkStrength(CustomGraph graph, HashMap<Integer,HashSet<Node>> maxClq) {
		int clqNr = maxClq.size(); 
		Matrix lkstrgth = new Basic2DMatrix(clqNr,clqNr);
		
		for(int i = 0; i < clqNr; i++) { 
			HashSet<Node> clq1 = maxClq.get(i); // select clique 1
			double clq1Size = clq1.size();
			for(int j = i + 1; j < clqNr; j++) {
				HashSet<Node> clq2 = maxClq.get(j); // select clique 2
				double clq2Size = clq2.size();
				
				HashSet<Node> diff12 = new HashSet<Node>(clq1); 
				diff12.removeAll(clq2); 
				double diff12size = diff12.size(); // size of clique 1 without nodes from clique 2
				
				double cdDist1 = 0;
				for(Node v1: diff12) {
					for(Node v2: clq2) {
						cdDist1 += CzechkanowskiDice(graph, v1, v2);  // Czechkanowski Dice Distance of the difference and clique 2
					}
				}
				
				HashSet<Node> diff21 = new HashSet<Node>(clq2); 
				diff21.removeAll(clq1);
				double diff21size = diff21.size(); // size of clique 2 without nodes from clique 1 
				
				double cdDist2 = 0;
				for(Node v1: diff21) {
					for(Node v2: clq1) {
						cdDist2 += CzechkanowskiDice(graph, v1, v2); // Czechkanowski Dice Distance of the difference and clique 1
					}
				}
				
				double lstr = cdDist2/(diff21size*clq1Size)*cdDist1/(diff12size*clq2Size);
				lstr = Math.sqrt(lstr);
				lkstrgth.set(i, j, lstr); // set matrix (entries have a triangular form)
				
			}
		}
		
		return lkstrgth;
	}
	
	/**
	 * Version of the adjusted Czechkanowski/Sorensen Dice Distance. The number of neighbors is changed to the average if it lay below the average. 
	 * @param graph a graph from which v1 and v2 are taken
	 * @param v1 node which is in a clique
	 * @param v2 node which is not in the same clique as v1
	 * @return adjusted Czechkanowski Dice distance
	 */
	protected double CzechkanowskiDice(CustomGraph graph, Node v1, Node v2) {
		NodeCursor nbors1 = v1.neighbors();
		NodeCursor nbors2 = v2.neighbors(); 

		int nbor1size = nbors1.size()/2; 
		int nbor2size = nbors2.size()/2; 
		
		double olapsize = 0; 
		
		for(int i = 0 ; i <nbors1.size(); i++) {
			Node n1 = nbors1.node();
			for(int j = 0 ; j <nbors1.size(); j++) {
				Node n2 = nbors2.node(); 
			
				if(n2 == n1) {
					olapsize++;
					break; 
				}
				
				if(nbors2.ok() == true){
					nbors2.cyclicNext();
				}
				else {
					break;
				}
			}
			
			if(nbors1.ok() == true){
				nbors1.cyclicNext();
			}
			else {
				break;
			}
		}
		double edgeNr = graph.edgeCount()/2;
		double nodeNr = graph.nodeCount(); 
		double avgDegr = 2*edgeNr/nodeNr;
		double tmp1 = avgDegr - nbor1size; 
		double tmp2 = avgDegr - nbor2size; 
		
		double lmbd1 = Double.max(0, tmp1);
		double lmbd2 = Double.max(0, tmp2);
		
		return olapsize/(lmbd1 + nbor1size + lmbd2 + nbor2size);
	}
	
	/**
	 * Evaluation of the cover of a graph. This measures the intra-link sparesity and should be minimized. 
	 * @param graph
	 * @param cover to evaluate on the graph
	 * @return negative Ratio Association
	 */
	protected double negativeRatioAssociation(CustomGraph graph, Vector sol) {
		double NRA = 0; 
		int comNr = (int) sol.max()+1; // starts with community 0
		
		List<Vector> members= new ArrayList<Vector>();
		double[] zeros = new double[sol.length()];
		Arrays.fill(zeros, 0);
		Vector v_hlp = new BasicVector(zeros);
		for(int j = 0; j < comNr; j++) {
			members.add(v_hlp);
		}
		
		for(int j = 0; j < sol.length(); j++) {
			int com = (int)sol.get(j);
			Vector v = new BasicVector();
			v = members.get(com).copy(); //separate the vector per community
			v.set(j, 1);
			members.set(com, v);
		}
		for(int i = 0; i<comNr; i++) {
			Vector v = members.get(i);
			NRA -= cliqueInterconectivity(graph, v, v)/v.sum();
		}
		
		return NRA;
	}
	
	/**
	 * Evaluation of the cover of a graph. This measures the inter-link density and should be minimized. 
	 * @param graph
	 * @param cover to evaluate on the graph
	 * @return Cut Ratio
	 */
	protected double cutRatio(CustomGraph graph, Vector sol) {
		double CR = 0; 
		int comNr = (int) sol.max()+1; 
		
		List<Vector> members= new ArrayList<Vector>();
		double[] zeros = new double[sol.length()];
		Arrays.fill(zeros, 0);
		Vector v_hlp = new BasicVector(zeros);
		for(int j = 0; j < comNr; j++) {
			members.add(v_hlp);
		}
		
		double[] ones = new double[sol.length()];
		Arrays.fill(ones, 1);
		Vector one = new BasicVector(zeros);
		for(int j = 0; j < sol.length(); j++) {
			int com = (int)sol.get(j);
			Vector v = members.get(com).copy(); //separate the vector per community
			v.set(j, 1);
			members.set(com, v);
		}

		for(int i = 0; i<comNr; i++) {
			Vector v = members.get(i); 
			Vector v_compl = one.subtract(v); // calculate inverse of v
			
			CR += cliqueInterconectivity(graph, v, v_compl)/v.sum();
		}
		
		return CR;
	}
	
	/** 
	 * Measure for the interconnectivity of two communities (can also be the same communities!)
	 * @param graph 
	 * @param com1 - community 1
	 * @param com2 - community 2
	 * @return shared edges between two communities
	 */
	protected double cliqueInterconectivity(CustomGraph graph, Vector com1, Vector com2) {
		double L = 0; // counter of edges in between the communities
		int com1Len = com1.length(); 
		Node[] nodes = graph.getNodeArray(); 
		for(int i = 0; i < com1Len; i++) { // iterates over all nodes
			if(com1.get(i) == 0) { // filters out all nodes within a community from the community vector
				continue;
			}
			Node n1 = nodes[i]; 
			for(int j = 0; j < com1Len; j++) { // iterates over all nodes
				if(com2.get(j) == 0) { // filters out all nodes within a community from the community vector
					continue;
				}
				Node n2 = nodes[j];
				
				if (graph.containsEdge(n1, n2)) { // if two nodes from these two communities are connected by an edge
					L += 1; 
				}
			}
		}
		return L;
	}
	

	
	protected void constructSolution(CustomGraph graph, Ant ant, boolean initial) {
		Matrix phi = new Basic2DMatrix(); 
		int group = ant.getGroup();
		Matrix m = pheromones.get(group); 
		Vector weight = ant.getWeight();
		if(initial) {
			for(int i = 0; i<nodeNr; i++) {
				for(int j = 0; j < nodeNr; j++) {
					phi.set(i, j, Math.pow(m.get(i, j), alpha)* Math.pow(heuristic.get(i, j), beta));
				}
			}
		}
		else {
			Vector sol = ant.getSolution(); 
			for(int i = 0; i<nodeNr; i++) {
				for(int j = 0; j < nodeNr; j++) {
					double update = m.get(i, j)+1/(1+TchebyehoffDecomposition(sol, weight))*isEdgeinSol(sol, i, j);
					phi.set(i, j, Math.pow(update, alpha)*Math.pow(heuristic.get(i, j), beta));
				}
			}
		}
		
		Random rand = new Random();
		
		List<Node> unvisited = new ArrayList<Node>(); 
		unvisited.addAll(Arrays.asList(graph.getNodeArray()));
		Node curr = unvisited.get(rand.nextInt());
		unvisited.remove(curr); 
		while(unvisited.isEmpty() != true) {
			if(rand.nextFloat() < R) {
				
			} else {
				
			}
			
		}
	}
	
	protected void updateEP() {
		
	}
	
	/**
	 * Updates the pheromone matrix: two mechanisms
	 * 1) pheromone evaporation on an edge
	 * 2) pheromone deposit on an edge
	 * @param addedSol solutions added to EP (non-dominated solution) (weight vector, solution vector)  
	 * @param groups of nodes (groups number, weight vectors)
	 */
	protected void updatePheromoneMatrix(HashMap<Vector,Vector> addedSol, HashMap<Integer,HashSet<Vector>> groups) {
		List<Matrix> pherUpdate = new ArrayList<Matrix>(); 
		for(int k = 0; k < K; k++) {
			Matrix m = pherUpdate.get(k);
			Matrix persist = pheromones.get(k).multiply(rho); // persistence of the pheromones on a path
			for(int i = 0; i < nodeNr; i++) { // starting point of edge
				for(int j = 0; j < nodeNr; i++) { // ending point of edge
					double delta = 0; 
					int l = 0;
					Iterator<Vector> it = addedSol.keySet().iterator();
					while(it.hasNext()){
						Vector weight = it.next();
						if(groups.get(k).contains(weight)) {
							Vector sol = addedSol.get(weight);
							delta += 1/(1 + TchebyehoffDecomposition(sol, weight)) * isEdgeinSol(sol, i, j); // changed pheromones on a path 
							l++; 
						}
						
					}
					m.set(i, j, delta + persist.get(i, j)); // evaporation + deposit
				}
			}
			pheromones.set(k, m);
		}
		
	}
	
	/**
	 * 
	 * 
	 * @param sol solution-vector
	 * @param lambdas weight vector of the solution
	 * @return result of the Tschebyeheff decomposition
	 */
	protected double TchebyehoffDecomposition(Vector sol, Vector lambda) {
		double tc = 0; 
		double RC = 0;
		return 0; 
		//TODO
	}
	
	/**
	 * Checks whether the edge (k,l) is contained in solution
	 * @param sol solution vector
	 * @param k index of a node
	 * @param l index of another node
	 * @return whether edge (k,l) is contained in solution sol
	 */
	protected double isEdgeinSol(Vector sol, int k, int l) {
		if(sol.get(k) == 1 && sol.get(l) == 1) {
			return 1; 
		}
		return 0; 
	}
	
	protected void updateCurrentSolution() {
		//TODO
	}
	
	
	/**
	 * Returns a log representing the concrete algorithm execution.
	 * @return The log.
	 */
	@Override
	public CoverCreationType getAlgorithmType(){
		return CoverCreationType.ANT_COLONY_OPTIMIZATION;
	}
	
	/**
	 * Returns all graph types the algorithm is compatible with.
	 * @return The compatible graph types.
	 * An empty set if the algorithm is not compatible with any type.
	 */
	public Set<GraphType> compatibleGraphTypes(){
		Set<GraphType> compatibilities = new HashSet<GraphType>();
		compatibilities.add(GraphType.ZERO_WEIGHTS);
		return compatibilities;
	};
	
	
	@Override
	public Map<String,String> getParameters(){
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put(MAX_ITERATIONS, Double.toString(maxIterations));
		parameters.put(NUMBER_OF_ANTS, Integer.toString(M));
		parameters.put(EVAPORATION_FACTOR, Double.toString(rho));
		parameters.put(MCR_THRESHOLD, Double.toString(threshold));
		return parameters;
	}

	@Override
	public void setParameters(Map<String, String> parameters) throws IllegalArgumentException {
		if(parameters.containsKey(MAX_ITERATIONS)) {
			maxIterations = Integer.parseInt(parameters.get(MAX_ITERATIONS));
			if(maxIterations <= 0) {
				throw new IllegalArgumentException();
			}
			parameters.remove(MAX_ITERATIONS);
		}
		if(parameters.containsKey(NUMBER_OF_ANTS)) {
			M = Integer.parseInt(parameters.get(NUMBER_OF_ANTS));
			if(M <= 0) {
				throw new IllegalArgumentException();
			}
			parameters.remove(NUMBER_OF_ANTS);
		}
		if(parameters.containsKey(EVAPORATION_FACTOR)) {
			rho = Double.parseDouble(parameters.get(EVAPORATION_FACTOR));
			if(rho < 0 || rho > 1) {
				throw new IllegalArgumentException();
			}
			parameters.remove(EVAPORATION_FACTOR);
		}
		
		if(parameters.containsKey(MCR_THRESHOLD)) {
			threshold = Double.parseDouble(parameters.get(MCR_THRESHOLD));
			if(threshold < 0 || threshold > 1) {
				throw new IllegalArgumentException();
			}
			parameters.remove(MCR_THRESHOLD);
		}
		
		if(parameters.containsKey(NUMMER_OF_NEIGHBORS)) {
			nearNbors = Integer.parseInt(parameters.get(NUMMER_OF_NEIGHBORS));
			if(nearNbors < 0 || nearNbors >= nodeNr) {
				throw new IllegalArgumentException();
			}
			parameters.remove(NUMMER_OF_NEIGHBORS);
		}
	
		if(parameters.size() > 0) {
			throw new IllegalArgumentException();
		}
	}
	
	
}

