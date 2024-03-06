package org.janelia.saalfeldlab.n5.bdv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.ij.N5ScalePyramidExporter;
import org.janelia.saalfeldlab.n5.metadata.imagej.ImagePlusLegacyMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.imagej.N5ImagePlusMetadata;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisUtils;
import org.junit.Before;
import org.junit.Test;

import bdv.cache.SharedQueue;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

public class BdvMetadataIoTests {

	private File baseDir;

	@Before
	public void before() {

		final URL configUrl = BdvMetadataIoTests.class.getResource("/plugins.config");
		baseDir = new File(configUrl.getFile()).getParentFile();
	}

	public <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> void readWriteParseTest(
			final ImagePlus imp,
			final String outputPath,
			final String dataset,
			final String blockSizeString,
			final String metadataType,
			final String compressionType,
			final boolean testMeta,
			final boolean testData) throws IOException {

		final N5ScalePyramidExporter writer = new N5ScalePyramidExporter();
		writer.setOptions(imp, outputPath, dataset, N5ScalePyramidExporter.AUTO_FORMAT, blockSizeString, true,
				N5ScalePyramidExporter.DOWN_SAMPLE, metadataType, compressionType);

		writer.setOverwrite(true); 	// overwrite on for this test
		writer.run(); 				// run() closes the n5 writer

		final String readerDataset = dataset;

		final N5Reader n5 = new N5Factory().openReader(outputPath);
		final N5DatasetDiscoverer datasetDiscoverer = new N5DatasetDiscoverer(n5, Executors.newSingleThreadExecutor(), (x) -> true,
				Arrays.asList(N5ViewerCreator.n5vParsers),
				Arrays.asList(N5ViewerCreator.n5vGroupParsers));

		final N5TreeNode root = datasetDiscoverer.discoverAndParseRecursive("");
		final Optional<N5TreeNode> metaOpt = root.getDescendant(readerDataset);
		if (!metaOpt.isPresent())
			fail("could not find metadata at: " + readerDataset);

		final List<ConverterSetup> converterSetups = new ArrayList<>();
		final List<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();

		final SharedQueue sharedQueue = new SharedQueue(1);
		final BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");

		final int numTimepoints = N5Viewer.buildN5Sources(
				n5,
				new DataSelection(n5, Collections.singletonList(metaOpt.get().getMetadata())),
				sharedQueue,
				converterSetups,
				sourcesAndConverters,
				options);

		assertEquals(String.format("channels for %s", dataset), imp.getNChannels(), sourcesAndConverters.size());
		assertEquals(String.format("time points for %s", dataset), imp.getNFrames(), numTimepoints);

		final Source<T> src0 = sourcesAndConverters.get(0).getSpimSource();
		assertEquals(String.format("slices for %s", dataset), imp.getNSlices(),
				src0.getSource(0, 0).dimension(2));

		final AffineTransform3D tform = new AffineTransform3D();
		src0.getSourceTransform(0, 0, tform);
		final double rx = tform.get(0, 0);
		final double ry = tform.get(1, 1);
		final double rz = tform.get(2, 2);
		final String unit = src0.getVoxelDimensions().unit();

		if (testMeta) {
			final boolean resEqual = rx == imp.getCalibration().pixelWidth &&
					ry == imp.getCalibration().pixelHeight &&
					rz == imp.getCalibration().pixelDepth;

			assertTrue(String.format("%s resolutions ", dataset), resEqual);
			assertTrue(String.format("%s units ", dataset),
					unit.equals(imp.getCalibration().getUnit()));

		}

		if (testData) {
			final List<Source<T>> srcList = sourcesAndConverters.stream().map(sac -> sac.getSpimSource()).collect(Collectors.toList());
			assertTrue(String.format("%s data ", dataset), sourceDataIdentical(imp, srcList));
		}
		n5.close();

		// remove
		final N5Writer n5w = new N5Factory().openWriter(outputPath);
		n5w.remove();
	}

	/*
	 * Test writing volumes of 2 to 5 dimensions of various metadata dialects,
	 * and ensure bdv sources are correctly read.
	 */
	@Test
	public void testMultiChannel() throws IOException {

		for (final String suffix : new String[]{".h5", ".n5", ".zarr"}) {
			testMultiChannelHelper(N5Importer.MetadataN5ViewerKey, suffix);
			testMultiChannelHelper(N5Importer.MetadataN5CosemKey, suffix);
			testMultiChannelHelper(N5Importer.MetadataOmeZarrKey, suffix);
			testMultiChannelHelper(N5Importer.MetadataImageJKey, suffix);
		}
	}

	public void testMultiChannelHelper(final String metatype, final String suffix) throws IOException {

		final int bitDepth = 8;

		final String n5RootPath = baseDir + "/test_" + metatype + "_dimCombos" + suffix;
		final String blockSizeString = "16";
		final String compressionString = "raw";

		// add zero to avoid eclipse making these final
		int nc = 1;
		nc += 0;
		int nz = 4;
		nz += 0;
		int nt = 5;
		nt += 0;

		for (nc = 1; nc <= 3; nc += 2) {
			for (nz = 1; nz <= 4; nz += 3) {
				for (nt = 1; nt <= 5; nt += 4) {
					final int N = nc * nz * nt;
					final ImagePlus imp = NewImage.createImage("test", 8, 6, N, bitDepth, NewImage.FILL_NOISE);
					imp.setDimensions(nc, nz, nt);
					imp.getCalibration().pixelWidth = 0.5;
					imp.getCalibration().pixelHeight = 0.6;

					if (nz > 1)
						imp.getCalibration().pixelDepth = 0.7;

					imp.getCalibration().setUnit("mm");

					final String dataset = String.format("/c%dz%dt%d", nc, nz, nt);
					readWriteParseTest(imp, n5RootPath, dataset, blockSizeString, metatype, compressionString, true, true);
				}
			}
		}
	}

	private static <T extends NumericType<T> & NativeType<T>> boolean sourceDataIdentical(final ImagePlus gt, final List<Source<T>> source) {

		@SuppressWarnings("unchecked")
		final Img<T> imgRaw = (Img<T>)ImageJFunctions.wrap(gt);
		final ImagePlusLegacyMetadataParser parser = new ImagePlusLegacyMetadataParser();
		N5ImagePlusMetadata meta;
		try {
			meta = parser.readMetadata(gt);
		} catch (final IOException e) {
			return false;
		}

		final RandomAccessibleInterval<T> img = AxisUtils.permuteForImagePlus(imgRaw, meta);

		final int nc = gt.getNChannels();
		final int nt = gt.getNFrames();
		for (int c = 0; c < nc; c++) {
			for (int t = 0; t < nt; t++) {
				final RandomAccessibleInterval<T> trueImg = Views.hyperSlice(Views.hyperSlice(img, 4, t), 2, c);
				final RandomAccessibleInterval<T> testImg = source.get(c).getSource(t, 0);
				if (!equal(trueImg, testImg))
					return false;
			}
		}

		return true;
	}

	public static <T extends NumericType<T> & NativeType<T>> boolean equal(final RandomAccessibleInterval<T> imgA, final RandomAccessibleInterval<T> imgB) {

		try {
			final Cursor<T> c = Views.flatIterable(imgA).cursor();
			final RandomAccess<T> r = imgB.randomAccess();
			while (c.hasNext()) {
				c.fwd();
				r.setPosition(c);

				if (!c.get().valueEquals(r.get()))
					return false;
			}
			return true;
		} catch (final Exception e) {
			return false;
		}
	}
}
