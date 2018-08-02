package org.janelia.saalfeldlab.n5.bdv.dataaccess.fs;

import java.awt.Choice;
import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;

import org.janelia.saalfeldlab.n5.bdv.BrowseHandler;

public class FSBrowseHandler implements BrowseHandler
{
	private final Component parent;
	private final Choice choice;

	public FSBrowseHandler( final Component parent, final Choice choice )
	{
		this.parent = parent;
		this.choice = choice;
	}

	@Override
	public String select()
	{
		File directory = choice.getSelectedItem() != null ? new File( choice.getSelectedItem() ) : null;
		while ( directory != null && !directory.exists() )
			directory = directory.getParentFile();

		final JFileChooser directoryChooser = new JFileChooser( directory );
		directoryChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

		final int result = directoryChooser.showOpenDialog( parent );
		return result == JFileChooser.APPROVE_OPTION ? directoryChooser.getSelectedFile().getAbsolutePath() : null;
	}
}
