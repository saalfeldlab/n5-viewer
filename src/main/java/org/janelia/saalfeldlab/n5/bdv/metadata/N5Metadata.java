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
package org.janelia.saalfeldlab.n5.bdv.metadata;

import com.google.gson.GsonBuilder;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Marker interface for single-scale or multi-scale N5 metadata (and possibly more).
 */
public interface N5Metadata {

    static GsonBuilder getGsonBuilder()
    {
        final GsonBuilder gsonBuilder = new GsonBuilder();
        registerGsonTypeAdapters( gsonBuilder );
        return gsonBuilder;
    }

    static void registerGsonTypeAdapters( final GsonBuilder gsonBuilder )
    {
        gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
    }
}
