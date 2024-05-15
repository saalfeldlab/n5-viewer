package org.janelia.saalfeldlab.n5.bdv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.janelia.saalfeldlab.n5.N5URI;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.NgffTests;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bdv.cache.SharedQueue;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public class N5vAxisPermutationTests {

	public static final double EPS = 1e-6;

	public static final String[] defaultAxes = new String[]{"x", "y", "c", "z", "t"};

	private URI containerUri;

	@Before
	public void before() {

		System.setProperty("java.awt.headless", "true");

		try {
			containerUri = new File(tempN5PathName()).getCanonicalFile().toURI();
		} catch (final IOException e) {}
	}

	@After
	public void after() {

		final N5Writer n5 = new N5Factory().openWriter(containerUri.toString());
		n5.remove();
	}

	private static String tempN5PathName() {

		try {
			final File tmpFile = Files.createTempDirectory("n5-viewer-ngff-test-").toFile();
			tmpFile.deleteOnExit();
			return tmpFile.getCanonicalPath();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected String tempN5Location() throws URISyntaxException {

		final String basePath = new File(tempN5PathName()).toURI().normalize().getPath();
		return new URI("file", null, basePath, null).toString();
	}

	@Test
	public void testPermutations() throws IOException {

		final N5Writer zarr = new N5Factory().openWriter(containerUri.toString());
		// don't check every axis permutation, but some relevant ones, and some strange ones
		final String[] names = new String[]{

				// TODO five still don't work
				"xyz", "zyx", "yzx",
				"xyc", "xcy", "cyx",
				"xyt", "xty", "tyx",
				"xyzt", "xtyz", "zxty", "tyzx",
				"xyczt", "xyzct", "xytcz", "tzcyx", "ctzxy"
		};

		// check both c- and f-order storage
		for (final String axes : names) {
			writeAndTest(zarr, axes + "_c");
			writeAndTest(zarr, axes + "_f");
		}

	}

	protected void writeAndTest(final N5Writer zarr, final String dset) throws IOException {

		writeAndTest(zarr, dset, NgffTests.isCOrderFromName(dset), NgffTests.permutationFromName(dset));
	}

	protected <T extends NumericType<T> & NativeType<T>> void writeAndTest(final N5Writer zarr, final String dset, final boolean cOrder, final int[] p)
			throws IOException {

		// write
		NgffTests.writePermutedAxes(zarr, baseName(p, cOrder), cOrder, p);
		final String dstNrm = N5URI.normalizeGroupPath(dset);

		// read
		final N5TreeNode node = N5DatasetDiscoverer.discover(zarr);
		final Stream<N5Metadata> metaStream = N5TreeNode.flattenN5Tree(node)
				.filter(x -> N5URI.normalizeGroupPath(x.getPath()).equals(dstNrm))
				.map(x -> {
					return (N5Metadata)x.getMetadata();
				});

		final DataSelection selection = new DataSelection(zarr, Collections.singletonList(metaStream.findFirst().get()));
		final SharedQueue sharedQueue = new SharedQueue(1);
		final List<ConverterSetup> converterSetups = new ArrayList<>();
		final List<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();

		final BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");
		final int numTimepoints = N5Viewer.buildN5Sources(
				zarr,
				selection,
				sharedQueue,
				converterSetups,
				sourcesAndConverters,
				options);

		assertTrue("sources found", sourcesAndConverters.size() > 0);
		final Source<T> src = sourcesAndConverters.get(0).getSpimSource();
		final RandomAccessibleInterval<T> rai = src.getSource(0, 0);
		final long[] dims = rai.dimensionsAsLongArray();

		final AffineTransform3D tform = new AffineTransform3D();
		src.getSourceTransform(0, 0, tform);

		// System.out.println("");
		// System.out.println("" + sourcesAndConverters.size());
		// System.out.println("");

		// test
		assertEquals(dset + "size x", NgffTests.NX, dims[0]);
		assertEquals(dset + "size y", NgffTests.NY, dims[1]);

		assertEquals(dset + "res x", NgffTests.RX, tform.get(0, 0), EPS);
		assertEquals(dset + "res y", NgffTests.RY, tform.get(1, 1), EPS);

		final char[] axes = dset.split("_")[0].toCharArray();
		if (ArrayUtils.contains(axes, NgffTests.Z)) {
			assertEquals(dset + "n slices", NgffTests.NZ, dims[2]);
			assertEquals(dset + "res z", NgffTests.RZ, tform.get(2, 2), EPS);
		}

		if (ArrayUtils.contains(axes, NgffTests.C)) {
			assertEquals(dset + "n channels", NgffTests.NC, sourcesAndConverters.size());
		}

		if (ArrayUtils.contains(axes, NgffTests.T)) {
			assertEquals(dset + "n timepoints", NgffTests.NT, numTimepoints);
		}
	}

	private static String baseName(final int[] p, final boolean cOrder) {

		final String suffix = cOrder ? "_c" : "_f";
		return Arrays.stream(p).mapToObj(i -> defaultAxes[i]).collect(Collectors.joining()) + suffix;
	}

}
