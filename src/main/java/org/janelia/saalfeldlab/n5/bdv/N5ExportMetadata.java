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

	private final String name;
	private final int numChannels;
	private final double[][] scales;
	private final VoxelDimensions pixelResolution;

	public N5ExportMetadata( final String name, final int numChannels, final double[][] scales, final VoxelDimensions pixelResolution )
	{
		this.name = name;
		this.numChannels = numChannels;
		this.scales = scales;
		this.pixelResolution = pixelResolution;
	}

	public String getName() { return name; }
	public int getNumChannels() { return numChannels; }
	public double[][] getScales() { return scales; }
	public VoxelDimensions getPixelResolution() { return pixelResolution; }

	public static String getChannelGroupPath( final int channel )
	{
		return "c" + channel;
	}

	public static String getScaleLevelDatasetPath( final int channel, final int scale )
	{
		return getChannelGroupPath( channel ) + "/s" + scale;
	}

	public static void write( final String basePath, final int numChannels, final double[][] scales, final FinalVoxelDimensions pixelResolution ) throws IOException
	{
		final Map< String, Object > attributes = new HashMap<>();
		attributes.put( numChannelsKey, numChannels );
		attributes.put( scalesKey, scales );
		attributes.put( pixelResolutionKey, pixelResolution );
		final N5Writer n5 = N5.openFSWriter( basePath );
		n5.setAttributes( "", attributes );
	}

	public static N5ExportMetadata read( final String basePath ) throws IOException
	{
		final N5Reader n5 = N5.openFSReader( basePath );
		return new N5ExportMetadata(
				Paths.get( basePath ).getFileName().toString(),
				n5.getAttribute( "", numChannelsKey, Integer.class ),
				n5.getAttribute( "", scalesKey, double[][].class ),
				n5.getAttribute( "", pixelResolutionKey, FinalVoxelDimensions.class ) );
	}
}