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

		new ImageJ();
		new N5ViewerPlugin().run("");
	}

	@Override
	public void run(final String args) {

		new N5ViewerCreator().openViewer(IJ::handleException);
	}
}
