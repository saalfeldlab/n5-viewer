package org.janelia.saalfeldlab.n5.bdv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadataParser;
import org.janelia.saalfeldlab.n5.universe.translation.JqUtils;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.util.Intervals;

public class BdvAxisMetadataTest {

	private N5FSReader n5;

	@Before
	public void setUp() throws IOException {

		final String n5Root = "src/test/resources/axisTest.n5";
		n5 = new N5FSReader(n5Root, JqUtils.gsonBuilder(null));
	}

	@Test
	public void sixDimTests() {

		checkTranslatedSizes("xyzCanonical", 1, new long[]{2, 3, 4}, "xyz");
		checkTranslatedSizes("cxyCanonical", 1, new long[]{3, 4}, "cxy");
	}

	private void checkTranslatedSizes(final String dataset, final int ntTrue, final long[] szTrue, final String suffix) {

		final CanonicalMetadataParser parser = new CanonicalMetadataParser();
		final Optional<CanonicalMetadata> meta1 = parser.parseMetadata(n5, dataset);
		final MetadataSource<?> src = new MetadataSource<>(n5, (N5DatasetMetadata)meta1.get());

		assertEquals("nt " + suffix, ntTrue, src.numTimePoints());
		assertArrayEquals("sz " + suffix, szTrue, Intervals.dimensionsAsLongArray(src.getSource(0, 0)));
	}
}
