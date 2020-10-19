package org.janelia.saalfeldlab.n5.bdv;

import java.util.Arrays;

import net.imglib2.realtransform.AffineTransform3D;

public class MultiscaleDatasets
{
	public final String[] paths;

	public final AffineTransform3D[] transforms;

	public MultiscaleDatasets( String[] paths, AffineTransform3D[] transforms )
	{
		this.paths = paths;
		this.transforms = transforms;
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
