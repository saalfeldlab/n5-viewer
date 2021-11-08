/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.n5.bdv;

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.export.ProgressWriterConsole;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.*;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultiscaleMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalSpatialMetadata;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.ui.DatasetSelectorDialog;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * {@link BigDataViewer}-based application for viewing N5 datasets.
 *
 * @author Igor Pisarev
 * @author John Bogovic
 */
public class N5Viewer implements PlugIn
{
	private static String lastOpenedContainer = "";

	public static final N5MetadataParser<?>[] n5vGroupParsers = new N5MetadataParser[]{ 
			new N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
			new N5ViewerMultiscaleMetadataParser(),
			new N5ViewerMultichannelMetadata.N5ViewerMultichannelMetadataParser() 
			};

	public static final N5MetadataParser<?>[] n5vParsers = new N5MetadataParser[] {
		new N5CosemMetadataParser(),
		new N5SingleScaleMetadataParser(),
	};

	final public static void main( final String... args )
	{
		new ImageJ();
		new N5Viewer().run( "" );
	}
	
	@Override
	public void run( final String args )
	{
		final DatasetSelectorDialog dialog = new DatasetSelectorDialog( 
				new N5Importer.N5ViewerReaderFun(),
				x -> "",
				lastOpenedContainer,
				n5vGroupParsers,
				n5vParsers);

//		dialog.setTreeRenderer( new N5DatasetTreeCellRenderer( true ) );
//		dialog.setRecursiveFilterCallback( new N5ViewerDatasetFilter() );

		dialog.setContainerPathUpdateCallback( x -> lastOpenedContainer = x );
		dialog.setTreeRenderer( new N5ViewerTreeCellRenderer() );

		dialog.run( selection -> {
			try
			{
				exec( selection );
			}
			catch ( final IOException e )
			{
				IJ.handleException( e );
			}
		} );
	}

	public static < T extends NumericType< T > & NativeType< T >,
					V extends Volatile< T > & NumericType< V >,
					R extends N5Reader > 
				void exec( final DataSelection selection ) throws IOException
	{
		final int numTimepoints = 1;
		Prefs.showScaleBar( true );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sourcesAndConverters = new ArrayList<>();

		final List<N5Metadata> selected = new ArrayList<>();
		for( N5Metadata meta : selection.metadata )
		{
			if( meta instanceof N5ViewerMultichannelMetadata )
			{
				N5ViewerMultichannelMetadata mc = (N5ViewerMultichannelMetadata)meta;
				for( MultiscaleMetadata<?> m : mc.getChildrenMetadata() )
					selected.add( m );
			}
			else if ( meta instanceof CanonicalMultichannelMetadata )
			{
				CanonicalMultichannelMetadata mc = (CanonicalMultichannelMetadata)meta;
				for( N5Metadata m : mc.getChildrenMetadata() )
					selected.add( m );
			}
			else
				selected.add( meta );
		}

		final List<N5Source<T>> sources = new ArrayList<>();
		final List<N5VolatileSource<T, V>> volatileSources = new ArrayList<>();

		for ( int i = 0; i < selected.size(); ++i )
		{
			final String[] datasetsToOpen;
			final AffineTransform3D[] transforms;

			final N5Metadata metadata = selected.get( i );
			if (metadata instanceof N5SingleScaleMetadata) {
				final N5SingleScaleMetadata singleScaleDataset = (N5SingleScaleMetadata) metadata;
				String[] tmpDatasets= new String[]{ singleScaleDataset.getPath() };
				AffineTransform3D[] tmpTransforms = new AffineTransform3D[]{ singleScaleDataset.spatialTransform3d() };

				MultiscaleDatasets msd = MultiscaleDatasets.sort( tmpDatasets, tmpTransforms );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5MultiScaleMetadata) {
				final N5MultiScaleMetadata multiScaleDataset = (N5MultiScaleMetadata) metadata;
				datasetsToOpen = multiScaleDataset.getPaths();
				transforms = multiScaleDataset.spatialTransforms3d();
			} else if (metadata instanceof N5CosemMetadata ) {
				final N5CosemMetadata singleScaleCosemDataset = (N5CosemMetadata) metadata;
				datasetsToOpen = new String[]{ singleScaleCosemDataset.getPath() };
				transforms = new AffineTransform3D[]{ singleScaleCosemDataset.spatialTransform3d() };
			} else if (metadata instanceof CanonicalSpatialMetadata ) {
				final CanonicalSpatialMetadata canonicalDataset = (CanonicalSpatialMetadata) metadata;
				datasetsToOpen = new String[]{ canonicalDataset.getPath() };
				transforms = new AffineTransform3D[]{ canonicalDataset.getSpatialTransform().spatialTransform3d() };
			} else if (metadata instanceof N5CosemMultiScaleMetadata ) {
				final N5CosemMultiScaleMetadata multiScaleDataset = (N5CosemMultiScaleMetadata) metadata;

				MultiscaleDatasets msd = MultiscaleDatasets.sort( multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d() );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof CanonicalMultiscaleMetadata ) {
				final CanonicalMultiscaleMetadata multiScaleDataset = (CanonicalMultiscaleMetadata) metadata;

				MultiscaleDatasets msd = MultiscaleDatasets.sort( multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d() );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata != null) {
				datasetsToOpen = new String[]{ metadata.getPath() };
				transforms = new AffineTransform3D[] { new AffineTransform3D() };
			} else if (metadata == null) {
				IJ.error("N5 Viewer", "Cannot open dataset where metadata is null");
				return;
			} else{
				IJ.error("N5 Viewer", "Unknown metadata type: " + metadata); return;
			}

			System.out.println( "opening with " + datasetsToOpen.length + " levels");
			System.out.println( "  scales:" );
			for ( int s = 0; s < datasetsToOpen.length; ++s )
				System.out.println( "  " + transforms[s] );

			@SuppressWarnings( "rawtypes" )
			final RandomAccessibleInterval[] images = new RandomAccessibleInterval[datasetsToOpen.length];
			for ( int s = 0; s < images.length; ++s )
				images[ s ] = N5Utils.openVolatile( selection.n5, datasetsToOpen[s] );

			@SuppressWarnings( "unchecked" )
			final N5Source<T> source = new N5Source<>(
					(T) Util.getTypeFromInterval(images[0]),
					"source " + (i + 1),
					images,
					transforms);

			final N5VolatileSource<T, V> volatileSource = source.asVolatile(sharedQueue);

			sources.add(source);
			volatileSources.add(volatileSource);

			addSourceToListsGenericType( volatileSource, i + 1, numTimepoints, volatileSource.getType(), converterSetups, sourcesAndConverters );
		}

		final BigDataViewer bdv = new BigDataViewer(
				converterSetups,
				sourcesAndConverters,
				null,
				numTimepoints,
				new CacheControl.CacheControls(),
				"N5 Viewer",
				new ProgressWriterConsole(),
				BdvOptions.options().values.getViewerOptions()
			);

		InitializeViewerState.initTransform( bdv.getViewer() );

		bdv.getViewer().setDisplayMode( DisplayMode.FUSED );
		bdv.getViewerFrame().setVisible( true );

		initCropController( bdv, sources );
	}

