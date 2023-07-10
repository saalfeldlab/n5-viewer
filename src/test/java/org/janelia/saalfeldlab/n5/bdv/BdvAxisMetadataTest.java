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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadataParser;
import org.janelia.saalfeldlab.n5.universe.translation.JqUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
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
		checkTranslatedSizes("xyzCanonical", 1, new long[]{2,3,4} , "xyz");
		checkTranslatedSizes("cxyCanonical", 1, new long[]{3, 4}, "cxy");
	}
	
	private void checkTranslatedSizes( String dataset, int ntTrue, long[] szTrue, String suffix ) {
		
		final CanonicalMetadataParser parser = new CanonicalMetadataParser();
		Optional<CanonicalMetadata> meta1 = parser.parseMetadata(n5, dataset);
		MetadataSource<?> src = new MetadataSource<>(n5, (N5DatasetMetadata)meta1.get());

		assertEquals("nt " + suffix, ntTrue, src.numTimePoints() );
		assertArrayEquals("sz " + suffix, szTrue, Intervals.dimensionsAsLongArray( src.getSource(0, 0)));
	}

}
