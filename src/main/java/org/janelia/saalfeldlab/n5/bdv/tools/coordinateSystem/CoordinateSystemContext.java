package org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5VSources;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.CoordinateSystem;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.scene.NgffScene;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.graph.TransformGraph;

/**
 * The source-agnostic model backing the {@link CoordinateSystemCard},
 * containing the {@link TransformGraph} the sources are aligned through, the
 * detected coordinate-system names, the currently-selected name, and (once
 * sources have been built) the {@link CoordinateSystemSourceTransformer} that re-aligns
 * them.
 * <p>
 * How the graph is obtained is not this class's concern -- the {@code from*}
 * factory methods derive it (and the names / default selection) eagerly from
 * whatever source is available (an {@link NgffScene}, or a single
 * {@link OmeNgffMultiScaleMetadata}), then discard that source. The card and
 * viewer therefore depend only on the derived state, not on how it was produced
 * or on a reader remaining open.
 **/
public class CoordinateSystemContext {

	private final TransformGraph graph;

	private final List<String> coordinateSystemNames;

	private String selectedCoordinateSystemName;

	private CoordinateSystemSourceTransformer aligner;

	private CoordinateSystemContext(
			final TransformGraph graph,
			final List<String> coordinateSystemNames,
			final String selectedCoordinateSystemName) {

		this.graph = graph;
		this.coordinateSystemNames = coordinateSystemNames;
		this.selectedCoordinateSystemName = selectedCoordinateSystemName;
	}

	/**
	 * Creates a context from an {@link NgffScene}, using its transform graph
	 * (which reads the scene's external datasets, hence the reader and base
	 * path). The initial selection defaults to the scene's default coordinate
	 * system.
	 *
	 * @param n5
	 *            the reader used to resolve the scene's graph
	 * @param scene
	 *            the scene whose coordinate systems back the card
	 * @param basePath
	 *            the path {@code scene} was read from (may be null or empty)
	 * @return the context
	 */
	public static CoordinateSystemContext fromScene(final N5Reader n5, final NgffScene scene, final String basePath) {

		final TransformGraph graph = buildGraph(n5, scene, basePath);
		final List<String> names = graph != null
				? namesFromGraph(graph)
				: namesFromCoordinateSystems(scene == null ? null : scene.getCoordinateSystems());
		final String selected = scene == null ? null : scene.getDefaultCoordinateSystemName();
		return new CoordinateSystemContext(graph, names, selected);
	}

	/**
	 * Creates a scene-free context from a single multiscales dataset's own
	 * coordinate systems and multiscale-level "additional" coordinate
	 * transformations (see {@link OmeNgffMultiScaleMetadata#getGraph()}). The
	 * initial selection defaults to the dataset's own local space
	 * ({@code coordinateSystems[0]}), so sources start in their native space
	 * until the user picks another coordinate system.
	 *
	 * @param multiscale
	 *            the multiscale dataset whose coordinate systems back the card
	 * @return the context
	 */
	public static CoordinateSystemContext fromMultiscale(final OmeNgffMultiScaleMetadata multiscale) {

		final TransformGraph graph = buildGraph(multiscale);
		final List<String> names = graph != null
				? namesFromGraph(graph)
				: namesFromCoordinateSystems(multiscale == null ? null : multiscale.getCoordinateSystems());
		final String selected = firstCoordinateSystemName(multiscale == null ? null : multiscale.getCoordinateSystems());
		return new CoordinateSystemContext(graph, names, selected);
	}

