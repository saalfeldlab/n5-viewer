package org.janelia.saalfeldlab.n5.bdv;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.viewer.ViewerPanel;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;

public class TileCounter< T extends NativeType< T > & RealType< T > > extends CustomBehaviourController
{
	private final ViewerPanel viewer;
	private final RandomAccess< IntType > tileRandomAccess;
	private final double[] pixRes;

	public TileCounter(
			final InputTriggerConfig config,
			final ViewerPanel viewer,
			final RandomAccessibleInterval< IntType > tileCountImg,
			final VoxelDimensions voxelDimensions )
	{
		super( config );
		this.viewer = viewer;
		this.tileRandomAccess = Views.extendZero( tileCountImg ).randomAccess();
		this.pixRes = N5MultiscaleSource.getNormalizedVoxelSize( voxelDimensions );
	}

	@Override public String getName() { return "tile count"; }
	@Override public String getTriggers() { return "SPACE button1"; }
	@Override public CustomBehaviour createBehaviour() { return new TilesCounterBehaviour(); }

	private class TilesCounterBehaviour extends CustomBehaviour implements ClickBehaviour
	{
		private final RealPoint point = new RealPoint( 3 );

		@Override
		public void click( final int x, final int y )
		{
			count();
		}

		public void count()
		{
			final int width = viewer.getWidth();
			final int height = viewer.getHeight();

			final long[] position = new long[ 3 ];

			final Set< Integer > tileSet = new TreeSet<>();

			for ( int x = 0; x <= width; ++x )
			{
				System.out.println( "  x=" + x + "..." );
				for ( int y = 0; y <= height; ++y )
				{
					viewer.displayToGlobalCoordinates( x, y, point );
					for ( int d = 0; d < 3; ++d )
						position[ d ] = Math.round( point.getDoublePosition( d ) / pixRes[ d ] );

					tileRandomAccess.setPosition( position );
					final int tileIndex = tileRandomAccess.get().get();
					if ( tileIndex != 0 )
						tileSet.add( tileIndex );
				}
			}

			System.out.println();
			System.out.println( "Tiles in the view of size " + Arrays.toString( new int[] { width, height } ) + ": " + tileSet.size() );
		}
	}
}