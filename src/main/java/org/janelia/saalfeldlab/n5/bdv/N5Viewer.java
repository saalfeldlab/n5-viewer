package org.janelia.saalfeldlab.n5.bdv;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadata.DisplayRange;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.VolatileRandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.viewer.Source;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

/**
 * <p>
 * {@link BigDataViewer}-based application for exploring {@link N5} datasets that conform to the {@link N5ExportMetadata} format (multichannel, multiscale).
 * Takes a root path to an N5 container as a command line argument or via Fiji's Plugins &gt; BigDataViewer &gt; N5 Viewer.
 * </p><p>
 * Expects that a given N5 container conforms to a special format:
 * </p><p>
 * /attributes.json<br>
 * <br>
 * /c0/attributes.json<br>
 * /c0/s0/<br>
 * /c0/s1/<br>
 * /c0/s2/<br>
 * /c0/...<br>
 * <br>
 * /c1/attributes.json<br>
 * /c1/s0/<br>
 * /c1/s1/<br>
 * /c1/s2/<br>
 * /c1/...<br>
 * <br>
 * ...<br>
 * </p><p>
 * Root attributes are used for all channels. They can be overridden by setting channel-specific attributes.
 * </p><p>
 * You can use {@link N5ExportMetadata} to simplify exporting the datasets to the specified format.<br>
 * <br>
 * Example of the attributes.json file:<br>
 * <pre>{
 *   "name":"some data",
 *   "scales":[[1,1,1],[2,2,1],[4,4,2],[8,8,4],[16,16,9],[32,32,17]],
 *   "pixelResolution":{"unit":"um","dimensions":[0.097,0.097,0.18]},
 *   "affineTransform":[[1,-0.30,-0.25,0],[0,1.25,0,0],[0,0,0.85,0]],
 *   "displayRange":{"min":500,"max":3000}
 *}</pre>
 *
 * @author Igor Pisarev
 */

public class N5Viewer implements PlugIn
{
	protected static String n5Path = "";

	final public static void main( final String... args ) throws IOException
	{
		new ImageJ();
		exec( args[ 0 ] );
	}

	@Override
	public void run( final String args )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
		gd.addDirectoryField( "N5_export_path: ", n5Path );
		gd.showDialog();
		if ( gd.wasCanceled() )
			return;

		n5Path = gd.getNextString();

