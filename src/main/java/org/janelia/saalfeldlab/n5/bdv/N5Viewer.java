package org.janelia.saalfeldlab.n5.bdv;

import java.io.IOException;
import java.util.Random;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadata.ChannelMetadata;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.BigDataViewer;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.VolatileRandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
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
 * {@link BigDataViewer}-based application for exploring N5 datasets that conform to the {@link N5ExportMetadata} format (multichannel, multiscale).
 * Takes a base path to an N5 dataset as a command line argument or via Fiji's Plugins &gt; BigDataViewer &gt; N5 Viewer.
 *
 * @author Igor Pisarev
 */

public class N5Viewer implements PlugIn
{
	protected static String n5Path = "";

	final public static void main( final String... args ) throws IOException
	{
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
		final N5ExportMetadata metadata = N5ExportMetadata.read( n5Path );
		final ARGBType[] colors = getColors( metadata.getNumChannels() );
		for ( int c = 0; c < metadata.getNumChannels(); ++c )
		{
			final RandomAccessibleInterval< T >[] scaleLevelImgs = new RandomAccessibleInterval[ metadata.getScales().length ];
			for ( int s = 0; s < metadata.getScales().length; ++s )
				scaleLevelImgs[ s ] = N5Utils.openVolatile( n5, N5ExportMetadata.getScaleLevelDatasetPath( c, s ) );

			final RandomAccessibleIntervalMipmapSource< T > source = new RandomAccessibleIntervalMipmapSource<>(
					scaleLevelImgs,
					Util.getTypeFromInterval( scaleLevelImgs[ 0 ] ),
					metadata.getScales(),
					metadata.getPixelResolution(),
					metadata.getName() );

			final VolatileRandomAccessibleIntervalMipmapSource< T, V > volatileSource = source.asVolatile( ( V ) VolatileTypeMatcher.getVolatileTypeForType( source.getType() ), sharedQueue );

			final TransformedSource< V > transformedVolatileSource = new TransformedSource<>( volatileSource );
			final AffineTransform3D voxelSizeTransform = new AffineTransform3D();
			final double[] normalizedVoxelSize = getNormalizedVoxelSize( source.getVoxelDimensions() );
			for ( int d = 0; d < voxelSizeTransform.numDimensions(); ++d )
				voxelSizeTransform.set( normalizedVoxelSize[ d ], d, d );
			transformedVolatileSource.setFixedTransform( voxelSizeTransform );

			final BdvStackSource< V > stackSource = BdvFunctions.show( transformedVolatileSource, bdvOptions );
			stackSource.setColor( colors[ c ] );

			final ChannelMetadata channelMetadata = metadata.getChannelMetadata( c );
			stackSource.setDisplayRange( channelMetadata.getDisplayRangeMin(), channelMetadata.getDisplayRangeMax() );

			bdvOptions.addTo( stackSource.getBdvHandle() );
		}
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
