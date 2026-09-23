package org.janelia.saalfeldlab.n5.bdv;

import static bdv.BigDataViewer.createConverterToARGB;
import static bdv.BigDataViewer.wrapWithTransformedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5URI;
import org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem.CoordinateSystemContext;
import org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem.CoordinateSystemSourceTransformer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.CoordinateSystem;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.DefaultAxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalSpatialMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.scene.NgffScene;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.scene.NgffSceneMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v06.graph.TransformGraph;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvOptions;
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.lazy.Lazy;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class N5VSources {

	static final String[] imagePlusAxisOrder = new String[] {"x", "y", "c", "z", "t" };

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> int buildN5Sources(
			final N5Reader n5,
			final DataSelection dataSelection,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return buildN5Sources(n5,
				N5Viewer.unwrapMultichannelSelections(dataSelection),
				sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	/**
	 * Builds sources for every path referenced by {@code scene}, and installs a
	 * {@link CoordinateSystemSourceTransformer} on the given {@link CoordinateSystemContext}
	 * so the sources can later be re-transformed into a different coordinate system
	 * (see {@link CoordinateSystemSourceTransformer#transformTo(String)}). The sources are
	 * initially transformed into the context's
	 * {@link CoordinateSystemContext#getSelectedCoordinateSystemName() selected
	 * coordinate system}.
	 *
	 * @param n5
	 *            the reader
	 * @param scene
	 *            the scene whose referenced paths should be turned into sources
	 * @param basePath
	 *            the path {@code scene}'s paths are relative to (may be null or empty)
	 * @param context
	 *            the context (built from {@code scene}) onto which the transformer is
	 *            installed
	 * @param sharedQueue
	 *            the shared queue
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which sources should be added
	 * @param sourcesAndConverters
	 *            list of {@link SourceAndConverter}s to which sources should be added
	 * @param options
	 *            bdv options
	 * @return the number of timepoints among the built sources
	 */
	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> int buildN5Sources(
			final N5Reader n5,
			final NgffScene scene,
			final String basePath,
			final CoordinateSystemContext context,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return buildN5Sources(n5,
				Collections.singletonList(new NgffSceneMetadata(basePath == null ? "" : basePath, scene)),
				context, sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	/**
	 * Builds sources for the given {@code selectedMetadata}, and installs a
	 * {@link CoordinateSystemSourceTransformer} on the given
	 * {@link CoordinateSystemContext} so the sources can later be
	 * re-transformed into a different coordinate system. The sources are
	 * initially transformed into the context's
	 * {@link CoordinateSystemContext#getSelectedCoordinateSystemName() selected
	 * coordinate system}.
	 * <p>
	 * Any {@link NgffSceneMetadata} entry is expanded into the datasets it
	 * references, so a scene and a plain multiscale dataset are built the same
	 * way. They differ only in the local space each source is transformed out
	 * of, which records: for a scene, the path-qualified name the scene records
	 * or the dataset's own {@code coordinateSystems[0]}
	 *
	 * @param n5
	 *            the reader
	 * @param selectedMetadata
	 *            the metadata for which sources should be built
	 * @param context
	 *            the context onto which the transformer is installed
	 * @param sharedQueue
	 *            the shared queue
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which sources should be
	 *            added
	 * @param sourcesAndConverters
	 *            list of {@link SourceAndConverter}s to which sources should be
	 *            added
	 * @param options
	 *            bdv options
	 * @return the number of timepoints among the built sources
	 */
	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
			final CoordinateSystemContext context,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		final ExpandedSelection expanded = expandSelections(n5, selectedMetadata);

		// sources are built in their native transform; the initial alignment is
		// expressed below as the transformer's first transformTo(...) call
		final List<List<TransformedSource<?>>> transformedSourcesByMetadata = new ArrayList<>();
		final int numTimepoints = buildN5Sources(n5, expanded.metadata,
				transformedSourcesByMetadata, sharedQueue, converterSetups, sourcesAndConverters, options);

		installTransformer(n5, context, transformedSourcesByMetadata, expanded.localSpaceNames);

		return numTimepoints;
	}

	/**
	 * The datasets to build sources for, and the
	 * coordinate-system name(s) each can be transformed out of.
	 */
	private static class ExpandedSelection {

		final List<N5Metadata> metadata = new ArrayList<>();

		final List<List<String>> localSpaceNames = new ArrayList<>();

		void add(final N5Metadata m, final List<String> names) {

			metadata.add(m);
			localSpaceNames.add(names);
		}
	}

	/**
	 * Applies {@link #expandSelection(N5Reader, N5Metadata, List)} to each selected
	 * entry, keeping every resulting dataset paired with its local coordinate-system
	 * name(s).
	 *
	 * @param n5               the reader
	 * @param selectedMetadata the selected metadata to expand
	 * @return the datasets to build sources from, and their local coordinate-system names
	 */
	private static ExpandedSelection expandSelections(final N5Reader n5, final List<N5Metadata> selectedMetadata) {

		final ExpandedSelection expandedSelection = new ExpandedSelection();
		for (final N5Metadata metadata : selectedMetadata) {

			final List<List<String>> localSpaceNames = new ArrayList<>();
			final List<N5Metadata> expanded = expandSelection(n5, metadata, localSpaceNames);
			for (int k = 0; k < expanded.size(); ++k)
				expandedSelection.add(expanded.get(k), localSpaceNames.get(k));
		}
		return expandedSelection;
	}

	/**
	 * Expands a selected metadata entry into the datasets that sources can be built
	 * from.
	 * <p>
	 * For example, an {@link NgffSceneMetadata} cannot be opened in isolation, but
	 * names a set of paths. This method replaces it with the
	 * {@link OmeNgffMetadata} parsed at each of those paths (resolved against the
	 * scene's own path). Any other metadata is a dataset already and is returned as
	 * a single-element list.
	 * <p>
	 * Each returned entry's {@link N5Metadata#getPath() path} is a distinct,
	 * independently-loadable dataset. Scene references that have no OME-ngff
	 * metadata are logged and skipped.
	 *
	 * @param n5       the reader
	 * @param metadata the selected metadata to expand
	 * @return the datasets {@code metadata} expands into
	 */
	public static List<N5Metadata> expandSelection(final N5Reader n5, final N5Metadata metadata) {

		return expandSelection(n5, metadata, null);
	}

	/**
	 * Backs {@link #expandSelection(N5Reader, N5Metadata)} and {@link #expandSelections}. When
	 * {@code localSpaceNamesOut} is non-null it is filled, parallel to the returned list,
	 * with the coordinate-system name(s) each expanded dataset can be transformed out of:
	 * for a scene source, the path-qualified name the scene relates it by
	 * ({@code CBCT/physical}); for a dataset on its own, its own {@code coordinateSystems[0]}
	 * ({@code physical}). A name of the wrong form matches no graph node, so its sources
	 * reset to identity. Callers that only need the datasets pass {@code null}.
	 */
	private static List<N5Metadata> expandSelection(final N5Reader n5, final N5Metadata metadata,
			final List<List<String>> localSpaceNamesOut) {

		final List<N5Metadata> expanded = new ArrayList<>();
		if (!(metadata instanceof NgffSceneMetadata)) {
			expanded.add(metadata);
			if (localSpaceNamesOut != null)
				localSpaceNamesOut.add(localSpaceNames(metadata));
			return expanded;
		}

		final NgffSceneMetadata sceneMetadata = (NgffSceneMetadata)metadata;
		final NgffScene scene = sceneMetadata.getScene();
		final String basePath = N5URI.normalizeGroupPath(sceneMetadata.getPath());
		final OmeNgffMetadataParser parser = new OmeNgffMetadataParser(n5);

		for (final String path : scene.getPaths()) {
			final String resolvedPath = basePath.isEmpty() ? path : basePath + "/" + path;
			final Optional<N5Metadata> referenced = parser.parseMetadata(n5, resolvedPath)
					.map(m -> (N5Metadata)m);
			if (referenced.isPresent()) {
				expanded.add(referenced.get());
				if (localSpaceNamesOut != null)
					localSpaceNamesOut.add(scene.localSpaceNames(path));
			} else
				System.err.println("N5VSources: scene at \"" + sceneMetadata.getPath()
						+ "\" references \"" + path + "\", which has no ome-ngff metadata; skipping it.");
		}
		return expanded;
	}

	/**
	 * Assembles a {@link CoordinateSystemSourceTransformer} from per-dataset
	 * {@link TransformedSource}s (index {@code i} = the sources built for one
	 * dataset) and per-dataset local coordinate-system names (index {@code i} =
	 * the space(s) that dataset can be transformed out of), installs it on
	 * {@code context}, and applies the initial transform to the context's
	 * selected coordinate system. Does nothing if the context has no graph.
	 *
	 * @param n5
	 *            the reader (retained by the transformer)
	 * @param context
	 *            the context onto which the transformer is installed
	 * @param transformedSourcesByMetadata
	 *            the transformed sources, grouped per dataset
	 * @param localSpaceNamesByMetadata
	 *            the local coordinate-system names, per dataset (index-aligned
	 *            with {@code transformedSourcesByMetadata})
	 */
	private static void installTransformer(
			final N5Reader n5,
			final CoordinateSystemContext context,
			final List<List<TransformedSource<?>>> transformedSourcesByMetadata,
			final List<List<String>> localSpaceNamesByMetadata) {

		final TransformGraph graph = context.getGraph();
		if (graph == null)
			return;

		final List<CoordinateSystemSourceTransformer.Binding> bindings = new ArrayList<>();
		for (int i = 0; i < transformedSourcesByMetadata.size(); ++i)
			bindings.add(new CoordinateSystemSourceTransformer.Binding(
					transformedSourcesByMetadata.get(i),
					localSpaceNamesByMetadata.get(i)));

		final CoordinateSystemSourceTransformer transformer = new CoordinateSystemSourceTransformer(n5, graph, bindings);
		context.setAligner(transformer);

		final String coordinateSystemName = context.getSelectedCoordinateSystemName();
		if (coordinateSystemName != null)
			transformer.transformTo(coordinateSystemName);
	}

	/**
	 * The local coordinate-system name(s) a dataset can be transformed out of: for an
	 * {@link OmeNgffMetadata}, the name of its own {@code coordinateSystems[0]}
	 * (the space its per-level pixel-to-physical transforms output to); for any
	 * other metadata type, none (it has no multiscale-level coordinate systems).
	 *
	 * @param metadata
	 *            the dataset metadata
	 * @return the local coordinate-system names for {@code metadata}
	 */
	private static List<String> localSpaceNames(final N5Metadata metadata) {

		final List<String> names = new ArrayList<>();
		if (metadata instanceof OmeNgffMetadata) {
			final CoordinateSystem[] coordinateSystems = ((OmeNgffMetadata)metadata).multiscales[0].getCoordinateSystems();
			if (coordinateSystems != null && coordinateSystems.length > 0)
				names.add(coordinateSystems[0].getName());
		}
		return names;
	}

	/**
	 * Builds sources for the given list of {@link N5Metadata}, without a
	 * {@link CoordinateSystemContext}, so no coordinate-system card and no
	 * re-transforming. Any {@link NgffSceneMetadata} entry is still expanded into
	 * the datasets it references (see {@link #expandSelections}); those sources are simply left
	 * in their native transforms.
	 *
	 * @param n5
	 *            the reader
	 * @param selectedMetadata
	 *            the metadata for which sources should be built
	 * @param sharedQueue
	 *            the shared queue
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which sources should be added
	 * @param sourcesAndConverters
	 *            list of {@link SourceAndConverter}s to which sources should be added
	 * @param options
	 *            bdv options
	 * @return the number of timepoints among the built sources
	 */
	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, M extends AxisMetadata & N5Metadata> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return buildN5Sources(n5, expandSelections(n5, selectedMetadata).metadata, (List<List<TransformedSource<?>>>)null,
				sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	/**
	 * As {@link #buildN5Sources(N5Reader, List, SharedQueue, List, List, BdvOptions)},
	 * but additionally records the built {@link TransformedSource}s grouped by
	 * their originating {@code selectedMetadata} entry, so they can later be
	 * re-transformed (see {@link CoordinateSystemSourceTransformer}).
	 * <p>
	 * Sources are built in their native (pixel-to-local-space) transform. Aligning
	 * them into some other coordinate system is done afterwards, by the
	 * {@link CoordinateSystemSourceTransformer} that {@code installTransformer}
	 * puts on a {@link CoordinateSystemContext}.
	 *
	 * @param transformedSourcesByMetadata
	 *            if non-null, populated with one list per {@code selectedMetadata}
	 *            entry (parallel to it, in the same order) of the
	 *            {@link TransformedSource}s built for that entry (one per
	 *            channel; empty for entries that produced no transformed
	 *            sources). Any existing contents are added to.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, M extends AxisMetadata & N5Metadata> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
			final List<List<TransformedSource<?>>> transformedSourcesByMetadata,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		final ArrayList<MetadataSource<?>> additionalSources = new ArrayList<>();

		// is2D should be true at the end of this loop if all sources are 2D
		boolean is2D = true;
		int numTimepoints = 1;

		int i;
		for (i = 0; i < selectedMetadata.size(); ++i) {
			String[] datasetsToOpen = null;
			AffineTransform3D[] transforms = null;

			final N5Metadata metadata = selectedMetadata.get(i);
			final String srcName = sourceBaseName(n5, metadata);

			final DatasetsAndTransforms dt = datasetsAndTransforms(metadata);
			if (dt != null) {
				datasetsToOpen = dt.datasets;
				transforms = dt.transforms;
			} else {
				// non-spatial dataset metadata is shown via MetadataSources instead
				final List<MetadataSource<?>> addTheseSources = MetadataSource
						.buildMetadataSources(n5, (N5DatasetMetadata)metadata);
				if (addTheseSources != null)
					additionalSources.addAll(addTheseSources);
			}

			if (datasetsToOpen == null || datasetsToOpen.length == 0) {
				// keep transformedSourcesByMetadata parallel to selectedMetadata
				if (transformedSourcesByMetadata != null)
					transformedSourcesByMetadata.add(new ArrayList<>());
				continue;
			}

			@SuppressWarnings("rawtypes")
			final RandomAccessibleInterval[] images = new RandomAccessibleInterval[datasetsToOpen.length];
			String unit = "pixel";
			for (int s = 0; s < images.length; ++s) {

				final RandomAccessibleInterval<?> img = loadImage(n5, datasetsToOpen[s]);

				final CanonicalImage canonical = permuteToCanonical(img, transforms[s], metadata);
				images[s] = canonical.img;
				unit = canonical.unit;

				is2D &= canonical.img.dimension(3) == 1;
				numTimepoints = (int)Math.max(numTimepoints, canonical.img.dimension(4));
			}

			// TODO: Ideally, the volatile views should use a caching strategy
			// where blocks are enqueued with reverse resolution level as
			// priority. However, this would require to predetermine the number
			// of resolution levels, which would man a lot of duplicated code
			// for analyzing selectedMetadata. Instead, wait until SharedQueue
			// supports growing numPriorities, then revisit.
			// See https://github.com/imglib/imglib2-cache/issues/18.
			// Probably it should look like this:
//			sharedQueue.ensureNumPriorities(images.length);
//			for (int s = 0; s < images.length; ++s) {
//				final int priority = images.length - 1 - s;
//				final CacheHints cacheHints = new CacheHints(LoadingStrategy.BUDGETED, priority, false);
//				vimages[s] = VolatileViews.wrapAsVolatile(images[s], sharedQueue, cacheHints);
//			}

			final T type = (T)images[0].getType();

			// this could / should be generalized
			final double rx = transforms[0].get(0, 0);
			final double ry = transforms[0].get(1, 1);
			final double rz = transforms[0].get(2, 2);

			/* there still can be many channels */
			final List<Pair<Source<T>, Source<V>>> sourcePairs = createSource(
					type,
					srcName,
					images,
					transforms,
					sharedQueue,
					new FinalVoxelDimensions(unit, rx, ry, rz));

			final int startIndex = sourcesAndConverters.size();
			for (final Pair<Source<T>, Source<V>> sourcePair : sourcePairs) {
				addSourceToListsGenericType(sourcePair.getA(), sourcePair.getB(), i + 1, converterSetups, sourcesAndConverters);
			}

			// capture the TransformedSources just built for this metadata entry
			if (transformedSourcesByMetadata != null) {
				final List<TransformedSource<?>> built = new ArrayList<>();
				for (int si = startIndex; si < sourcesAndConverters.size(); ++si) {
					final Source<?> spimSource = sourcesAndConverters.get(si).getSpimSource();
					if (spimSource instanceof TransformedSource)
						built.add((TransformedSource<?>)spimSource);
				}
				transformedSourcesByMetadata.add(built);
			}
		}

		for (@SuppressWarnings("rawtypes") final MetadataSource src : additionalSources) {
			if (src.numTimePoints() > numTimepoints)
				numTimepoints = src.numTimePoints();

			addSourceToListsGenericType(src, i + 1, converterSetups, sourcesAndConverters);
		}

		if (is2D)
			options.is2D();

		return numTimepoints;
	}
	

	/*
	 * If the image is of type {@link LabelMultisetType} to {@link UnsignedLongType}.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends NumericType<T> & NativeType<T>> RandomAccessibleInterval<?> loadImage(
			final N5Reader n5, final String dataset) {

		final CachedCellImg<?, ?> img = N5Utils.openVolatile(n5, dataset);
		final Object t = img.getType();
		if( t instanceof LabelMultisetType ) {

			final CachedCellImg<LabelMultisetType, ?> lmsImg = (CachedCellImg<LabelMultisetType, ?>)img;
			return convertLabelMultisetCache(lmsImg);
		}

		return (RandomAccessibleInterval<T>)img;
	}

	@SuppressWarnings("unchecked")
	private static <T extends NumericType<T> & NativeType<T>, V extends NumericType<V> & NativeType<V>> List<Pair<Source<T>, Source<V>>> createSource(
			final T type,
			final String srcName,
			final RandomAccessibleInterval<T>[] images,
			final AffineTransform3D[] transforms,
			final SharedQueue sharedQueue,
			final VoxelDimensions vd) {

		final long nChannels = images[0].dimension(2);

		final ArrayList<Pair<Source<T>, Source<V>>> sourcePairs = new ArrayList<>();
		for (int c = 0; c < nChannels; ++c) {

			final RandomAccessibleInterval<T>[] channels = new RandomAccessibleInterval[images.length];
			for (int level = 0; level < images.length; ++level)
				channels[level] = Views.hyperSlice(images[level], 2, c);

			final String channelName = channelSourceName(srcName, c, nChannels);
			final RandomAccessibleIntervalMipmapSource4D<T> source = new RandomAccessibleIntervalMipmapSource4D<>(
					channels, type, transforms, vd, channelName, true);

			// TODO fix generics
			final ValuePair<Source<T>, Source<V>> pair = new ValuePair(
					source,
					source.asVolatile(sharedQueue));
			sourcePairs.add(pair);
		}
		return sourcePairs;
	}

	/**
	 * Returns the names of the sources that {@link #buildN5Sources} would create for
	 * {@code metadata}, in order. 
	 * <p>
	 * A single metadata entry can map to several sources through:
	 * <ul>
	 * <li>a metadata group (e.g. multichannel) expands to one entry per
	 * child</li>
	 * <li>a single dataset with a channel axis of length {@code N} expands to
	 * {@code N} channel sources.</li>
	 * </ul>
	 * Because both this method and the build share the same helpers
	 * ({@link #datasetsAndTransforms}, {@link #permuteToCanonical},
	 * {@link #sourceBaseName}, {@link #channelSourceName}), the names and their count
	 * always match what actually gets built.
	 * <p>
	 * Determining the channel count opens the finest-scale image lazily,
	 * it reads array metadata but no pixel data.
	 *
	 * @param n5
	 *            the reader
	 * @param metadata
	 *            the metadata to inspect
	 * @return the ordered source names this metadata would produce; or empty
	 */
	public static List<String> sourceNamesFor(final N5Reader n5, final N5Metadata metadata) {

		final List<String> names = new ArrayList<>();
		for (final N5Metadata leaf : N5Viewer.unwrapMultichannelSelections(
				new DataSelection(n5, Collections.singletonList(metadata))))
			leafSourceNames(n5, leaf, names);
		return names;
	}

	/**
	 * Appends the source names that a single (already group-unwrapped) metadata entry
	 * would produce. Mirrors the per-entry logic in {@link #buildN5Sources}.
	 */
	private static void leafSourceNames(final N5Reader n5, final N5Metadata metadata, final List<String> names) {

		final DatasetsAndTransforms dt = datasetsAndTransforms(metadata);
		if (dt == null) {
			// non-spatial dataset metadata → one MetadataSource per built source (no channel split)
			final List<MetadataSource<?>> built = MetadataSource.buildMetadataSources(n5, (N5DatasetMetadata)metadata);
			if (built != null)
				for (final MetadataSource<?> src : built)
					names.add(src.getName());
			return;
		}

		final String base = sourceBaseName(n5, metadata);
		final long nChannels = channelCount(n5, metadata, dt);
		for (long c = 0; c < nChannels; ++c)
			names.add(channelSourceName(base, c, nChannels));
	}

	/**
	 * The number of channel sources a single dataset expands into: the length of the
	 * channel axis after permuting the finest-scale image to the canonical XYCZT order
	 * (dimension 2), exactly as {@link #createSource} slices it.
	 */
	private static long channelCount(final N5Reader n5, final N5Metadata metadata, final DatasetsAndTransforms dt) {

		final RandomAccessibleInterval<?> img = loadImage(n5, dt.datasets[0]);
		// the transform is permuted in place for some metadata types; pass a throwaway
		return permuteToCanonical(img, new AffineTransform3D(), metadata).img.dimension(2);
	}

	/**
	 * The base source name for {@code metadata}: its {@link N5Metadata#getName() name},
	 * or the container's leaf name when the metadata has none.
	 */
	private static String sourceBaseName(final N5Reader n5, final N5Metadata metadata) {

		String srcName = metadata.getName();
		if (srcName == null || srcName.isEmpty())
			srcName = n5.getURI().toString().replaceFirst("/$", "").replaceFirst(".*/", "");
		return srcName;
	}

	/**
	 * The name of channel {@code channel} of a source with base name {@code baseName};
	 * single-channel sources keep the base name, multi-channel sources get a
	 * {@code _ch<channel>} suffix.
	 */
	static String channelSourceName(final String baseName, final long channel, final long nChannels) {

		return nChannels > 1 ? baseName + "_ch" + channel : baseName;
	}

	/** The finest-scale dataset paths and their pixel-to-physical transforms for a metadata entry. */
	private static class DatasetsAndTransforms {

		final String[] datasets;
		final AffineTransform3D[] transforms;

		DatasetsAndTransforms(final String[] datasets, final AffineTransform3D[] transforms) {
			this.datasets = datasets;
			this.transforms = transforms;
		}
	}

	/** A canonically-ordered (XYCZT) image and the physical unit derived alongside it. */
	private static class CanonicalImage {

		final RandomAccessibleInterval<?> img;
		final String unit;

		CanonicalImage(final RandomAccessibleInterval<?> img, final String unit) {
			this.img = img;
			this.unit = unit;
		}
	}

	/**
	 * Resolves the datasets to open and their transforms for a metadata entry, mirroring
	 * the metadata-type dispatch in {@link #buildN5Sources}. Returns {@code null} for
	 * non-spatial {@link N5DatasetMetadata}, which the build shows via
	 * {@link MetadataSource}s instead.
	 */
	private static DatasetsAndTransforms datasetsAndTransforms(final N5Metadata metadata) {

		if (metadata instanceof N5SingleScaleMetadata) {
			final N5SingleScaleMetadata singleScaleDataset = (N5SingleScaleMetadata)metadata;
			final String[] tmpDatasets = new String[]{singleScaleDataset.getPath()};
			final AffineTransform3D[] tmpTransforms = new AffineTransform3D[]{
					singleScaleDataset.spatialTransform3d()};

			final MultiscaleDatasets msd = MultiscaleDatasets.sort(tmpDatasets, tmpTransforms);
			return new DatasetsAndTransforms(msd.getPaths(), msd.getTransforms());
		} else if (metadata instanceof N5MultiScaleMetadata) {
			final N5MultiScaleMetadata multiScaleDataset = (N5MultiScaleMetadata)metadata;
			return new DatasetsAndTransforms(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
		} else if (metadata instanceof N5CosemMetadata) {
			final N5CosemMetadata singleScaleCosemDataset = (N5CosemMetadata)metadata;
			return new DatasetsAndTransforms(new String[]{singleScaleCosemDataset.getPath()},
					new AffineTransform3D[]{singleScaleCosemDataset.spatialTransform3d()});
		} else if (metadata instanceof CanonicalSpatialMetadata) {
			final CanonicalSpatialMetadata canonicalDataset = (CanonicalSpatialMetadata)metadata;
			return new DatasetsAndTransforms(new String[]{canonicalDataset.getPath()},
					new AffineTransform3D[]{canonicalDataset.getSpatialTransform().spatialTransform3d()});
		} else if (metadata instanceof OmeNgffMetadata) {
			final OmeNgffMetadata multiScaleDataset = (OmeNgffMetadata)metadata;
			final MultiscaleDatasets msd = MultiscaleDatasets
					.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
			return new DatasetsAndTransforms(msd.getPaths(), msd.getTransforms());
		} else if (metadata instanceof N5CosemMultiScaleMetadata) {
			final N5CosemMultiScaleMetadata multiScaleDataset = (N5CosemMultiScaleMetadata)metadata;
			final MultiscaleDatasets msd = MultiscaleDatasets
					.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
			return new DatasetsAndTransforms(msd.getPaths(), msd.getTransforms());
		} else if (metadata instanceof CanonicalMultiscaleMetadata) {
			final CanonicalMultiscaleMetadata multiScaleDataset = (CanonicalMultiscaleMetadata)metadata;
			final MultiscaleDatasets msd = MultiscaleDatasets
					.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
			return new DatasetsAndTransforms(msd.getPaths(), msd.getTransforms());
		} else if (metadata instanceof SpatialMetadata) {
			return new DatasetsAndTransforms(new String[]{metadata.getPath()},
					new AffineTransform3D[]{ ((SpatialMetadata)metadata).spatialTransform3d() });
		} else if (metadata instanceof N5DatasetMetadata) {
			return null;
		} else {
			return new DatasetsAndTransforms(new String[]{metadata.getPath()},
					new AffineTransform3D[]{new AffineTransform3D()});
		}
	}

	/**
	 * Permutes {@code img} into the canonical XYCZT dimension order, choosing
	 * the axis interpretation from {@code metadata} exactly as
	 * {@link #buildN5Sources} does, and returns it together with the physical
	 * unit. For COSEM/NGFF metadata {@code transform} is permuted in place to
	 * match.
	 */
	@SuppressWarnings("unchecked")
	private static <A extends AxisMetadata & N5Metadata> CanonicalImage permuteToCanonical(
			final RandomAccessibleInterval<?> img,
			final AffineTransform3D transform,
			final N5Metadata metadata) {

		final RandomAccessibleInterval<?> imagejImg;
		String unit = "pixel";
		if (metadata instanceof AxisMetadata)
		{
			imagejImg = AxisUtils.permute(img, (A)metadata, imagePlusAxisOrder);
			unit = unitFromAxes(((AxisMetadata)metadata).getAxes());
		}
		else if( metadata instanceof N5SingleScaleMetadata )
		{
			final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)metadata );
			imagejImg = AxisUtils.permute( img, axes, imagePlusAxisOrder );
			unit = ((N5SingleScaleMetadata)metadata).unit();
		}
		else if( isN5ViewerMultiscale(metadata))
		{
			final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)(((N5MultiScaleMetadata)metadata).getChildrenMetadata()[0]) );
			imagejImg = AxisUtils.permute( img, axes, imagePlusAxisOrder );
			unit = unitFromAxes(axes.getAxes());
		}
		else if( isCosemMultiscale(metadata))
		{
			final N5CosemMultiScaleMetadata cosemMulti = ((N5CosemMultiScaleMetadata)metadata);
			final N5CosemMetadata cosemMeta = cosemMulti.getChildrenMetadata()[0];
			imagejImg = permuteForImagePlus(img, transform, cosemMeta);
			unit = cosemMeta.unit();
		}
		else
		{
			final NgffSingleScaleAxesMetadata ngffMeta = isNgffMultiscale(metadata);
			if( ngffMeta != null ) {
				imagejImg = permuteForImagePlus(img, transform, ngffMeta);
				unit = ngffMeta.unit();
			}
			else
			{
				RandomAccessibleInterval< ? > imgTmp = img;
				while( imgTmp.numDimensions() < 5 )
					imgTmp = Views.addDimension(imgTmp, 0, 0 );
				imagejImg = imgTmp;
			}
		}
		return new CanonicalImage(imagejImg, unit);
	}

	private static String unitFromAxes(Axis[] axes) {

		final Optional<Axis> axisOpt = Arrays.stream(axes)
				.filter(x -> x.getType().equals(Axis.SPACE)).findFirst();

		if (axisOpt.isPresent())
			return axisOpt.get().getUnit();

		return "pixel";
	}

	private static boolean isN5ViewerMultiscale( final N5Metadata metadata )
	{
		if(metadata instanceof N5MultiScaleMetadata )
		{
			final N5MultiScaleMetadata ms = (N5MultiScaleMetadata)metadata;
			final N5SingleScaleMetadata[] children = ms.getChildrenMetadata();
			if( children.length > 0 )
				return children[0] instanceof N5SingleScaleMetadata;
		}
		return false;
	}

	private static boolean isCosemMultiscale( final N5Metadata metadata )
	{
		if(metadata instanceof N5CosemMultiScaleMetadata )
		{
			final N5CosemMultiScaleMetadata ms = (N5CosemMultiScaleMetadata)metadata;
			final N5CosemMetadata[] children = ms.getChildrenMetadata();
			if( children.length > 0 )
				return children[0] instanceof N5CosemMetadata;
		}
		return false;
	}

	private static NgffSingleScaleAxesMetadata isNgffMultiscale(final N5Metadata metadata) {

		if (metadata instanceof OmeNgffMetadata) {

			final OmeNgffMetadata ngff = (OmeNgffMetadata)metadata;
			final OmeNgffMultiScaleMetadata[] ms = ngff.multiscales;

			// TODO when do we not just take the first one?
			final NgffSingleScaleAxesMetadata[] children = ms[0].getChildrenMetadata();
			if( children.length > 0 )
				if( children[0] instanceof NgffSingleScaleAxesMetadata)
					return children[0];

		}

		return null;
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@code SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static <T> void addSourceToListsGenericType(
			final Source<T> source,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		addSourceToListsGenericType(source, null, setupId, converterSetups, sources);
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param volatileSource
	 *            corresponding volatile source.
	 * @param setupId
	 *            id of the new source for use in {@code SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static <T, V extends Volatile<T>> void addSourceToListsGenericType(
			final Source<T> source,
			final Source<V> volatileSource,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		final T type = source.getType();
		if (type instanceof RealType || type instanceof ARGBType || type instanceof VolatileARGBType)
			addSourceToListsNumericType(
					(Source)source,
					(Source)volatileSource,
					setupId,
					converterSetups,
					(List)sources);
		else
			throw new IllegalArgumentException("Unknown source type. Expected RealType, ARGBType, or VolatileARGBType");
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param volatileSource
	 *            corresponding volatile source.
	 * @param setupId
	 *            id of the new source for use in {@code SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static <T extends NumericType<T>, V extends Volatile<T> & NumericType<V>> void addSourceToListsNumericType(
			final Source<T> source,
			final Source<V> volatileSource,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		final SourceAndConverter<V> vsoc = (volatileSource == null)
				? null
				: new SourceAndConverter<>(volatileSource, createConverterToARGB(volatileSource.getType()));
		final SourceAndConverter<T> soc = new SourceAndConverter<>(
				source,
				createConverterToARGB(source.getType()),
				vsoc);
		final SourceAndConverter<T> tsoc = wrapWithTransformedSource(soc);

		converterSetups.add(BigDataViewer.createConverterSetup(tsoc, setupId));
		sources.add(tsoc);
	}
	
	/**
	 * Returns an image with dimensions in a canonical order XYCZY. Also
	 * permutes the given pixel to physical transform in-place.
	 *
	 * @param <T>
	 *            the type
	 * @param img
	 *            the image
	 * @param transform
	 *            the pixel to physical transfom
	 * @param meta
	 *            axis metadata
	 * @return a possibly permuted image
	 */
	private static <T, M extends N5Metadata, A extends AxisMetadata & N5Metadata> RandomAccessibleInterval<T> permuteForImagePlus(
			final RandomAccessibleInterval<T> img,
			AffineTransform3D transform,
			final A meta) {

		final int[] p = AxisUtils.findPermutationByName(meta, imagePlusAxisOrder);
		AxisUtils.fillPermutation(p);

		RandomAccessibleInterval<T> imgTmp = img;
		while (imgTmp.numDimensions() < 5)
			imgTmp = Views.addDimension(imgTmp, 0, 0);

		if (AxisUtils.isIdentityPermutation(p))
			return imgTmp;

		// update spatial transformation
		// exchange rows and columns of permutation matrix appropriately
		final int[] spatialPermutation = new int[]{p[0], p[1], p[3]};
		final AffineGet permTform = AxisUtils.axisPermutationTransform(spatialPermutation);
		transform.concatenate(permTform.inverse()).preConcatenate(permTform);

		return AxisUtils.permute(imgTmp, AxisUtils.invertPermutation(p));
	}
	
	private static RandomAccessibleInterval<VolatileUnsignedLongType> convertLabelMultisetVolatile( final CachedCellImg<LabelMultisetType,?> lmsImg ) {

		// TODO this isn't working (VolatileViews throws a NPE), but have not yet investigated why
		// see ViewCosem in n5-utils for something similar

		final RandomAccessibleInterval<Volatile<LabelMultisetType>> vimg = VolatileViews.wrapAsVolatile( lmsImg );
		return Converters.convert2(vimg,
				(a, b) -> {
					b.set(a.get().argMax());
					b.setValid(a.isValid());
				},
				VolatileUnsignedLongType::new);
	}

	private static CachedCellImg<UnsignedLongType, ?> convertLabelMultisetLazy(final CachedCellImg<LabelMultisetType, ?> lmsImg) {

		// use Lazy.generate to convert and cache
		final int[] cellDims = new int[lmsImg.numDimensions()];
		lmsImg.getCellGrid().cellDimensions(cellDims);

		return Lazy.generate(lmsImg, cellDims, new UnsignedLongType(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				x -> {
					final IntervalView<LabelMultisetType> in = (IntervalView<LabelMultisetType>)Views.interval(lmsImg, x);
					final Cursor<LabelMultisetType> inc = in.cursor();
					final Cursor<UnsignedLongType> outc = Views.flatIterable(x).cursor();
					while (outc.hasNext())
						outc.next().set(inc.next().argMax());
				});
	}

	private static CachedCellImg<UnsignedLongType, ?> convertLabelMultisetCache(final CachedCellImg<LabelMultisetType, ?> lmsImg) {

		final int[] cellDims = new int[lmsImg.numDimensions()];
		lmsImg.getCellGrid().cellDimensions(cellDims);

		return new ReadOnlyCachedCellImgFactory()
				.create(lmsImg.dimensionsAsLongArray(), new UnsignedLongType(),
						out -> {
							final IntervalView<LabelMultisetType> in = (IntervalView<LabelMultisetType>)Views.interval(lmsImg, out);
							final Cursor<LabelMultisetType> inc = in.cursor();
							final Cursor<UnsignedLongType> outc = out.cursor();
							while (outc.hasNext())
								outc.next().set(inc.next().argMax());
						},
						new ReadOnlyCachedCellImgOptions()
								.cellDimensions(cellDims)
								.volatileAccesses(true));
	}


}
