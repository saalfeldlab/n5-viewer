/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.n5.bdv;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatasetSelectorDialog
{
	private static final String HISTORY_PREF_KEY = "n5-viewer.history";

	private GenericDialogPlus gd;
	private Choice choice;

	private Button okButton;
	private boolean removeFirstItem;
	private int lastItemIndex = -1;

	public String run()
	{
		gd = new GenericDialogPlus( "N5 Viewer" );

		// load selection history
		final SelectionHistory selectionHistory = new SelectionHistory( HISTORY_PREF_KEY );

		// add selection history component
		gd.addChoice( "N5_dataset_path: ", selectionHistory.getHistory().toArray( new String[ 0 ] ), "" );
		choice = ( Choice ) gd.getChoices().get( 0 );

		// hack to stretch the choice component horizontally
		gd.remove( choice );
		final GridBagConstraints choiceConstraints = new GridBagConstraints();
		choiceConstraints.fill = GridBagConstraints.HORIZONTAL;
		choiceConstraints.gridx = 1;
		choiceConstraints.gridy = 0;
		choiceConstraints.insets = new Insets( 5, 0, 5, 0 );
		gd.add( choice, choiceConstraints);

		// add browse button & listener
		final Button browseButton = new Button( "Browse..." );
		gd.add( browseButton );
		browseButton.addActionListener( new BrowseListener() );
		browseButton.addKeyListener( gd );

		// add browse button & listener
		final Button openLinkButton = new Button( "Open link..." );
		gd.add( openLinkButton );
		openLinkButton.addActionListener( new OpenLinkListener() );
		openLinkButton.addKeyListener( gd );

		// add handler to set OK button state at startup
		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						okButton = gd.getButtons()[ 0 ];
						okButton.setEnabled( choice.getItemCount() > 0 );
					}
				}
			);

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// update selection history
		final String n5Path = gd.getNextChoice();
		selectionHistory.addToHistory( n5Path );

		return n5Path;
	}

	private void setSelectedItem( final String newSelected )
	{
		final List< String > choiceItems = new ArrayList<>();
		for ( int i = 0; i < choice.getItemCount(); ++i )
			choiceItems.add( choice.getItem( i ) );

		// put old selected item back on its position, or just remove it if was not present in the history
		if ( removeFirstItem )
		{
			final String oldSelected = choiceItems.remove( 0 );
			if ( lastItemIndex != -1 )
				choiceItems.add( lastItemIndex, oldSelected );
		}

		// move new selected item to the top, or just add it if was not present if the history
		final int newSelectedIndex = choiceItems.indexOf( newSelected );
		if ( newSelectedIndex != -1 )
			choiceItems.remove( newSelectedIndex );
		choiceItems.add( 0, newSelected );

		okButton.setEnabled( true );
		removeFirstItem = true;
		lastItemIndex = newSelectedIndex;

		choice.removeAll();
		for ( final String item : choiceItems )
			choice.add( item );
		choice.select( 0 );
	}

	private class BrowseListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent event )
		{
			File directory = choice.getSelectedItem() != null ? new File( choice.getSelectedItem() ) : null;
			while ( directory != null && !directory.exists() )
				directory = directory.getParentFile();

			final JFileChooser directoryChooser = new JFileChooser( directory );
			directoryChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			final int result = directoryChooser.showOpenDialog( gd );
			if ( result != JFileChooser.APPROVE_OPTION )
				return;

			setSelectedItem( directoryChooser.getSelectedFile().getAbsolutePath() );
		}
	}

	private class OpenLinkListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent event )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addStringField( "Paste_link_here:", "", 50 );
			gd.showDialog();
			if ( gd.wasCanceled() )
				return;

			final String linkStr = gd.getNextString().trim();

			if ( DataAccessType.detectType( linkStr ) == null )
			{
				IJ.error( "The link cannot be parsed or is not supported." + System.lineSeparator() +
						"It should be either a file path, an Amazon Web Services S3 link, or a Google Cloud Storage link." );
				return;
			}

			setSelectedItem( linkStr );
		}
	}
}
