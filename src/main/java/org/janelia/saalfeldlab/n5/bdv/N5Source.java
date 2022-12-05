/*-
 * #%L
 * N5 Viewer
 * %%
 * Copyright (C) 2017 - 2022 Igor Pisarev, Stephan Saalfeld
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.janelia.saalfeldlab.n5.bdv;

import bdv.util.AbstractSource;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;

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
}
