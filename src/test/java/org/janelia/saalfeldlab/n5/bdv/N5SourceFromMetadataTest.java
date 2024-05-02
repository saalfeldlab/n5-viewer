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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadata.CosemTransform;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5ViewerDatasetMetadataWriter;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadataParser;
import org.janelia.saalfeldlab.n5.universe.translation.TranslatedN5Reader;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.util.Intervals;

public class N5SourceFromMetadataTest {

	static private String testDirPath = System.getProperty("user.home") + "/tmp/n5-test";
	static private String testBaseDatasetName = "/test/bdvSourceMetadata";

	private N5FSWriter n5;

	@Before
	public void before() {

		try {
			n5 = new N5FSWriter(testDirPath);

			final ArrayImg<ByteType, ByteArray> img = ArrayImgs.bytes(7, 5, 3);
			final ArrayImg<ByteType, ByteArray> img6d = ArrayImgs.bytes(3, 5, 7, 11, 13, 17);

			// write 6d
			final String dataset6d = testBaseDatasetName + "/img6d";
			N5Utils.save(img6d, n5, dataset6d, new int[]{17, 17, 17, 17, 17, 17}, new RawCompression());
			final DatasetAttributes dsetAttrs = n5.getDatasetAttributes(dataset6d);

			// cosem
			final String datasetCosem = testBaseDatasetName + "/cosem";
			final N5CosemMetadata meta = new N5CosemMetadata(
					"",
					new CosemTransform(
							new String[]{"z", "y", "x"},
							new double[]{2, 3, 4},
							new double[]{-1, -2, -3},
							new String[]{"um", "um", "um"}),
					dsetAttrs);
			final N5CosemMetadataParser pcosem = new N5CosemMetadataParser();

			N5Utils.save(img, n5, datasetCosem, new int[]{7, 7, 7}, new RawCompression());
			pcosem.writeMetadata(meta, n5, datasetCosem);

			// n5v
			final String n5vDataset = testBaseDatasetName + "/n5v";
			final N5ViewerDatasetMetadataWriter n5vWriter = new N5ViewerDatasetMetadataWriter();

			N5Utils.save(img, n5, n5vDataset, new int[]{7, 7, 7}, new RawCompression());
			n5vWriter.writeMetadata(meta, n5, n5vDataset);

		} catch (final IOException e) {
			fail(e.getMessage());
		} catch (final Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void sixDimTests() {

		final String dataset = "/test/bdvSourceMetadata/img6d";

		// raw size: [ 3, 5, 7, 11, 13, 17 ]
		final String axisOrder1 = "[\"x\",\"y\",\"z\",\"t\",\"c\",\"q\"]";
		final long[] sz1 = new long[]{3, 5, 7};
		final int nt1 = 11;

		final String axisOrder2 = "[\"t\",\"c\",\"q\",\"x\",\"y\",\"z\"]";
		final long[] sz2 = new long[]{11, 13, 17};
		final int nt2 = 3;

		final String axisOrder3 = "[\"x\",\"q\",\"w\",\"y\",\"z\",\"o\"]";
		final long[] sz3 = new long[]{3, 11, 13};
		final int nt3 = 1;

		final String xlationBase = "include \"n5\";\n"
				+ "def genAxes( $lbls; $unit ): axesFromLabels( $lbls ;\"mm\") | "
				+ "map(. += {\"name\":.label} | del(.label));\n"
				+ "\n"
				+ "def setMeta: identityAsFlatAffine(6) as $id | \n"
				+ " . + arrayUnitAxisToTransform( $id;\n"
				+ "	     \"mm\"; \n"
				+ "	     genAxes( %s ;\"mm\"));\n"
				+ "addPaths | getSubTree(\"%s\") |= (.attributes |= setMeta)";

		checkTranslatedSizes(String.format(xlationBase, axisOrder1, dataset), dataset, nt1, sz1, "1");
		checkTranslatedSizes(String.format(xlationBase, axisOrder2, dataset), dataset, nt2, sz2, "2");
		checkTranslatedSizes(String.format(xlationBase, axisOrder3, dataset), dataset, nt3, sz3, "3");
	}

	private void checkTranslatedSizes(final String translation, final String dataset, final int ntTrue, final long[] szTrue, final String suffix) {

		final CanonicalMetadataParser parser = new CanonicalMetadataParser();
		final TranslatedN5Reader xlated = new TranslatedN5Reader(n5, translation, ".");
		final Optional<CanonicalMetadata> meta1 = parser.parseMetadata(xlated, dataset);
		final MetadataSource<?> src = new MetadataSource<>(xlated, (N5DatasetMetadata)meta1.get());

		assertEquals("nt " + suffix, ntTrue, src.numTimePoints());
		assertArrayEquals("sz " + suffix, szTrue, Intervals.dimensionsAsLongArray(src.getSource(0, 0)));
	}

	@Test
	public void cosemTest() {

		final N5CosemMetadataParser p = new N5CosemMetadataParser();
		final String dataset = testBaseDatasetName + "/cosem";

		final Optional<N5CosemMetadata> parsedMeta = p.parseMetadata(n5, dataset);
		final Optional<MetadataSource<?>> srcOpt = parsedMeta.map(m -> new MetadataSource<>(n5, m));

		assertTrue("cosem src exists", srcOpt.isPresent());
		final MetadataSource<?> src = srcOpt.get();

		assertEquals("cosem nt=1", 1, src.numTimePoints());

		final RandomAccessibleInterval<?> srcImg = src.getSource(0, 0);
		assertEquals("cosem is 3d", 3, srcImg.numDimensions());
	}

	@Test
	public void n5vTest() {

		final N5SingleScaleMetadataParser p = new N5SingleScaleMetadataParser();
		final String dataset = testBaseDatasetName + "/n5v";

		final Optional<N5SingleScaleMetadata> parsedMeta = p.parseMetadata(n5, dataset);
		final Optional<MetadataSource<?>> srcOpt = parsedMeta.map(m -> new MetadataSource<>(n5, m));

		assertTrue("n5v src exists", srcOpt.isPresent());
		final MetadataSource<?> src = srcOpt.get();

		assertEquals("n5v nt=1", 1, src.numTimePoints());

		final RandomAccessibleInterval<?> srcImg = src.getSource(0, 0);
		assertEquals("n5v is 3d", 3, srcImg.numDimensions());
	}

}
