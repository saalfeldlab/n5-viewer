package org.janelia.saalfeldlab.n5.bdv;

import bdv.BigDataViewer;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

/**
 * {@link BigDataViewer}-based Fiji plugin for viewing N5 datasets.
 *
 * @author Igor Pisarev
 * @author John Bogovic
 */
public class N5ViewerPlugin implements PlugIn {

	final public static void main(final String... args) {

//		// can also run directly from a url
//		BdvHandle bdv = N5Viewer.show( "s3://janelia-cosem-datasets/jrc_mus-meissner-corpuscle-1/jrc_mus-meissner-corpuscle-1.n5?/em/fibsem-uint8" );

		new ImageJ();
		new N5ViewerPlugin().run("");
	}

	@Override
	public void run(final String args) {

		new N5ViewerCreator().openViewer(IJ::handleException);
	}
}
