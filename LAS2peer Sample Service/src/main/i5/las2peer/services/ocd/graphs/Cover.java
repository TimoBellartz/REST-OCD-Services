package i5.las2peer.services.ocd.graphs;

import i5.las2peer.services.ocd.algorithms.CoverCreationLog;
import i5.las2peer.services.ocd.algorithms.CoverCreationType;
import i5.las2peer.services.ocd.metrics.OcdMetricLog;
import i5.las2peer.services.ocd.metrics.OcdMetricType;
import i5.las2peer.services.ocd.utils.NonZeroEntriesVectorProcedure;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.la4j.matrix.Matrix;
import org.la4j.matrix.sparse.CCSMatrix;
import org.la4j.vector.Vector;
import org.la4j.vector.Vectors;

import y.base.Node;
import y.base.NodeCursor;


@Entity
@IdClass(CoverId.class)
public class Cover {

	/////////////////// DATABASE COLUMN NAMES
	
	/*
	 * Database column name definitions.
	 */
	public static final String graphIdColumnName = "GRAPH_ID";
	public static final String graphUserColumnName = "USER_NAME";
	private static final String nameColumnName = "NAME";
	private static final String descriptionColumnName = "DESCRIPTION";
	public static final String idColumnName = "ID";
//	private static final String lastUpdateColumnName = "LAST_UPDATE";
	private static final String creationMethodColumnName = "CREATION_METHOD";
	
	/*
	 * Field name definitions for JPQL queries.
	 */
	
	public static final String GRAPH_FIELD_NAME = "graph";
	public static final String CREATION_METHOD_FIELD_NAME  = "creationMethod";
	public static final String METRICS_FIELD_NAME = "metrics";
	public static final String ID_FIELD_NAME = "id";
	
	///////////////////////// ATTRIBUTES
	/**
	 * System generated persistence id.
	 */
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = idColumnName)
	private long id;
	/**
	 * The graph that the cover is based on.
	 */
	@Id
	//@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumns( {
		@JoinColumn(name = graphIdColumnName, referencedColumnName = CustomGraph.idColumnName),
		@JoinColumn(name = graphUserColumnName, referencedColumnName = CustomGraph.userColumnName)
	})
	private CustomGraph graph = new CustomGraph();	
	/**
	 * The name of the cover.
	 */
	@Column(name = nameColumnName)
	private String name = "";
	/**
	 * A description of the cover.
	 */
	@Column(name = descriptionColumnName)
	private String description = "";
