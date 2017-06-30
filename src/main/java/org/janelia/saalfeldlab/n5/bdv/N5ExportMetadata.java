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

import org.janelia.saalfeldlab.n5.N5;
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

/**
 * <p>
 * Defines the format for multichannel multiscale datasets stored in an N5 container:
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
 * Root attributes are used as defaults for all channels. They can be overridden by setting channel-specific attributes.
 * </p><p>
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

public class N5ExportMetadata
{
	public static class DisplayRange
	{
		public final double min, max;
		public DisplayRange( final double min, final double max )
		{
			this.min = min;
			this.max = max;
		}
	}

	public static String getChannelGroupPath( final int channel )
	{
		return String.format( "c%d", channel );
	}

	public static String getScaleLevelDatasetPath( final int channel, final int scale )
	{
		return String.format( "%s/s%d", getChannelGroupPath( channel ), scale );
	}

	private static final String nameKey = "name";
	private static final String scalesKey = "scales";
	private static final String pixelResolutionKey = "pixelResolution";
	private static final String affineTransformKey = "affineTransform";
	private static final String displayRangeKey = "displayRange";

	private final N5Writer n5;

	public N5ExportMetadata( final String basePath )
	{
		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
		n5 = N5.openFSWriter( basePath, gsonBuilder );
	}

	public int getNumChannels() throws IOException
	{
		return n5.list( "" ).length;
	}

	public void setName( final String name ) throws IOException { setAttribute( nameKey, name ); }
	public String getName() throws IOException { return getAttribute( nameKey, String.class ); }

	public void setDefaultScales( final double[][] scales ) throws IOException { setAttribute( scalesKey, scales ); }
	public void setDefaultPixelResolution( final VoxelDimensions pixelResolution ) throws IOException { setAttribute( pixelResolutionKey, pixelResolution ); }
	public void setDefaultAffineTransform( final AffineTransform3D affineTransform ) throws IOException { setAttribute( affineTransformKey, affineTransform ); }
	public void setDefaultDisplayRange( final DisplayRange displayRange ) throws IOException { setAttribute( displayRangeKey, displayRange ); }

	public void setScales( final int channel, final double[][] scales ) throws IOException { setAttribute( channel, scalesKey, scales ); }
	public void setPixelResolution( final int channel, final VoxelDimensions pixelResolution ) throws IOException { setAttribute( channel, pixelResolutionKey, pixelResolution ); }
	public void setAffineTransform( final int channel, final AffineTransform3D affineTransform ) throws IOException { setAttribute( channel, affineTransformKey, affineTransform ); }
	public void setDisplayRange( final int channel, final DisplayRange displayRange ) throws IOException { setAttribute( channel, displayRangeKey, displayRange ); }

	public double[][] getScales( final int channel ) throws IOException { return getAttribute( channel, scalesKey, double[][].class ); }
	public VoxelDimensions getPixelResolution( final int channel ) throws IOException { return getAttribute( channel, pixelResolutionKey, FinalVoxelDimensions.class ); }
	public AffineTransform3D getAffineTransform( final int channel ) throws IOException { return getAttribute( channel, affineTransformKey, AffineTransform3D.class ); }
	public DisplayRange getDisplayRange( final int channel ) throws IOException { return getAttribute( channel, displayRangeKey, DisplayRange.class ); }

	private < T > T getAttribute( final String key, final Class< T > clazz ) throws IOException
	{
		return n5.getAttribute( "", key, clazz );
	}

	private < T > void setAttribute( final String key, final T value ) throws IOException
	{
		n5.setAttribute( "", key, value );
	}

	private < T > T getAttribute( final int channel, final String key, final Class< T > clazz ) throws IOException
	{
		final T overriddenValue = n5.getAttribute( getChannelGroupPath( channel ), key, clazz );
		return overriddenValue != null ? overriddenValue : getAttribute( key, clazz );
	}

	private < T > void setAttribute( final int channel, final String key, final T value ) throws IOException
	{
		n5.setAttribute( getChannelGroupPath( channel ), key, value );
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
