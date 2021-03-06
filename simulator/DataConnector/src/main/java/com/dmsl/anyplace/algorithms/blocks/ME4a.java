/**
 * Anyplace Simulator:  A trace-driven evaluation and visualization of IoT Data Prefetching in Indoor Navigation SOAs
 *
 * Author(s): Zacharias Georgiou, Panagiotis Irakleous

 * Supervisor: Demetrios Zeinalipour-Yazti
 *
 * Copyright (c) 2017 Data Management Systems Laboratory, University of Cyprus
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/
 */
package com.dmsl.anyplace.algorithms.blocks;

import com.dmsl.anyplace.algorithms.DijkstraAlgorithm;
import com.dmsl.anyplace.buildings.clean.CleanBuilding;
import com.dmsl.anyplace.buildings.clean.CleanPoi;
import com.dmsl.anyplace.buildings.clean.Pair;

import java.util.*;
import java.util.stream.Collectors;


public class ME4a extends AlgorithmBlocks {

	int startNode;

	Random rnd;

	private int topMdestinations;
	private int topMdestinationsSteps;
	private boolean radar;
	private boolean isFirstTime = true;

	private List<CleanPoi> destNodes;
	private boolean destNodesFinished[];
	private int destNodesCount[];

	private DijkstraAlgorithm dijkstra;

	public ME4a(CleanPoi[] vertices, int nBlocks, Random rnd, float probLost, int topMdestinations,
			int topMdestinationSteps, boolean radar, CleanBuilding building) {
		super(6, vertices, nBlocks, probLost);
		this.rnd = rnd;
		this.topMdestinations = topMdestinations;
		this.topMdestinationsSteps = topMdestinationSteps;
		this.radar = radar;
		createGraph(building);
	}

	/**
	 * Call this only one time
	 * 
	 * @param building
	 */
	public void createGraph(CleanBuilding building) {
		Map<Integer, DijkstraAlgorithm.Vertex> vertices = new HashMap<>();
		List<DijkstraAlgorithm.Edge> edges = new ArrayList<>();
		CleanPoi[] pois = building.getVertices();
		int ids = 0;
		for (CleanPoi poi : pois)
			vertices.put(poi.getId(), new DijkstraAlgorithm.Vertex(poi.getId()));

		for (CleanPoi poi : pois) {
			int from = poi.getId();

			double lat1 = Double.parseDouble(poi.getLat());
			double lon1 = Double.parseDouble(poi.getLon());

			for (Pair p : poi.getNeighbours()) {
				int to = p.getTo();
				double lat2 = Double.parseDouble(pois[to].getLat());
				double lon2 = Double.parseDouble(pois[to].getLon());
				double dist = distance(lat1, lon1, lat2, lon2);
				edges.add(new DijkstraAlgorithm.Edge(ids++, vertices.get(from), vertices.get(to), dist));
			}
		}

		DijkstraAlgorithm.Graph graph = new DijkstraAlgorithm.Graph(vertices, edges);
		// System.out.println(vertices);
		dijkstra = new DijkstraAlgorithm(graph);

	}

	public List<CleanPoi> getTopNodes(int source, CleanPoi[] vertices, int topMdestinations, int topMdestinationSteps) {
		List<CleanPoi> res = new ArrayList<CleanPoi>();

		Map<Integer, Integer> visited = new HashMap<>();

		Queue<BFSNode> queue = new LinkedList<BFSNode>();
		queue.add(new BFSNode(source, 0));
		visited.put(source, source);
		res.add(vertices[source]);

		while (!queue.isEmpty()) {
			BFSNode current = queue.poll();
			if (current.level == topMdestinationSteps)
				break;

			for (Pair p : vertices[current.id].getNeighboursToList()) {
				Integer to = p.getTo();
				if (!visited.containsKey(to)) {
					visited.put(to, to);
					queue.add(new BFSNode(to, current.level + 1));
					res.add(vertices[to]);

				}
			}

		}

		return res.stream().sorted((x1, x2) -> x1.getPagerank() > x2.getPagerank() ? -1 : 1).limit(topMdestinations)
				.collect(Collectors.toList());

	}

