package org.janelia.saalfeldlab.n5.bdv;

import java.util.Arrays;

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

public class TilesAtPoint< T extends NativeType< T > & RealType< T > > extends CustomBehaviourController
{
	private final ViewerPanel viewer;
	private final RandomAccess< IntType > tileRandomAccess;
	private final double[] pixRes;

	public TilesAtPoint(
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
	@Override public CustomBehaviour createBehaviour() { return new TilesAtPointBehaviour(); }

	private class TilesAtPointBehaviour extends CustomBehaviour implements ClickBehaviour
	{
		final RealPoint lastClick = new RealPoint( 3 );
		final long[] position = new long[ 3 ];

		@Override
		public void click( final int x, final int y )
		{
			viewer.displayToGlobalCoordinates( x, y, lastClick );

			for ( int d = 0; d < position.length; ++d )
				position[ d ] = Math.round( lastClick.getDoublePosition( d ) / pixRes[ d ] );

			tileRandomAccess.setPosition( position );
			final int tileIndex = tileRandomAccess.get().get();

			if ( tileIndex != 0 )
				System.out.println( "Tile at " + Arrays.toString( position ) + ": " + ( tileIndex - 1 ) );
			else
				System.out.println( "No tiles at " + Arrays.toString( position ) );
		}
	}
}