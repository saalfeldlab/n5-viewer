package org.janelia.saalfeldlab.n5.bdv;

import java.io.IOException;
import java.util.Arrays;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public class MultiscaleDatasets
{
	private final String[] paths;

	private final AffineTransform3D[] transforms;

	private AffineTransform3D sourceTransform;

	private String unit;

	public MultiscaleDatasets( String[] paths, AffineTransform3D[] transforms, final String unit )
	{
		this.paths = paths;
		this.transforms = transforms;
		this.unit = unit;
	}

	public MultiscaleDatasets( String[] paths, AffineTransform3D[] transforms )
	{
		this( paths, transforms, "pixel" );
	}

	public MultiscaleDatasets( ScaleLevel[] scales )
	{
		int N = scales.length;
		this.paths = new String[ N ];
		this.transforms = new AffineTransform3D[ N ];
		for( int i = 0; i < N; i++ )
		{
			paths[ i ] = scales[ i ].path;
			transforms[ i ] = scales[ i ].transform;
		}
	}

	public void setTransform( final AffineTransform3D transform )
	{
		sourceTransform = transform;
	}

	public String[] getPaths()
	{
		return paths;
	}

	public AffineTransform3D[] getTransforms()
	{
		return transforms;
	}

	public <T extends NumericType<T>> Source<T> openAsSource( final N5Reader n5, final boolean isVolatile, final String name )
	{
		@SuppressWarnings( "rawtypes" )
		final RandomAccessibleInterval[] images = new RandomAccessibleInterval[paths.length];
		final double[][] mipmapScales = new double[ images.length ][ 3 ];
		for ( int s = 0; s < images.length; ++s )
		{
			try
			{
				if( isVolatile )
					images[ s ] = N5Utils.openVolatile( n5, paths[s] );
				else
					images[ s ] = N5Utils.open( n5, paths[s] );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

			mipmapScales[ s ][ 0 ] = transforms[ s ].get( 0, 0 );
			mipmapScales[ s ][ 1 ] = transforms[ s ].get( 1, 1 );
			mipmapScales[ s ][ 2 ] = transforms[ s ].get( 2, 2 );
		}

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleIntervalMipmapSource<T> source = new RandomAccessibleIntervalMipmapSource<T>( 
				images,
				(T)Util.getTypeFromInterval(images[0]),
				mipmapScales,
				new mpicbg.spim.data.sequence.FinalVoxelDimensions( unit, mipmapScales[0]),
				sourceTransform == null ? new AffineTransform3D() : sourceTransform,
				name );

		return source;
	}

	public static MultiscaleDatasets sort( final String[] paths, AffineTransform3D[] transforms )
	{
		assert( paths.length == transforms.length );

		int N = paths.length;
		ScaleLevel[] scales = new ScaleLevel[ N ];
		for( int i = 0; i < N; i++ )
		{
			scales[ i ] = new ScaleLevel( paths[ i ], transforms[ i ] );
		}
		Arrays.sort( scales );
		return new MultiscaleDatasets( scales );
	}

	public static class ScaleLevel implements Comparable< ScaleLevel >
	{
		public final String path;
		public final AffineTransform3D transform;
		public final double averageScale;

		public ScaleLevel( final String path, final AffineTransform3D transform )
		{
			this.path = path;
			this.transform = transform;
			averageScale = (transform.get( 0, 0 ) + 
							transform.get( 1, 1 ) + 
							transform.get( 2, 2 )) / 3;
		}

		public String toString()
		{
			return path + " (" + averageScale + ")";
		}

		@Override
		public int compareTo( ScaleLevel o )
		{
			if( this.averageScale == o.averageScale )
				return 0;
			else if ( this.averageScale < o.averageScale )
				return -1;
			else
				return 1;
		}
	};

}
