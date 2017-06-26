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
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;

public class N5ExportMetadata
{
	private static final String numChannelsKey = "numChannels";
	private static final String scalesKey = "scales";
	private static final String pixelResolutionKey = "pixelResolution";

	private final ChannelMetadata[] channelsMetadata;
	private final String name;
	private final double[][] scales;
	private final VoxelDimensions pixelResolution;

	public N5ExportMetadata(
			final int numChannels,
			final String name,
			final double[][] scales )
	{
		this( numChannels, name, scales, null );
	}

	public N5ExportMetadata(
			final int numChannels,
			final String name,
			final double[][] scales,
			final VoxelDimensions pixelResolution )
	{
		this( new ChannelMetadata[ numChannels ], name, scales, null );
	}

	public N5ExportMetadata(
			final ChannelMetadata[] channelsMetadata,
			final String name,
			final double[][] scales )
	{
		this( channelsMetadata, name, scales, null );
	}

	public N5ExportMetadata(
			final ChannelMetadata[] channelsMetadata,
			final String name,
			final double[][] scales,
			final VoxelDimensions pixelResolution )
	{
		this.channelsMetadata = channelsMetadata;
		this.name = name;
		this.scales = scales;
		this.pixelResolution = pixelResolution;
	}

	public String getName() { return name; }
	public int getNumChannels() { return channelsMetadata.length; }
	public ChannelMetadata getChannelMetadata( final int channel ) { return channelsMetadata[ channel ]; }
	public double[][] getScales() { return scales; }
	public VoxelDimensions getPixelResolution() { return pixelResolution; }

	public static String getScaleLevelDatasetPath( final int channel, final int scale )
	{
		return String.format( "%s/s%d", ChannelMetadata.getChannelGroupPath( channel ), scale );
	}

	public static void write( final String basePath, final N5ExportMetadata metadata ) throws IOException
	{
		final Map< String, Object > attributes = new HashMap<>();
		attributes.put( numChannelsKey, metadata.getNumChannels() );
		attributes.put( scalesKey, metadata.getScales() );
		attributes.put( pixelResolutionKey, metadata.getPixelResolution() );
		final N5Writer n5 = N5.openFSWriter( basePath );
		n5.setAttributes( "", attributes );

		for ( int channel = 0; channel < metadata.getNumChannels(); ++channel )
			ChannelMetadata.write( basePath, channel, metadata.getChannelMetadata( channel ) );
	}

	public static N5ExportMetadata read( final String basePath ) throws IOException
	{
		final N5Reader n5 = N5.openFSReader( basePath );

		final int numChannels = n5.getAttribute( "", numChannelsKey, Integer.class );
		final ChannelMetadata[] channelsMetadata = new ChannelMetadata[ numChannels ];
		for ( int channel = 0; channel < numChannels; ++channel )
			channelsMetadata[ channel ] = ChannelMetadata.read( basePath, channel );

		return new N5ExportMetadata(
				channelsMetadata,
				Paths.get( basePath ).getFileName().toString(),
				n5.getAttribute( "", scalesKey, double[][].class ),
				n5.getAttribute( "", pixelResolutionKey, FinalVoxelDimensions.class ) );
	}

	public static class ChannelMetadata
	{
		private static final String displayRangeMinKey = "displayRangeMin";
		private static final String displayRangeMaxKey = "displayRangeMax";

		private final Double displayRangeMin;
		private final Double displayRangeMax;

		public ChannelMetadata()
		{
			this( null, null );
		}

		private ChannelMetadata( final Double displayRangeMin, final Double displayRangeMax )
		{
			this.displayRangeMin = displayRangeMin;
			this.displayRangeMax = displayRangeMax;
		}

		public Double getDisplayRangeMin() { return displayRangeMin; }
		public Double getDisplayRangeMax() { return displayRangeMax; }

		public static String getChannelGroupPath( final int channel )
		{
			return String.format( "c%d", channel );
		}

		public static void write( final String basePath, final int channel, final ChannelMetadata metadata ) throws IOException
		{
			final Map< String, Object > attributes = new HashMap<>();
			attributes.put( displayRangeMinKey, metadata.getDisplayRangeMin() );
			attributes.put( displayRangeMaxKey, metadata.getDisplayRangeMax() );
			final N5Writer n5 = N5.openFSWriter( basePath );
			final String channelGroupPath = getChannelGroupPath( channel );
			n5.setAttributes( channelGroupPath, attributes );
		}

		public static ChannelMetadata read( final String basePath, final int channel ) throws IOException
		{
			final N5Reader n5 = N5.openFSReader( basePath );
			final String channelGroupPath = getChannelGroupPath( channel );
			return new ChannelMetadata(
					n5.getAttribute( channelGroupPath, displayRangeMinKey, Double.class ),
					n5.getAttribute( channelGroupPath, displayRangeMaxKey, Double.class ) );
		}
	}
}