//	/**
//	 * Last time of modification.
//	 */
//	@Version
//	@Column(name = lastUpdateColumnName)
//	private Timestamp lastUpdate;
	/**
	 * Logged data about the algorithm that created the cover.
	 */
	@OneToOne(orphanRemoval = true, cascade={CascadeType.ALL})
	@JoinColumn(name = creationMethodColumnName)
	private CoverCreationLog creationMethod = new CoverCreationLog(CoverCreationType.UNDEFINED, new HashMap<String, String>(), new HashSet<GraphType>());
	@OneToMany(mappedBy = "cover", orphanRemoval = true, cascade={CascadeType.ALL} /*, fetch=FetchType.LAZY */)
	private List<Community> communities = new ArrayList<Community>();
	@OneToMany(mappedBy = "cover", orphanRemoval = true, cascade={CascadeType.ALL})
	private List<OcdMetricLog> metrics = new ArrayList<OcdMetricLog>();
	
	/////////////////////////////////////////// METHODS AND CONSTRUCTORS
	
	/*
	 * Only for persistence purposes.
	 */
	protected Cover() {
	}
	
	public Cover(CustomGraph graph) {
		this.graph = graph;
	}
	
	/**
	 * Creates an instance of a cover by deriving the communities from a membership matrix.
	 * Note that the membership matrix (and consequently the cover) will automatically be row normalized.
	 * @param graph The corresponding graph.
	 * @param memberships A membership matrix, with non negative entries. Each row i contains the belonging factors of the node with index i
	 * of the corresponding graph. Hence the number of rows corresponds the number of graph nodes and the number of columns the
	 * number of communities.
	 */
	public Cover(CustomGraph graph, Matrix memberships) {
		this.graph = graph;
		setMemberships(memberships, true);
	}

	public long getId() {
		return id;
	}
	
	public void setGraph(CustomGraph graph) {
		this.graph = graph;
	}
	
	public CustomGraph getGraph() {
		return graph;
	}

	public Matrix getMemberships() {
		Matrix memberships = new CCSMatrix(graph.nodeCount(), communities.size());
		Map<CustomNode, Node> reverseNodeMap = new HashMap<CustomNode, Node>();
		NodeCursor nodes = graph.nodes();
		while(nodes.ok()) {
			Node node = nodes.node();
			reverseNodeMap.put(graph.getCustomNode(node), node);
			nodes.next();
		}
		for(int i=0; i<communities.size(); i++) {
			Community community = communities.get(i);
			for(Map.Entry<Node, Double> membership : community.getMemberships().entrySet()) {
				memberships.set(membership.getKey().index(), i, membership.getValue());
			}
		}
		return memberships;
	}
	
	/*
	 * Sets the communities from a membership matrix. All metric logs (besides optionally the execution time) will be removed from the cover.
	 * Note that the membership matrix (and consequently the cover) will automatically be row normalized.
	 * @param memberships A membership matrix, with non negative entries. Each row i contains the belonging factors of the node with index i
	 * of the corresponding graph. Hence the number of rows corresponds the number of graph nodes and the number of columns the
	 * number of communities.
	 * @param keepExecutionTime Decides whether the (first) execution time metric log is kept.
	 */
	protected void setMemberships(Matrix memberships, boolean keepExecutionTime) {
		if(memberships.rows() != graph.nodeCount()) {
			throw new IllegalArgumentException("The row number of the membership matrix must correspond to the graph node count.");
		}
		communities.clear();
		OcdMetricLog executionTime = getMetric(OcdMetricType.EXECUTION_TIME);
		metrics.clear();
		if(executionTime != null && keepExecutionTime) {
			metrics.add(executionTime);
		}
		memberships = this.normalizeMembershipMatrix(memberships);
		Node[] nodes = graph.getNodeArray();
		for(int j=0; j<memberships.columns(); j++) {
			Community community = new Community(this);
			communities.add(community);
		}
		for(int i=0; i<memberships.rows(); i++) {
			NonZeroEntriesVectorProcedure procedure = new NonZeroEntriesVectorProcedure();
			memberships.getRow(i).eachNonZero(procedure);
			List<Integer> nonZeroEntries = procedure.getNonZeroEntries();
			for(int j : nonZeroEntries) {
				Community community = communities.get(j);
				community.setBelongingFactor(nodes[i], memberships.get(i, j));
			}
			
		}
	}

	/**
	 * Sets the communities from a membership matrix. All metric logs (besides optionally the execution time) will be removed from the cover.
	 * Note that the membership matrix (and consequently the cover) will automatically be row normalized.
	 * @param memberships A membership matrix, with non negative entries. Each row i contains the belonging factors of the node with index i
	 * of the corresponding graph. Hence the number of rows corresponds the number of graph nodes and the number of columns the
	 * number of communities.
	 */
	public void setMemberships(Matrix memberships) {
		setMemberships(memberships, false);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

//	public Timestamp getLastUpdate() {
//		return lastUpdate;
//	}

	public CoverCreationLog getCreationMethod() {
		return creationMethod;
	}

	public void setCreationMethod(CoverCreationLog creationMethod) {
		if(creationMethod != null) {
			this.creationMethod = creationMethod;
		}
		else {
			this.creationMethod = new CoverCreationLog(CoverCreationType.UNDEFINED, new HashMap<String, String>(), new HashSet<GraphType>());
		}
	}

	public List<OcdMetricLog> getMetrics() {
		return metrics;
	}

	public void setMetrics(List<OcdMetricLog> metrics) {
		this.metrics.clear();
		for(OcdMetricLog metric : metrics) {
			if(metric != null)
				this.metrics.add(metric);
		}
	}

	/**
	 * Returns the first metric occurrence with the corresponding type.
	 * @param metricType
	 * @return The metric. Null if no such metric exists.
	 */
	public OcdMetricLog getMetric(OcdMetricType metricType) {
		for(OcdMetricLog metric : this.metrics){
			if(metricType == metric.getType()) {
				return metric;
			}
		}
		return null;
	}
	
	public void addMetric(OcdMetricLog metric) {
		if(metric != null) {
			this.metrics.add(metric);
		}
	}
	
	public void removeMetric(OcdMetricLog metric) {
		this.metrics.remove(metric);
	}
	
	public int communityCount() {
		return communities.size();
	}
	
	public List<Integer> getCommunityIndices(Node node) {
		List<Integer> communityIndices = new ArrayList<Integer>();
		for(int j=0; j < communities.size(); j++) {
			if(this.communities.get(j).getBelongingFactor(node) > 0) {
				communityIndices.add(j);
			}
		}
		return communityIndices;
	}
	
	public double getBelongingFactor(Node node, int communityIndex) {
		return communities.get(communityIndex).getBelongingFactor(node);
	}
	
	public String getCommunityName(int communityIndex) {
		return communities.get(communityIndex).getName();
	}
	
	public void setCommunityName(int communityIndex, String name) {
		communities.get(communityIndex).setName(name);
	}
	
	public Color getCommunityColor(int communityIndex) {
		return communities.get(communityIndex).getColor();
	}
	
	public void setCommunityColor(int communityIndex, Color color) {
		communities.get(communityIndex).setColor(color);
	}
	
	/*
	 * Overload for internal reuse and performance.
	 * Normalizes each row of a matrix using the one norm.
	 * Note that a unit vector column is added for each row that is equal
	 * to zero to create a separate node community.
	 * @param matrix The memberships matrix to be normalized and set.
	 * @return The normalized membership matrix.
	 */
	protected Matrix normalizeMembershipMatrix(Matrix matrix) {
		List<Integer> zeroRowIndices = new ArrayList<Integer>();
		for(int i=0; i<matrix.rows(); i++) {
			Vector row = matrix.getRow(i);
			double norm = row.fold(Vectors.mkManhattanNormAccumulator());
			if(norm != 0) {
				row = row.divide(norm);
				matrix.setRow(i, row);
			}
			else {
				zeroRowIndices.add(i);
			}
		}
		/*
		 * Resizing also rows is required in case there are zero columns.
		 */
		matrix = matrix.resize(graph.nodeCount(), matrix.columns() + zeroRowIndices.size());
		for(int i = 0; i < zeroRowIndices.size(); i++) {
			matrix.set(zeroRowIndices.get(i), matrix.columns() - zeroRowIndices.size() + i, 1d);
		}
		return matrix;
	}
	
	/**
	 * Filters the cover membership matrix by removing insignificant membership values.
	 * The cover is then normalized and empty communities are removed. All metric results
	 * besides the execution time are removed as well.
	 * All entries below the threshold will be set to 0, unless they are the maximum 
	 * belonging factor of the node.
	 * @param threshold 
	 * 
	 */
	public void filterMembershipsbyThreshold(double threshold) {
		Matrix memberships = this.getMemberships();
		for(int i=0; i<memberships.rows(); i++) {
			setRowEntriesBelowThresholdToZero(memberships, i, threshold);
		}
		this.setMemberships(memberships , true);
		removeEmptyCommunities();
	}

	@Override
	public String toString() {
		String coverString = "Cover: " + getName() + "\n";
		coverString += "Graph: " + getGraph().getName() + "\n";
		coverString += "Algorithm: " + getCreationMethod().getType().toString() + "\n" + "params:" + "\n";
		for(Map.Entry<String, String> entry : getCreationMethod().getParameters().entrySet()) {
			coverString += entry.getKey() + " = " + entry.getValue() + "\n";
		}
		coverString += "Community Count: " + communityCount() + "\n";
		OcdMetricLog metric;
		for(int i=0; i<metrics.size(); i++) {
			metric = metrics.get(i);
			coverString += metric.getType().toString() + " = ";
			coverString += metric.getValue() + "\n" + "params:" + "\n";
			for(Map.Entry<String, String> entry : metric.getParameters().entrySet()) {
				coverString += entry.getKey() + " = " + entry.getValue() + "\n";
			}
			coverString += "\n";
		}
		coverString += "Membership Matrix\n";
		coverString += getMemberships().toString();
		return coverString;
	}
	
	public int getCommunitySize(int communityIndex) {
		return communities.get(communityIndex).getSize();
	}
	
	/*
	 * Sets all matrix entries of one row which are lower than the threshold and the rows max entry to zero.
	 * @param matrix The matrix being filtered.
	 * @param rowIndex The index of the row being filtered.
	 * @param threshold The threshold.
	 */
	protected void setRowEntriesBelowThresholdToZero(Matrix matrix, int rowIndex, double threshold) {
		Vector row = matrix.getRow(rowIndex);
		double rowThreshold = Math.min(row.fold(Vectors.mkMaxAccumulator()), threshold);
		BelowThresholdEntriesVectorProcedure procedure = new BelowThresholdEntriesVectorProcedure(rowThreshold);
		row.eachNonZero(procedure);
		List<Integer> belowThresholdEntries = procedure.getBelowThresholdEntries();
		for(int i : belowThresholdEntries) {
			row.set(i, 0);
		}
		matrix.setRow(rowIndex, row);
	}
	
	/*
	 * Removes all empty communities from the graph.
	 * A community is considered as empty when the corresponding belonging factor
	 * equals 0 for each node.
	 */
	protected void removeEmptyCommunities() {
		Iterator<Community> it = communities.iterator();
		while(it.hasNext()) {
			Community community = it.next();
			if(community.getSize() == 0) {
				it.remove();
			}
		}
	}

	
}
