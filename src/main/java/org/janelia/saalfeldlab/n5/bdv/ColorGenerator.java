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

import java.util.Random;

import net.imglib2.type.numeric.ARGBType;

public class ColorGenerator
{
	private static final int[] PREDEFINED_COLORS = new int[] {
			ARGBType.rgba( 0xff, 0, 0xff, 0xff ),
			ARGBType.rgba( 0, 0xff, 0, 0xff ),
			ARGBType.rgba( 0, 0, 0xff, 0xff ),
			ARGBType.rgba( 0xff, 0, 0, 0xff ),
			ARGBType.rgba( 0xff, 0xff, 0, 0xff ),
			ARGBType.rgba( 0, 0xff, 0xff, 0xff ),
		};

	public static ARGBType[] getColors( final int numChannels )
	{
		assert numChannels >= 0;
		if ( numChannels <= 0 )
			return new ARGBType[ 0 ];
		else if ( numChannels == 1 )
			return new ARGBType[] { new ARGBType( 0xffffffff ) };

		final ARGBType[] colors = new ARGBType[ numChannels ];
		Random rnd = null;

		for ( int c = 0; c < numChannels; ++c )
		{
			if ( c < PREDEFINED_COLORS.length )
			{
				colors[ c ] = new ARGBType( PREDEFINED_COLORS[ c ] );
			}
			else
			{
				if ( rnd == null )
					rnd = new Random();

				colors[ c ] = new ARGBType( ARGBType.rgba( rnd.nextInt( 1 << 7 ) << 1, rnd.nextInt( 1 << 7 ) << 1, rnd.nextInt( 1 << 7 ) << 1, 0xff ) );
			}
		}

		return colors;
	}
}
