package org.janelia.saalfeldlab.n5.bdv;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.N5URI;
import org.janelia.saalfeldlab.n5.ui.DataSelection;

import bdv.BigDataViewer;
import bdv.util.BdvOptions;
import ij.IJ;
import ij.ImageJ;
import ij.Macro;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;

/**
 * {@link BigDataViewer}-based Fiji plugin for viewing N5 datasets.
 *
 * @author Igor Pisarev
 * @author John Bogovic
 */
public class N5ViewerPlugin implements PlugIn {

	public static final String COMMAND_NAME = "HDF5/N5/Zarr/OME-NGFF Viewer";
	public static final String N5_URIS_KEY = "urls";

	final public static void main(final String... args) {

//		// can also run directly from a url
//		BdvHandle bdv = N5Viewer.show( "s3://janelia-cosem-datasets/jrc_mus-meissner-corpuscle-1/jrc_mus-meissner-corpuscle-1.n5?/em/fibsem-uint8" );

		new ImageJ();
		new N5ViewerPlugin().run("");
	}

	private boolean initialRecorderState;

	public N5ViewerPlugin() {
		// store value of record
		// necessary to skip initial opening of this dialog
		initialRecorderState = Recorder.record;
		Recorder.record = false;
	}

	@Override
	public void run(final String args) {

		final String macroOptions = Macro.getOptions();
		String options = args;
		if (options == null || options.isEmpty())
			options = macroOptions;

		final boolean isMacro = (options != null && !options.isEmpty());

//		final boolean isMacro = (args != null && !args.isEmpty());
//		final String options = args;

		if (isMacro) {

			// disable recorder
			initialRecorderState = Recorder.record;
			Recorder.record = false;

			final String n5Uris = Macro.getValue(options, N5_URIS_KEY, "");
			N5Viewer.show(n5Uris.split(","), BdvOptions.options().frameTitle("N5 Viewer"));

			// set recorder back
			Recorder.record = initialRecorderState;

		} else {

			new N5ViewerCreator().openViewer(
					IJ::handleException,
					null,
					this::selectionConsumer,
					this::cancelConsumer);
		}
	}

	protected static void recordMacro(final String n5Uris) {

		if (!Recorder.record)
			return;

		Recorder.setCommand(COMMAND_NAME);

		Recorder.resetCommandOptions();
		Recorder.recordOption(N5_URIS_KEY, n5Uris);
		Recorder.getCommandOptions();

		Recorder.saveCommand();
		Recorder.getCommandOptions();
	}

	private void selectionConsumer(final DataSelection selection) {

		Recorder.record = initialRecorderState;
		if (Recorder.record) {

			final URI rootUri = selection.n5.getURI();
			final String n5Uris = selection.metadata.stream().map(m -> {
				try {
					return N5URI.from(rootUri.toString(), m.getPath(), null).toString();
				} catch (URISyntaxException e) {
					return null;
				}
			}).filter(x -> x != null).collect(Collectors.joining(","));

			recordMacro(n5Uris);
		}
	}

	private void cancelConsumer(final Void v) {

		Recorder.record = initialRecorderState;
	}
}