	public ArrayList<Integer> run(int u) {

		if (radar || isFirstTime) {
			destNodes = getTopNodes(u, vertices, topMdestinations, topMdestinationsSteps);
			isFirstTime = false;
		}

		// Reinitialize
		destNodesFinished = new boolean[destNodes.size()]; // All destnodes are
															// unvisited
		destNodesCount = new int[destNodes.size()]; // Set to zero download
													// nodes

		// System.out.println(destNodes.stream().map(x ->
		// x.getPagerank()).collect(Collectors.toList()));

		PriorityQueue<ME4Node> blocksNode = new PriorityQueue<ME4Node>();
		ArrayList<ME4Node> localVisited = new ArrayList<>();
		ArrayList<ME4Node> toReturn = new ArrayList<>();
		int count = 0;
		this.startNode = u;

		ME4Node n = new ME4Node(null, u, 0.0, 0.0, -1);

		blocksNode.add(n);
		localVisited.add(n);

		while (!blocksNode.isEmpty()) {
			ME4Node current = blocksNode.remove();

			// Add the node if its not the beginning or its already seen
			if (u != current.u && !visited.contains(current.u)) {

				// If that path is finished continue, assign a new one
				int oldDestIndex = current.destNodeIndex;
				current.destNodeIndex = changePathIfNecessary(current, oldDestIndex,true);

				// If it changed we put it back to the queue.
				// If it changed but all destinations are visited we skip it.
				if (current.destNodeIndex != oldDestIndex && !allDestVisited()) {
					blocksNode.add(current);
					continue;
				}

				// If current is one of the goals, it means we finished that
				// path
				if (destNodes.get(current.destNodeIndex).getId() == current.u)
					destNodesFinished[current.destNodeIndex] = true;

				visited.add(current.u);
				toReturn.add(current);
				if (++count == nBlocks) {
					break;
				}
			}

			// System.out.println("Possible Nodes:");
			for (ME4Node ot : getPossibleNodes(current)) {

				if (!localVisited.contains(ot)) {
					blocksNode.add(ot);
					localVisited.add(ot);
				}
			}
		}
		// Reverse the path
		Stack<ME4Node> stack = new Stack<>();
		while (n != null) {
			stack.push(n);
			n = n.parent;
		}

		ArrayList<Integer> res = new ArrayList<>();
		for (ME4Node node : toReturn) {
			res.add(node.u);
		}

		return res;
	}

	public PriorityQueue<ME4Node> getPossibleNodes(ME4Node n) {

		PriorityQueue<ME4Node> res = new PriorityQueue<>();
		CleanPoi old = vertices[n.u];
		double lat0 = Double.parseDouble(old.getLat());
		double lon0 = Double.parseDouble(old.getLon());
		for (Pair p : vertices[n.u].getNeighboursToList()) {

			CleanPoi me = vertices[p.getTo()];
			double lat1 = Double.parseDouble(me.getLat());
			double lon1 = Double.parseDouble(me.getLon());
			int destNodeIndex = n.destNodeIndex;

			// It means we are at the beginning, without knowing which
			// destination
			// we should follow. Here we need to assign the destination
			if (destNodeIndex == -1) {
				double minDistance = Double.MAX_VALUE;
				int destNodeNewID = -1;
				int i = 0;
				for (CleanPoi destNode : destNodes) {
					double lat = Double.parseDouble(destNode.getLat());
					double lon = Double.parseDouble(destNode.getLon());
					double dist = distance(lat, lon, lat1, lon1);
					if (dist < minDistance) {
						minDistance = dist;
						destNodeNewID = i;
					}
					i++;
				}
				// Assign it
				destNodeIndex = destNodeNewID;
			}

			// Change Path if necessary
			destNodeIndex = changePathIfNecessary(n, destNodeIndex, false);

			CleanPoi destination = destNodes.get(destNodeIndex);

			// double lat2 = Double.parseDouble(destination.getLat());
			// double lon2 = Double.parseDouble(destination.getLon());

			// Weight 2

			double w2 = dijkstra.execute(dijkstra.nodes.get(me.getId()), dijkstra.nodes.get(destination.getId()));
			// double distToDestination = distance(lat1, lon1, lat2, lon2);

			// Weight 1
			double dist = distance(lat1, lon1, lat0, lon0);
			res.add(new ME4Node(n, p.getTo(), n.weight1 + dist, w2, destNodeIndex));

		}
		return res;
	}

