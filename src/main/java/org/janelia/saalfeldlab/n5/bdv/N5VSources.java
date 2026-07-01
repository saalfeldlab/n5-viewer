package org.janelia.saalfeldlab.n5.bdv;

import static bdv.BigDataViewer.createConverterToARGB;
import static bdv.BigDataViewer.wrapWithTransformedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5Reader;
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
import org.janelia.saalfeldlab.n5.universe.metadata.axes.DefaultAxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalSpatialMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.scene.NgffScene;

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
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class N5VSources {
	
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
	 * Builds sources for every path referenced by the given {@link NgffScene},
	 * resolving and parsing the {@link OmeNgffMetadata} at each path.
	 *
	 * @param n5
	 *            the reader
	 * @param scene
	 *            the scene whose referenced paths should be turned into sources
	 * @param basePath
	 *            the path that {@code scene}'s paths are relative to (may be null or empty)
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
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return buildN5Sources(n5, scene, scene.getDefaultCoordinateSystemName(), basePath,
				sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	/**
	 * Builds sources for every path referenced by the given {@link NgffScene}
	 * via a coordinate transformation involving the named coordinate system,
	 * resolving and parsing the {@link OmeNgffMetadata} at each path.
	 *
	 * @param n5
	 *            the reader
	 * @param scene
	 *            the scene whose referenced paths should be turned into sources
	 * @param coordinateSystemName
	 *            the name of the coordinate system whose referenced paths should
	 *            be turned into sources
	 * @param basePath
	 *            the path that {@code scene}'s paths are relative to (may be null or empty)
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
			final String coordinateSystemName,
			final String basePath,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		final OmeNgffMetadataParser parser = new OmeNgffMetadataParser(n5);
		final List<N5Metadata> selectedMetadata = new ArrayList<>();
		final String[] paths = coordinateSystemName == null ? scene.getPaths() : scene.getPaths(coordinateSystemName);
		for (final String path : paths) {
			final String resolvedPath = (basePath == null || basePath.isEmpty())
					? path : basePath + "/" + path;
			parser.parseMetadata(n5, resolvedPath).ifPresent(selectedMetadata::add);
		}

		return buildN5Sources(n5, selectedMetadata, sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, M extends AxisMetadata & N5Metadata> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
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
			String srcName = metadata.getName();
			if( srcName == null || srcName.isEmpty()) {
				srcName = n5.getURI().toString().replaceFirst("/$", "").replaceFirst(".*/", "");
			}

			// TODO: simplify this if/elseif block: much of these ifwall cases can be combined
			if (metadata instanceof N5SingleScaleMetadata) {
				final N5SingleScaleMetadata singleScaleDataset = (N5SingleScaleMetadata)metadata;
				final String[] tmpDatasets = new String[]{singleScaleDataset.getPath()};
				final AffineTransform3D[] tmpTransforms = new AffineTransform3D[]{
						singleScaleDataset.spatialTransform3d()};

				final MultiscaleDatasets msd = MultiscaleDatasets.sort(tmpDatasets, tmpTransforms);
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5MultiScaleMetadata) {
				final N5MultiScaleMetadata multiScaleDataset = (N5MultiScaleMetadata)metadata;
				datasetsToOpen = multiScaleDataset.getPaths();
				transforms = multiScaleDataset.spatialTransforms3d();
			} else if (metadata instanceof N5CosemMetadata) {
				final N5CosemMetadata singleScaleCosemDataset = (N5CosemMetadata)metadata;
				datasetsToOpen = new String[]{singleScaleCosemDataset.getPath()};
				transforms = new AffineTransform3D[]{singleScaleCosemDataset.spatialTransform3d()};
			} else if (metadata instanceof CanonicalSpatialMetadata) {
				final CanonicalSpatialMetadata canonicalDataset = (CanonicalSpatialMetadata)metadata;
				datasetsToOpen = new String[]{canonicalDataset.getPath()};
				transforms = new AffineTransform3D[]{canonicalDataset.getSpatialTransform().spatialTransform3d()};
			} else if (metadata instanceof OmeNgffMetadata) {
				final OmeNgffMetadata multiScaleDataset = (OmeNgffMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5CosemMultiScaleMetadata) {
				final N5CosemMultiScaleMetadata multiScaleDataset = (N5CosemMultiScaleMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof CanonicalMultiscaleMetadata) {
				final CanonicalMultiscaleMetadata multiScaleDataset = (CanonicalMultiscaleMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof SpatialMetadata) {

				datasetsToOpen = new String[]{metadata.getPath()};
				transforms = new AffineTransform3D[]{ ((SpatialMetadata)metadata).spatialTransform3d() };
			} else if (metadata instanceof N5DatasetMetadata) {
				final List<MetadataSource<?>> addTheseSources = MetadataSource
						.buildMetadataSources(n5, (N5DatasetMetadata)metadata);
				if (addTheseSources != null)
					additionalSources.addAll(addTheseSources);
			} else {
				datasetsToOpen = new String[]{metadata.getPath()};
				transforms = new AffineTransform3D[]{new AffineTransform3D()};
			}

			if (datasetsToOpen == null || datasetsToOpen.length == 0)
				continue;

			@SuppressWarnings("rawtypes")
			final RandomAccessibleInterval[] images = new RandomAccessibleInterval[datasetsToOpen.length];
			String unit = "pixel";
			for (int s = 0; s < images.length; ++s) {

				@SuppressWarnings("unchecked")
				final RandomAccessibleInterval<T> img = (RandomAccessibleInterval<T>)loadImage(n5, datasetsToOpen[s]);

				final RandomAccessibleInterval< ? > imagejImg;
				if (metadata instanceof AxisMetadata)
				{
					imagejImg = AxisUtils.permuteForImagePlus(img, (M)metadata);
					unit = unitFromAxes(((AxisMetadata)metadata).getAxes());
				}
				else if( metadata instanceof N5SingleScaleMetadata )
				{
					final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)metadata );
					imagejImg = AxisUtils.permuteForImagePlus( img, axes );
					unit = ((N5SingleScaleMetadata)metadata).unit();
				}
				else if( isN5ViewerMultiscale(metadata))
				{
					final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)(((N5MultiScaleMetadata)metadata).getChildrenMetadata()[0]) );
					imagejImg = AxisUtils.permuteForImagePlus( img, axes );
					unit = unitFromAxes(axes.getAxes());
				}
				else if( isCosemMultiscale(metadata))
				{
					final N5CosemMultiScaleMetadata cosemMulti = ((N5CosemMultiScaleMetadata)metadata);
					final N5CosemMetadata cosemMeta = cosemMulti.getChildrenMetadata()[0];
					imagejImg = permuteForImagePlus(img, transforms[s], cosemMeta);
					unit = cosemMeta.unit();
				}
				else
				{
					final NgffSingleScaleAxesMetadata ngffMeta = isNgffMultiscale(metadata);
					if( ngffMeta != null ) {
						imagejImg = permuteForImagePlus(img, transforms[s], ngffMeta);
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
				images[s] = imagejImg;

				is2D &= imagejImg.dimension(3) == 1;
				numTimepoints = (int)Math.max(numTimepoints, imagejImg.dimension(4));
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

			@SuppressWarnings("unchecked")
			final T type = (T)Util.getTypeFromInterval(images[0]);

			// this could / should be generalized
			final double rx = transforms[0].get(0, 0);
			final double ry = transforms[0].get(1, 1);
			final double rz = transforms[0].get(2, 2);

			/* there still can be many channels */
			@SuppressWarnings("unchecked")
			final List<Pair<Source<T>, Source<V>>> sourcePairs = createSource(
					type,
					srcName,
					images,
					transforms,
					sharedQueue,
					new FinalVoxelDimensions(unit, rx, ry, rz));

			for (final Pair<Source<T>, Source<V>> sourcePair : sourcePairs) {
				addSourceToListsGenericType(sourcePair.getA(), sourcePair.getB(), i + 1, converterSetups, sourcesAndConverters);
			}
		}

		for (final MetadataSource src : additionalSources) {
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
	private static <T extends NumericType<T> & NativeType<T>> RandomAccessibleInterval<?> loadImage(
			final N5Reader n5, final String dataset) {

		final CachedCellImg<?, ?> img = N5Utils.openVolatile(n5, dataset);
		final Object t = img.getType();
		if( t instanceof LabelMultisetType ) {

			final CachedCellImg<LabelMultisetType, ?> lmsImg = (CachedCellImg<LabelMultisetType, ?>)img;
			return convertLabelMultisetCache(lmsImg);

			// TODO compare to the below
//			return (CachedCellImg<T, ?>)convertLabelMultisetLazy(
//					(CachedCellImg<LabelMultisetType, ?>)img);

//			return (RandomAccessibleInterval<T>)convertLabelMultisetVolatile(
//					(CachedCellImg<LabelMultisetType, ?>)img);
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

			final String channelName = nChannels > 1 ? srcName + "_ch" + c : srcName;
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

		final int[] p = AxisUtils.findImagePlusPermutation(meta);
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
