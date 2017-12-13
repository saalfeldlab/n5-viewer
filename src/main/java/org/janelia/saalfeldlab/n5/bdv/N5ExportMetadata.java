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

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import com.google.gson.GsonBuilder;

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
 *   "affineTransform":[[1,-0.30,-0.25,0],[0,1.25,0,0],[0,0,0.85,0]]
 *}</pre>
 *
 * @author Igor Pisarev
 */
public interface N5ExportMetadata
{
	public static String getChannelGroupPath( final int channel )
	{
		return String.format( "c%d", channel );
	}

	public static String getScaleLevelDatasetPath( final int channel, final int scale )
	{
		return String.format( "%s/s%d", getChannelGroupPath( channel ), scale );
	}

	public static GsonBuilder getGsonBuilder()
	{
		final GsonBuilder gsonBuilder = new GsonBuilder();
		registerGsonTypeAdapters( gsonBuilder );
		return gsonBuilder;
	}

	public static void registerGsonTypeAdapters( final GsonBuilder gsonBuilder )
	{
		gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
	}

	public static N5ExportMetadataReader openForReading( final N5Reader n5Reader )
	{
		return new N5ExportMetadataReader( n5Reader );
	}

	public static N5ExportMetadataWriter openForWriting( final N5Writer n5Writer )
	{
		return new N5ExportMetadataWriter( n5Writer );
	}
}