	/**
	 * Detects the coordinate systems declared by the given discovered
	 * {@code metadata}, returning a context for them, or {@code null} if there is
	 * nothing for a card to offer -- that is, if no entry is an
	 * {@link OmeNgffMetadata}, or the first one that is declares fewer than two
	 * coordinate systems (with only its own local space there is no alternative
	 * to select).
	 * <p>
	 * Only the first {@link OmeNgffMetadata} entry backs the context, since a
	 * context has a single graph; datasets each carrying their own disjoint graph
	 * cannot be resolved against one another. Relating several datasets to a
	 * shared coordinate system is what an {@link NgffScene} is for -- use
	 * {@link #fromScene(N5Reader, NgffScene, String)} in that case.
	 *
	 * @param metadata
	 *            the discovered dataset metadata
	 * @return the context, or {@code null} if no coordinate systems are offered
	 */
	public static CoordinateSystemContext fromMetadata(final List<? extends N5Metadata> metadata) {

		if (metadata == null)
			return null;

		for (final N5Metadata m : metadata) {

			if (!(m instanceof OmeNgffMetadata))
				continue;

			final OmeNgffMultiScaleMetadata[] multiscales = ((OmeNgffMetadata)m).multiscales;
			if (multiscales == null || multiscales.length == 0)
				continue;

			final CoordinateSystemContext context = fromMultiscale(multiscales[0]);
			if (context.getCoordinateSystemNames().size() > 1)
				return context;
		}
		return null;
	}

	/**
	 * @return all detected coordinate-system names (the graph's full node set,
	 *         path-qualified for scenes e.g. {@code "CBCT/physical"}), sorted
	 *         alphabetically
	 */
	public List<String> getCoordinateSystemNames() {

		return coordinateSystemNames;
	}

	/**
	 * @return the currently-selected coordinate system, or {@code null}
	 */
	public String getSelectedCoordinateSystemName() {

		return selectedCoordinateSystemName;
	}

	public void setSelectedCoordinateSystemName(final String coordinateSystemName) {

		this.selectedCoordinateSystemName = coordinateSystemName;
	}

	/**
	 * @return the transform graph the sources are aligned through (built once
	 *         and reused for both the coordinate-system names and the
	 *         {@link CoordinateSystemSourceTransformer}), or {@code null} if it could not
	 *         be resolved
	 */
	public TransformGraph getGraph() {

		return graph;
	}

	/**
	 * @return the aligner that re-aligns this context's sources into a selected
	 *         coordinate system, or {@code null} until one has been installed
	 *         (see {@link org.janelia.saalfeldlab.n5.bdv.N5VSources#buildN5Sources(N5Reader, NgffScene, String, CoordinateSystemContext, bdv.cache.SharedQueue, List, List, bdv.util.BdvOptions)})
	 */
	public CoordinateSystemSourceTransformer getAligner() {

		return aligner;
	}

	public void setAligner(final CoordinateSystemSourceTransformer aligner) {

		this.aligner = aligner;
	}

	private static TransformGraph buildGraph(final N5Reader n5, final NgffScene scene, final String basePath) {

		if (scene == null)
			return null;

		try {
			return scene.getGraph(n5, basePath);
		} catch (final Exception e) {
			return null;
		}
	}

	private static TransformGraph buildGraph(final OmeNgffMultiScaleMetadata multiscale) {

		if (multiscale == null)
			return null;

		try {
			return multiscale.getGraph();
		} catch (final Exception e) {
			return null;
		}
	}

	/**
	 * The coordinate-system names registered in {@code graph} (its full node
	 * set), sorted alphabetically.
	 */
	private static List<String> namesFromGraph(final TransformGraph graph) {

		return graph.getCoordinateSystems().coordinateSystems()
				.map(CoordinateSystem::getName)
				.sorted()
				.collect(Collectors.toList());
	}

	/**
	 * Fallback used when the graph could not be resolved: the names of the given
	 * coordinate systems, sorted alphabetically.
	 */
	private static List<String> namesFromCoordinateSystems(final CoordinateSystem[] coordinateSystems) {

		final List<String> names = new ArrayList<>();
		if (coordinateSystems != null)
			for (final CoordinateSystem cs : coordinateSystems)
				names.add(cs.getName());
		names.sort(null);
		return names;
	}

	/**
	 * @return the name of the first of the given coordinate systems, or
	 *         {@code null} if there are none
	 */
	private static String firstCoordinateSystemName(final CoordinateSystem[] coordinateSystems) {

		return (coordinateSystems == null || coordinateSystems.length == 0)
				? null : coordinateSystems[0].getName();
	}
}
