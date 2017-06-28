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
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public class N5ExportMetadata
{
	private static final String nameKey = "name";
	private static final String numChannelsKey = "numChannels";
	private static final String scalesKey = "scales";
	private static final String pixelResolutionKey = "pixelResolution";
	private static final String affineTransformKey = "affineTransform";

	private final ChannelMetadata[] channelsMetadata;
	private final String name;
	private final double[][] scales;
	private final VoxelDimensions pixelResolution;
	private final AffineTransform3D affineTransform;

	public N5ExportMetadata(
			final String name,
			final int numChannels,
			final double[][] scales )
	{
		this( name, numChannels, scales, null, null );
	}

	public N5ExportMetadata(
			final String name,
			final int numChannels,
			final double[][] scales,
			final VoxelDimensions pixelResolution )
	{
		this( name, new ChannelMetadata[ numChannels ], scales, pixelResolution, null );
	}

	public N5ExportMetadata(
			final String name,
			final int numChannels,
			final double[][] scales,
			final AffineTransform3D affineTransform )
	{
		this( name, new ChannelMetadata[ numChannels ], scales, null, affineTransform );
	}

	public N5ExportMetadata(
			final String name,
			final int numChannels,
			final double[][] scales,
			final VoxelDimensions pixelResolution,
			final AffineTransform3D affineTransform )
	{
		this( name, new ChannelMetadata[ numChannels ], scales, pixelResolution, affineTransform );
	}

	public N5ExportMetadata(
			final String name,
			final ChannelMetadata[] channelsMetadata,
			final double[][] scales )
	{
		this( name, channelsMetadata, scales, null, null );
	}

	public N5ExportMetadata(
			final String name,
			final ChannelMetadata[] channelsMetadata,
			final double[][] scales,
			final VoxelDimensions pixelResolution )
	{
		this( name, channelsMetadata, scales, pixelResolution, null );
	}

	public N5ExportMetadata(
			final String name,
			final ChannelMetadata[] channelsMetadata,
			final double[][] scales,
			final AffineTransform3D affineTransform )
	{
		this( name, channelsMetadata, scales, null, affineTransform );
	}

	public N5ExportMetadata(
			final String name,
			final ChannelMetadata[] channelsMetadata,
			final double[][] scales,
			final VoxelDimensions pixelResolution,
			final AffineTransform3D affineTransform )
	{
		this.name = name;
		this.channelsMetadata = channelsMetadata;
		this.scales = scales;
		this.pixelResolution = pixelResolution;
		this.affineTransform = affineTransform;
	}

	public String getName() { return name; }
	public int getNumChannels() { return channelsMetadata.length; }
	public ChannelMetadata getChannelMetadata( final int channel ) { return channelsMetadata[ channel ]; }
	public double[][] getScales() { return scales; }
	public VoxelDimensions getPixelResolution() { return pixelResolution; }
	public AffineTransform3D getAffineTransform() { return affineTransform; }

	public static String getScaleLevelDatasetPath( final int channel, final int scale )
	{
		return String.format( "%s/s%d", ChannelMetadata.getChannelGroupPath( channel ), scale );
	}

	public static void write( final String basePath, final N5ExportMetadata metadata ) throws IOException
	{
		final Map< String, Object > attributes = new HashMap<>();
		attributes.put( nameKey, metadata.getName() );
		attributes.put( numChannelsKey, metadata.getNumChannels() );
		attributes.put( scalesKey, metadata.getScales() );
		attributes.put( pixelResolutionKey, metadata.getPixelResolution() );
		attributes.put( affineTransformKey, metadata.getAffineTransform() );

		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
		final N5Writer n5 = N5.openFSWriter( basePath, gsonBuilder );
		n5.setAttributes( "", attributes );

		for ( int channel = 0; channel < metadata.getNumChannels(); ++channel )
			ChannelMetadata.write( basePath, channel, metadata.getChannelMetadata( channel ) );
	}

	public static N5ExportMetadata read( final String basePath ) throws IOException
	{
		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
		final N5Reader n5 = N5.openFSReader( basePath, gsonBuilder );

		final int numChannels = n5.getAttribute( "", numChannelsKey, Integer.class );
		final ChannelMetadata[] channelsMetadata = new ChannelMetadata[ numChannels ];
		for ( int channel = 0; channel < numChannels; ++channel )
			channelsMetadata[ channel ] = ChannelMetadata.read( basePath, channel );

		return new N5ExportMetadata(
				n5.getAttribute( "", nameKey, String.class ),
				channelsMetadata,
				n5.getAttribute( "", scalesKey, double[][].class ),
				n5.getAttribute( "", pixelResolutionKey, FinalVoxelDimensions.class ),
				n5.getAttribute( "", affineTransformKey, AffineTransform3D.class ) );
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

		public ChannelMetadata( final double displayRangeMin, final double displayRangeMax )
		{
			this( new Double( displayRangeMin ), new Double( displayRangeMax ) );
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

	private static class AffineTransform3DJsonAdapter implements JsonSerializer< AffineTransform3D >, JsonDeserializer< AffineTransform3D >
	{
		@Override
		public JsonElement serialize( final AffineTransform3D src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final JsonArray jsonMatrixArray = new JsonArray();
			for ( int row = 0; row < src.numDimensions(); ++row )
			{
				final JsonArray jsonRowArray = new JsonArray();
				for ( int col = 0; col < src.numDimensions() + 1; ++col )
					jsonRowArray.add( src.get( row, col ) );
				jsonMatrixArray.add( jsonRowArray );
			}
			return jsonMatrixArray;
		}

		@Override
		public AffineTransform3D deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
		{
			final AffineTransform3D affineTransform = new AffineTransform3D();
			final JsonArray jsonMatrixArray = json.getAsJsonArray();
			for ( int row = 0; row < jsonMatrixArray.size(); ++row )
			{
				final JsonArray jsonRowArray = jsonMatrixArray.get( row ).getAsJsonArray();
				for ( int col = 0; col < jsonRowArray.size(); ++col )
					affineTransform.set( jsonRowArray.get( col ).getAsDouble(), row, col );
			}
			return affineTransform;
		}
	}
}
