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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import net.imglib2.realtransform.AffineTransform3D;

public class MultiscaleSortTest
{
	@Test
	public void testScaleSorting()
	{
		AffineTransform3D a = new AffineTransform3D();
		a.scale( 1.0 );
		AffineTransform3D b = new AffineTransform3D();
		b.scale( 2.0 );
		AffineTransform3D c = new AffineTransform3D();
		c.scale( 3.0 );
		AffineTransform3D d = new AffineTransform3D();
		d.scale( 4.0 );

		String[] paths = new String[] { "d","b","a","c" };
		AffineTransform3D[] xfms = new AffineTransform3D[]{ d, b, a, c };

		MultiscaleDatasets datasets = MultiscaleDatasets.sort( paths, xfms );
		assertArrayEquals( "check order", datasets.getPaths(), new String[] {"a", "b", "c", "d" });
	}

}
