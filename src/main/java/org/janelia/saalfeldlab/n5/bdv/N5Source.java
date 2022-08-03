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

import bdv.cache.SharedQueue;
import bdv.util.AbstractSource;
import bdv.util.volatiles.VolatileTypeMatcher;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

import java.util.function.Supplier;

public class N5Source< T extends NumericType< T > > extends AbstractSource< T >
{

	protected final RandomAccessibleInterval< T >[] images;

	protected final AffineTransform3D[] transforms;

	public N5Source(
			final T type,
			final String name,
			final RandomAccessibleInterval< T >[] images,
			final AffineTransform3D[] transforms )
	{
		super( type, name );
		this.images = images;
		this.transforms = transforms;
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		return images[ level ];
	}

	@Override
	public synchronized void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( transforms[ level ] );
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return images.length;
	}

	public < V extends Volatile< T > & NumericType< V > > N5VolatileSource< T, V > asVolatile( final V vType, final SharedQueue queue )
	{
		return new N5VolatileSource<>( this, vType, queue );
	}

	public < V extends Volatile< T > & NumericType< V > > N5VolatileSource< T, V > asVolatile(final Supplier< V > vTypeSupplier, final SharedQueue queue )
	{
		return new N5VolatileSource<>( this, vTypeSupplier, queue );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public < V extends Volatile< T > & NumericType< V > > N5VolatileSource< T, V > asVolatile( final SharedQueue queue )
	{
		final T t = getType();
		if ( t instanceof NativeType )
			return new N5VolatileSource<>( this, ( V ) VolatileTypeMatcher.getVolatileTypeForType( ( NativeType )getType() ), queue );
		else
			throw new UnsupportedOperationException( "This method only works for sources of NativeType." );
	}
}
