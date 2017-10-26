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

import org.janelia.saalfeldlab.n5.N5Writer;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public class N5ExportMetadataWriter extends N5ExportMetadataReader
{
	private final N5Writer n5Writer;

	N5ExportMetadataWriter( final N5Writer n5Writer )
	{
		super( n5Writer );
		this.n5Writer = n5Writer;
	}

	public void setName( final String name ) throws IOException { setAttribute( nameKey, name ); }

	public void setDefaultScales( final double[][] scales ) throws IOException { setAttribute( scalesKey, scales ); }
	public void setDefaultPixelResolution( final VoxelDimensions pixelResolution ) throws IOException { setAttribute( pixelResolutionKey, pixelResolution ); }
	public void setDefaultAffineTransform( final AffineTransform3D affineTransform ) throws IOException { setAttribute( affineTransformKey, affineTransform ); }

	public void setScales( final int channel, final double[][] scales ) throws IOException { setAttribute( channel, scalesKey, scales ); }
	public void setPixelResolution( final int channel, final VoxelDimensions pixelResolution ) throws IOException { setAttribute( channel, pixelResolutionKey, pixelResolution ); }
	public void setAffineTransform( final int channel, final AffineTransform3D affineTransform ) throws IOException { setAttribute( channel, affineTransformKey, affineTransform ); }

	private < T > void setAttribute( final String key, final T value ) throws IOException
	{
		n5Writer.setAttribute( "", key, value );
	}

	private < T > void setAttribute( final int channel, final String key, final T value ) throws IOException
	{
		n5Writer.setAttribute( N5ExportMetadata.getChannelGroupPath( channel ), key, value );
	}
}
