package org.janelia.saalfeldlab.n5.bdv;

import java.awt.Choice;
import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;

public class FilesystemBrowseHandler implements BrowseHandler
{
	private final Component parent;
	private final Choice choice;

	public FilesystemBrowseHandler( final Component parent, final Choice choice )
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