	public boolean allDestVisited() {
		for (int i = 0; i < destNodesFinished.length; i++)
			if (!destNodesFinished[i])
				return false;
		return true;
	}

	public int changePathIfNecessary(ME4Node current, int destNodeIndex, boolean toAssingW2) {

		boolean allFinished = allDestVisited();
		CleanPoi me = vertices[current.u];
		// Check if everything is finished, we give a random node
		if (allFinished) {

			destNodeIndex = rnd.nextInt(destNodesFinished.length);

			// Assign new w2
			if (toAssingW2) {
				CleanPoi destination = destNodes.get(destNodeIndex);
				double w2 = dijkstra.execute(dijkstra.nodes.get(me.getId()), dijkstra.nodes.get(destination.getId()));
				current.weight2 = w2;
			}
		} else {

			// If the path is finished we switch to a non-visited close one
			if (destNodesFinished[destNodeIndex]) {

				double lat1 = Double.parseDouble(me.getLat());
				double lon1 = Double.parseDouble(me.getLon());
				double minDistance = Double.MAX_VALUE;
				int destNodeNewID = -1;

				for (int i = 0; i < destNodes.size(); i++) {
					if (destNodesFinished[i])
						continue;
					CleanPoi destNode = destNodes.get(i);

					double lat = Double.parseDouble(destNode.getLat());
					double lon = Double.parseDouble(destNode.getLon());
					double dist = distance(lat, lon, lat1, lon1);
					if (dist < minDistance) {
						minDistance = dist;
						destNodeNewID = i;
					}

				}

				// Assign it
				destNodeIndex = destNodeNewID;
				if (toAssingW2) {
					CleanPoi destination = destNodes.get(destNodeIndex);
					double w2 = dijkstra.execute(dijkstra.nodes.get(me.getId()),
							dijkstra.nodes.get(destination.getId()));
					current.weight2 = w2;
				}
			} else {
				// Nothing changes. We continue with the same
			}

		}
		return destNodeIndex;

	}

	private static double distance(double lat1, double lon1, double lat2, double lon2) {

		final int R = 6371; // Radius of the earth

		Double latDistance = Math.toRadians(lat2 - lat1);
		Double lonDistance = Math.toRadians(lon2 - lon1);
		Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c * 1000; // convert to meters

		distance = Math.pow(distance, 2);

		return Math.sqrt(distance);
	}

	/**
	 * Small wrapper class
	 * 
	 * @author zgeorg03
	 *
	 */
	private class BFSNode {
		int id;
		int level;

		public BFSNode(int id, int level) {
			this.id = id;
			this.level = level;
		}
	}

	private class ME4Node implements Comparable<ME4Node> {
		ME4Node parent;
		int destNodeIndex;
		int u;
		double weight1;
		double weight2;

		public ME4Node(ME4Node parent, int u, double weight1, double weight2, int destNodeIndex) {
			this.parent = parent;
			this.u = u;
			this.weight1 = weight1;
			this.weight2 = weight2;
			this.destNodeIndex = destNodeIndex;

		}

		@Override
		public String toString() {

			String s1 = String.format("%.2f", weight1);
			String s2 = String.format("%.2f", weight2);

			return u + "(\t" + s1 + "\t" + s2 + "\t" + destNodeIndex + ")";

		}

		public boolean equals(Object obj) {
			ME4Node o = (ME4Node) (obj);
			return this.u == o.u;
		}

		@Override
		public int compareTo(ME4Node o) {

			// If I have less => better
			if ((weight1 + weight2) < (o.weight1 + o.weight2))
				return -1;
			if ((weight1 + weight2) > (o.weight1 + o.weight2))
				return 1;

			return 0;
		}
	}

}