	private static < T extends NumericType< T > & NativeType< T > > void initCropController( final BigDataViewer bdv, final List< ? extends Source< T > > sources )
	{
		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();
		final InputTriggerConfig config = new InputTriggerConfig();

		final CropController< T > cropController = new CropController<>(
					bdv.getViewer(),
					sources,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config );

		bindings.addBehaviourMap( "crop", cropController.getBehaviourMap() );
		bindings.addInputTriggerMap( "crop", cropController.getInputTriggerMap() );
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
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param numTimepoints
	 *            the number of timepoints of the source.
	 * @param type
	 *            instance of the {@code img} type.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static < T > void addSourceToListsGenericType(
			final Source< T > source,
			final int setupId,
			final int numTimepoints,
			final T type,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ? > > sources )
	{
		if ( type instanceof RealType )
			addSourceToListsRealType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else if ( type instanceof ARGBType )
			addSourceToListsARGBType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else if ( type instanceof VolatileARGBType )
			addSourceToListsVolatileARGBType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else
			throw new IllegalArgumentException( "Unknown source type. Expected RealType, ARGBType, or VolatileARGBType" );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link RealARGBColorConverter} and into
	 * a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static < T extends RealType< T > > void addSourceToListsRealType(
			final Source< T > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final T type = Util.getTypeFromInterval( source.getSource( 0, 0 ) );
		final double typeMin = Math.max( 0, Math.min( type.getMinValue(), 65535 ) );
		final double typeMax = Math.max( 0, Math.min( type.getMaxValue(), 65535 ) );
		final RealARGBColorConverter< T > converter = RealARGBColorConverter.create( source.getType(), typeMin, typeMax );
		converter.setColor( new ARGBType( 0xffffffff ) );

		final TransformedSource< T > ts = new TransformedSource<>( source );
		final SourceAndConverter< T > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link ScaledARGBConverter.ARGB} and
	 * into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static void addSourceToListsARGBType(
			final Source< ARGBType > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ARGBType > > sources )
	{
		final TransformedSource< ARGBType > ts = new TransformedSource<>( source );
		final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
		final SourceAndConverter< ARGBType > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link ScaledARGBConverter.ARGB} and
	 * into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static void addSourceToListsVolatileARGBType(
			final Source< VolatileARGBType > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< VolatileARGBType > > sources )
	{
		final TransformedSource< VolatileARGBType > ts = new TransformedSource<>( source );
		final ScaledARGBConverter.VolatileARGB converter = new ScaledARGBConverter.VolatileARGB( 0, 255 );
		final SourceAndConverter< VolatileARGBType > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}
}
