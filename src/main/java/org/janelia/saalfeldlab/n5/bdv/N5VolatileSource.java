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

import bdv.cache.SharedQueue;
import bdv.util.AbstractSource;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;

import java.util.function.Supplier;

public class N5VolatileSource< T extends NumericType< T >, V extends Volatile< T > & NumericType< V > > extends AbstractSource< V > {

    private final N5Source< T > source;

    private SharedQueue queue;

    public N5VolatileSource(
            final N5Source< T > source,
            final V type,
            final SharedQueue queue )
    {
        super( type, source.getName() );
        this.source = source;
        this.queue = queue;
    }

    public N5VolatileSource(
            final N5Source< T > source,
            final Supplier< V > typeSupplier,
            final SharedQueue queue )
    {
        this( source, typeSupplier.get(), queue );
    }

    @Override
    public RandomAccessibleInterval< V > getSource(final int t, final int level )
    {
        return VolatileViews.wrapAsVolatile( source.getSource( t, level ), queue, new CacheHints( LoadingStrategy.VOLATILE, level, true ) );
    }

    @Override
    public synchronized void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
    {
        source.getSourceTransform( t, level, transform );
    }

    @Override
    public VoxelDimensions getVoxelDimensions()
    {
        return source.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels()
    {
        return source.getNumMipmapLevels();
    }
}
