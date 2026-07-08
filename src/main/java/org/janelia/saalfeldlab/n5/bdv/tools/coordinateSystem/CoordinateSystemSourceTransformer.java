package org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem;

import java.util.List;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.Common;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.graph.TransformGraph;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.graph.TransformPath;

import bdv.tools.transformation.TransformedSource;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Transforms already-built {@link TransformedSource}s into a chosen coordinate
 * system.
 *
 * Done by recomputing a transformation for each source from a shared
 * {@link TransformGraph} and applying it via
 * {@link TransformedSource#setFixedTransform(AffineTransform3D)}.
 * <p>
 * The graph is target-independent (it is the whole scene graph), so it is built
 * once and reused for every {@link #transformTo(String)} call; only the
 * {@code graph.path(localSpace, targetCoordinateSystem)} lookup depends on the
 * selected coordinate system.
 * <p>
 * When used in the context of bigdataviewer, callers are responsible for
 * requesting one (e.g. {@code viewerPanel.requestRepaint()}) after
 * {@link #alignTo(String)}.
 */
public class CoordinateSystemSourceTransformer {

	/**
	 * The sources built for one dataset (one per channel, all sharing the same
	 * transformation) together with the qualified names of the dataset's own
	 * coordinate system(s), used to look up a path into the target coordinate
	 * system in the {@link TransformGraph}.
	 */
	public static class Binding {

		private final List<TransformedSource<?>> sources;

		private final List<String> candidateLocalSpaceNames;

		public Binding(final List<TransformedSource<?>> sources, final List<String> candidateLocalSpaceNames) {

			this.sources = sources;
			this.candidateLocalSpaceNames = candidateLocalSpaceNames;
		}

		public List<TransformedSource<?>> getSources() {

			return sources;
		}

		public List<String> getCandidateLocalSpaceNames() {

			return candidateLocalSpaceNames;
		}
	}

	private final N5Reader n5;

	private final TransformGraph graph;

	private final List<Binding> bindings;

	public CoordinateSystemSourceTransformer(final N5Reader n5, final TransformGraph graph, final List<Binding> bindings) {

		this.n5 = n5;
		this.graph = graph;
		this.bindings = bindings;
	}

	public List<Binding> getBindings() {

		return bindings;
	}

	/**
	 * Transforms every bound source into {@code coordinateSystemName}.
	 * <p>
	 * For each {@link Binding}, the transform from the dataset's own coordinate
	 * system into {@code coordinateSystemName} is resolved from the graph and
	 * set as the source's fixed transform. Bindings whose dataset cannot reach
	 * {@code coordinateSystemName} are reset to the identity transform (rather
	 * than left at a previously-selected coordinate system's transform).
	 *
	 * @param coordinateSystemName
	 *            the name of the target coordinate system
	 */
	public void transformTo(final String coordinateSystemName) {

		for (final Binding binding : bindings) {

			final AffineTransform3D resolved = resolve(binding.getCandidateLocalSpaceNames(), coordinateSystemName);
			final AffineTransform3D fixed = resolved != null ? resolved : new AffineTransform3D();

			for (final TransformedSource<?> source : binding.getSources())
				source.setFixedTransform(fixed);
		}
	}

	/**
	 * Finds the {@link AffineTransform3D} mapping any of {@code candidateLocalSpaceNames}
	 * into {@code coordinateSystemName} via the graph, taking the first that
	 * resolves.
	 *
	 * @param candidateLocalSpaceNames
	 *            the qualified names of the dataset's own coordinate system(s)
	 * @param coordinateSystemName
	 *            the name of the target coordinate system
	 * @return the transform into {@code coordinateSystemName}, or {@code null}
	 *         if none of the candidates can reach it
	 */
	private AffineTransform3D resolve(final List<String> candidateLocalSpaceNames, final String coordinateSystemName) {

		for (final String localSpaceName : candidateLocalSpaceNames) {
			final Optional<TransformPath> transformPath = graph.path(localSpaceName, coordinateSystemName);
			if (transformPath.isPresent())
				Common.toAffine3D(n5, graph, transformPath.get().flatTransforms());
		}
		return null;
	}
}