		try
		{
			exec( n5Path );
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
		}
	}

	@SuppressWarnings("unchecked")
	final public static < T extends NumericType< T > & NativeType< T >, V extends Volatile< T > & NumericType< V > > void exec( final String n5Path ) throws IOException
	{
		final BdvOptions bdvOptions = BdvOptions.options();
		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final N5Reader n5 = N5.openFSReader( n5Path );
		final N5ExportMetadata metadata = new N5ExportMetadata( n5Path );

		final ARGBType[] colors = getColors( metadata.getNumChannels() );
		final String displayName = metadata.getName() != null ? metadata.getName() : Paths.get( n5Path ).getFileName().toString();

		final List< Source< T > > sources = new ArrayList<>();

		for ( int c = 0; c < metadata.getNumChannels(); ++c )
		{
			final double[][] scales = metadata.getScales( c );
			final RandomAccessibleInterval< T >[] scaleLevelImgs = new RandomAccessibleInterval[ scales.length ];
			for ( int s = 0; s < scales.length; ++s )
				scaleLevelImgs[ s ] = N5Utils.openVolatile( n5, N5ExportMetadata.getScaleLevelDatasetPath( c, s ) );

			final RandomAccessibleIntervalMipmapSource< T > source = new RandomAccessibleIntervalMipmapSource<>(
					scaleLevelImgs,
					Util.getTypeFromInterval( scaleLevelImgs[ 0 ] ),
					scales,
					metadata.getPixelResolution( c ),
					displayName );

			final VolatileRandomAccessibleIntervalMipmapSource< T, V > volatileSource = source.asVolatile( ( V ) VolatileTypeMatcher.getVolatileTypeForType( source.getType() ), sharedQueue );

			// account for the voxel size
			final TransformedSource< T > transformedSource = new TransformedSource<>( source );
			final TransformedSource< V > transformedVolatileSource = new TransformedSource<>( volatileSource );
			if ( source.getVoxelDimensions() != null )
			{
				final AffineTransform3D voxelSizeTransform = new AffineTransform3D();
				final double[] normalizedVoxelSize = getNormalizedVoxelSize( source.getVoxelDimensions() );
				for ( int d = 0; d < voxelSizeTransform.numDimensions(); ++d )
					voxelSizeTransform.set( normalizedVoxelSize[ d ], d, d );

				transformedSource.setFixedTransform( voxelSizeTransform );
				transformedVolatileSource.setFixedTransform( voxelSizeTransform );

				final AffineTransform3D metadataTransform = metadata.getAffineTransform( c );
				if ( metadataTransform != null )
				{
					transformedSource.setIncrementalTransform( metadataTransform );
					transformedVolatileSource.setIncrementalTransform( metadataTransform );
				}
			}

			// display in BDV
			final BdvStackSource< V > stackSource = BdvFunctions.show( transformedVolatileSource, bdvOptions );

			// set metadata
			stackSource.setColor( colors[ c ] );

			final DisplayRange displayRange = metadata.getDisplayRange( c );
			if ( displayRange != null )
				stackSource.setDisplayRange( displayRange.min, displayRange.max );

			sources.add( transformedSource );

			// reuse BDV handle
			bdvOptions.addTo( stackSource.getBdvHandle() );
		}

		final BdvHandle bdvHandle = bdvOptions.values.addTo().getBdvHandle();
		final TriggerBehaviourBindings bindings = bdvHandle.getTriggerbindings();
		final InputTriggerConfig config = new InputTriggerConfig();

		final CropController< T > cropController = new CropController<>(
					bdvHandle.getViewerPanel(),
					sources,
					config,
					bdvHandle.getKeybindings(),
					config );

		bindings.addBehaviourMap( "crop", cropController.getBehaviourMap() );
		bindings.addInputTriggerMap( "crop", cropController.getInputTriggerMap() );
	}

	private static ARGBType[] getColors( final int numChannels )
	{
		assert numChannels >= 0;
		if ( numChannels <= 0 )
			return new ARGBType[ 0 ];
		else if ( numChannels == 1 )
			return new ARGBType[] { new ARGBType( 0xffffffff ) };

		final int[] predefinedColors = new int[] {
				ARGBType.rgba( 0xff, 0, 0xff, 0xff ),
				ARGBType.rgba( 0, 0xff, 0, 0xff ),
				ARGBType.rgba( 0, 0, 0xff, 0xff ),
				ARGBType.rgba( 0xff, 0, 0, 0xff ),
				ARGBType.rgba( 0xff, 0xff, 0, 0xff ),
				ARGBType.rgba( 0, 0xff, 0xff, 0xff ),
		};

		final ARGBType[] colors = new ARGBType[ numChannels ];
		Random rnd = null;

		for ( int c = 0; c < numChannels; ++c )
		{
			if ( c < predefinedColors.length )
			{
				colors[ c ] = new ARGBType( predefinedColors[ c ] );
			}
			else
			{
				if ( rnd == null )
					rnd = new Random();

				colors[ c ] = new ARGBType( ARGBType.rgba( rnd.nextInt( 1 << 7) << 1, rnd.nextInt( 1 << 7) << 1, rnd.nextInt( 1 << 7) << 1, 0xff ) );
			}
		}

		return colors;
	}

	private static double[] getNormalizedVoxelSize( final VoxelDimensions voxelDimensions )
	{
		double minVoxelDim = Double.POSITIVE_INFINITY;
		for ( int d = 0; d < voxelDimensions.numDimensions(); ++d )
			minVoxelDim = Math.min( minVoxelDim, voxelDimensions.dimension( d ) );
		final double[] normalizedVoxelSize = new double[ voxelDimensions.numDimensions() ];
		for ( int d = 0; d < voxelDimensions.numDimensions(); ++d )
			normalizedVoxelSize[ d ] = voxelDimensions.dimension( d ) / minVoxelDim;
		return normalizedVoxelSize;
	}
}
