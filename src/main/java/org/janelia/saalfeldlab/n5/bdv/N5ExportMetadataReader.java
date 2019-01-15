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

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.n5.N5Reader;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public class N5ExportMetadataReader
{
	protected static final String nameKey = "name";
	protected static final String scalesKey = "scales";
	protected static final String downsamplingFactorsKey = "downsamplingFactors";
	protected static final String pixelResolutionKey = "pixelResolution";
	protected static final String affineTransformKey = "affineTransform";
	protected static final Pattern scaleLevelPattern = Pattern.compile("^s\\d+$");

	private final N5Reader n5Reader;

	N5ExportMetadataReader( final N5Reader n5Reader )
	{
		this.n5Reader = n5Reader;
	}

	public int getNumChannels() throws IOException
	{
		return n5Reader.list( "/" ).length;
	}

	public String getName() throws IOException
	{
		return getAttribute( nameKey, String.class );
	}

	public double[][] getScales( final int channel ) throws IOException
	{
		// check the root scales attribute
		// if it is not there, try the downsamplingFactors attribute for every dataset under the channel group
		double[][] scales = getAttribute( channel, scalesKey, double[][].class );

		if ( scales == null )
		{
			final int numScales = listScaleLevels( channel ).length;

			scales = new double[ numScales ][];
			for ( int scale = 0; scale < numScales; ++scale )
			{
				double[] downsamplingFactors = n5Reader.getAttribute( N5ExportMetadata.getScaleLevelDatasetPath( channel, scale ), downsamplingFactorsKey, double[].class );
				if ( downsamplingFactors == null )
				{
					if ( scale == 0 )
					{
						downsamplingFactors = new double[ n5Reader.getDatasetAttributes( N5ExportMetadata.getScaleLevelDatasetPath( channel, scale ) ).getNumDimensions() ];
						Arrays.fill( downsamplingFactors, 1 );
					}
					else
					{
						throw new IllegalArgumentException( "downsamplingFactors are not specified for some datasets" );
					}
				}
				scales[ scale ] = downsamplingFactors;
			}
		}

		return scales;
	}

	public VoxelDimensions getPixelResolution( final int channel ) throws IOException
	{
		// check the root pixelResolution attribute
		// if it is not there, try the pixelResolution attribute for every dataset under the channel group
		VoxelDimensions pixelResolution = getAttribute( channel, pixelResolutionKey, FinalVoxelDimensions.class );

		if ( pixelResolution == null )
		{
			final int numScales = listScaleLevels( channel ).length;

			double[] pixelResolutionArr = null;
			for ( int scale = 0; scale < numScales; ++scale )
			{
				final double[] pixelResolutionDatasetArr = n5Reader.getAttribute( N5ExportMetadata.getScaleLevelDatasetPath( channel, scale ), pixelResolutionKey, double[].class );
				if ( pixelResolutionDatasetArr == null )
					continue;

				if ( pixelResolutionArr == null )
				{
					pixelResolutionArr = pixelResolutionDatasetArr;
				}
				else if ( !Arrays.equals( pixelResolutionArr, pixelResolutionDatasetArr ) )
				{
					throw new IllegalArgumentException( "pixelResolution is not the same for scale level datasets" );
				}
			}
			pixelResolution = new FinalVoxelDimensions( "um", pixelResolutionArr );
		}

		return pixelResolution;
	}

	public AffineTransform3D getAffineTransform( final int channel ) throws IOException
	{
		return getAttribute( channel, affineTransformKey, AffineTransform3D.class );
	}

	private < T > T getAttribute( final String key, final Class< T > clazz ) throws IOException
	{
		return n5Reader.getAttribute( "/", key, clazz );
	}

	private < T > T getAttribute( final int channel, final String key, final Class< T > clazz ) throws IOException
	{
		final T overriddenValue = n5Reader.getAttribute( N5ExportMetadata.getChannelGroupPath( channel ), key, clazz );
		return overriddenValue != null ? overriddenValue : getAttribute( key, clazz );
	}

	private String[] listScaleLevels( final int channel ) throws IOException
	{
		return Arrays.stream( n5Reader.list( N5ExportMetadata.getChannelGroupPath( channel ) ) )
			.filter( scaleLevelPattern.asPredicate() )
			.toArray( String[]::new );
	}
}
