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

import java.lang.reflect.Type;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.imglib2.realtransform.AffineTransform3D;

class AffineTransform3DJsonAdapter implements JsonSerializer< AffineTransform3D >, JsonDeserializer< AffineTransform3D >
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