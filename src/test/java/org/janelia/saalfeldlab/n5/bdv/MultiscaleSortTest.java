package org.janelia.saalfeldlab.n5.bdv;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import net.imglib2.realtransform.AffineTransform3D;

public class MultiscaleSortTest {

	@Test
	public void testScaleSorting() {

		final AffineTransform3D a = new AffineTransform3D();
		a.scale(1.0);
		final AffineTransform3D b = new AffineTransform3D();
		b.scale(2.0);
		final AffineTransform3D c = new AffineTransform3D();
		c.scale(3.0);
		final AffineTransform3D d = new AffineTransform3D();
		d.scale(4.0);

		final String[] paths = new String[]{"d", "b", "a", "c"};
		final AffineTransform3D[] xfms = new AffineTransform3D[]{d, b, a, c};

		final MultiscaleDatasets datasets = MultiscaleDatasets.sort(paths, xfms);
		assertArrayEquals("check order", datasets.getPaths(), new String[]{"a", "b", "c", "d"});
	}

}
