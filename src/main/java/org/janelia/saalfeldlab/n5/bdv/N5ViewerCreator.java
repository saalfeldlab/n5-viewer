package org.janelia.saalfeldlab.n5.bdv;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.imagej.ImagePlusLegacyMetadataParser;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.ui.DatasetSelectorDialog;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5GenericSingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5ViewerMultiscaleMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser;

import ij.ImageJ;

/**
 * Workflow which asks the user to browse the filesystem and select data sets to
 * open, then opens an N5Viewer with the selected data sets.
 *
 * Maintains state concerning the previously opened container, and fills it in
 * if the user requests the workflow to run again.
 *
 * @see N5Viewer
 */
public class N5ViewerCreator {

	public static final N5MetadataParser<?>[] n5vGroupParsers = new N5MetadataParser[]{
			new OmeNgffMetadataParser(),
			new N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
			new N5ViewerMultiscaleMetadataParser(),
			new CanonicalMetadataParser(),
			new N5ViewerMultichannelMetadata.N5ViewerMultichannelMetadataParser(),
			new N5ViewerMultichannelMetadata.GenericMultichannelMetadataParser()

	};

	public static final N5MetadataParser<?>[] n5vParsers = new N5MetadataParser[]{
			new ImagePlusLegacyMetadataParser(),
			new N5CosemMetadataParser(),
			new N5SingleScaleMetadataParser(),
			new CanonicalMetadataParser(),
			new N5GenericSingleScaleMetadataParser()
	};

	private String lastOpenedContainer = "";

	final public static void main(final String... args) {

		new ImageJ();
		ij.Prefs.setThreads(6);
		new N5ViewerCreator().openViewer(Throwable::printStackTrace);
	}

	/**
	 * Display a data selection dialog, and open a viewer with the selected
	 * data.
	 *
	 * @param exceptionHandler
	 *            handler for any exceptions throw once the data has been
	 *            selected
	 */
	public void openViewer(final Consumer<Exception> exceptionHandler) {

		openViewer(exceptionHandler, null);
	}

	/**
	 * Display a data selection dialog, and open a viewer with the selected
	 * data.
	 *
	 * @param exceptionHandler
	 *            handler for any exceptions throw once the data has been
	 *            selected
	 * @param viewerConsumer
	 *            consumer for the viewer that is created once the data has been
	 *            selected
	 */
	public void openViewer(
			final Consumer<Exception> exceptionHandler,
			final Consumer<N5Viewer> viewerConsumer) {

		openViewer( exceptionHandler, viewerConsumer, null, null );
	}

	/**
	 * Display a data selection dialog, and open a viewer with the selected
	 * data.
	 *
	 * @param exceptionHandler
	 *            handler for any exceptions throw once the data has been
	 *            selected
	 * @param viewerConsumer
	 *            consumer for the viewer that is created once the data has been
	 *            selected
	 * @param selectionConsumer
	 *            consumer for the data selected in the dialog
	 * @param cancelConsumer
	 *            consumer to be executed if the dialog is cancelled.
	 */
	public void openViewer(
			final Consumer<Exception> exceptionHandler,
			final Consumer<N5Viewer> viewerConsumer,
			final Consumer<DataSelection> selectionConsumer,
			final Consumer<Void> cancelConsumer) {

		final ExecutorService exec = Executors.newFixedThreadPool(ij.Prefs.getThreads());
		final DatasetSelectorDialog dialog = new DatasetSelectorDialog(
				new N5Importer.N5ViewerReaderFun(),
				new N5Importer.N5BasePathFun(),
				lastOpenedContainer,
				n5vGroupParsers,
				n5vParsers);

		dialog.setLoaderExecutor(exec);
		dialog.setContainerPathUpdateCallback(x -> lastOpenedContainer = x);
		dialog.setTreeRenderer(new N5ViewerTreeCellRenderer(false));
		dialog.setCancelCallback(cancelConsumer);

		dialog.run(selection -> {
			try {
				final N5Viewer n5Viewer = new N5Viewer(null, selection, true);
				if (selectionConsumer != null) {
					selectionConsumer.accept(selection);
				}
				if (viewerConsumer != null) {
					viewerConsumer.accept(n5Viewer);
				}
			} catch (final IOException e) {
				exceptionHandler.accept(e);
			}
		});
	}
}
