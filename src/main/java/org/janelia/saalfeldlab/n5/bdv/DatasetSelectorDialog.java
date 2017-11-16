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

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory.DataAccessType;

import fiji.util.gui.GenericDialogPlus;
import ij.Prefs;

public class DatasetSelectorDialog
{
	private static final Map< DataAccessType, String > storages;
	private static final Map< DataAccessType, String > storageHistoryPrefKeys;
	static
	{
		storages = new HashMap<>();
		storages.put( DataAccessType.FILESYSTEM, "Filesystem" );
		storages.put( DataAccessType.AMAZON_S3, "Amazon Web Services S3" );
		storages.put( DataAccessType.GOOGLE_CLOUD, "Google Cloud Storage" );

		storageHistoryPrefKeys = new HashMap<>();
		storageHistoryPrefKeys.put( DataAccessType.FILESYSTEM, "n5-viewer.history" );
		storageHistoryPrefKeys.put( DataAccessType.AMAZON_S3, "n5-viewer-s3.history" );
		storageHistoryPrefKeys.put( DataAccessType.GOOGLE_CLOUD, "n5-viewer-gs.history" );
	}
	private static DataAccessType getAccessTypeByLabel( final String label )
	{
		for ( final Entry< DataAccessType, String > entry : storages.entrySet() )
			if ( entry.getValue().equals( label ) )
				return entry.getKey();
		return null;
	}

	private static final String STORAGE_PREF_KEY = "n5-viewer.storage";

	private static final String EMPTY_ITEM = "                                                                                                ";

	private DataAccessType selectedStorageType;

	private Map< DataAccessType, SelectionHistory > storageSelectionHistory;
	private Map< DataAccessType, BrowseHandler > storageBrowseHandlers;

	private GenericDialogPlus gd;
	private Choice choice;
	private Button okButton;

	private boolean fakeFirstItem;

	public String run()
	{
		gd = new GenericDialogPlus( "N5 Viewer" );

		// load selection history for all storage types
		storageSelectionHistory = new HashMap<>();
		for ( final Entry< DataAccessType, String > entry : storageHistoryPrefKeys.entrySet() )
			storageSelectionHistory.put( entry.getKey(), new SelectionHistory( entry.getValue() ) );

		// create browse handlers
		storageBrowseHandlers = new HashMap<>();
		storageBrowseHandlers.put( DataAccessType.FILESYSTEM, new FilesystemBrowseHandler() );
		storageBrowseHandlers.put( DataAccessType.AMAZON_S3, new S3BrowseHandler() );
		storageBrowseHandlers.put( DataAccessType.GOOGLE_CLOUD, new GoogleCloudBrowseHandler() );

		// add storage type selector
		selectedStorageType = DataAccessType.valueOf( Prefs.get( STORAGE_PREF_KEY, DataAccessType.FILESYSTEM.toString() ) );
		gd.addRadioButtonGroup(
				null,
				new String[] {
						storages.get( DataAccessType.FILESYSTEM ),
						storages.get( DataAccessType.AMAZON_S3 ),
						storages.get( DataAccessType.GOOGLE_CLOUD )
					},
				1,
				3,
				storages.get( selectedStorageType )
			);

		// add storage type change listener
		final StorageTypeListener storageListener = new StorageTypeListener();
		final Deque< Component > components = new ArrayDeque<>( Collections.singleton( gd ) );
		while ( !components.isEmpty() )
		{
			final Component component = components.pop();
			if ( component instanceof Container )
			{
				final Container container = ( Container ) component;
				components.addAll( Arrays.asList( container.getComponents() ) );
			}
			else if ( component instanceof Checkbox )
			{
				final Checkbox checkbox = ( Checkbox ) component;
				checkbox.addItemListener( storageListener );
			}
		}

		// add selection history component
		gd.addChoice( "N5_dataset_path: ", getChoiceItems().toArray( new String[ 0 ] ), null );
		choice = ( Choice ) gd.getChoices().get( 0 );

		// add browse button & listener
		final Button button = new Button( "Browse..." );
		final BrowseListener browseListener = new BrowseListener();
		button.addActionListener( browseListener );
		button.addKeyListener( gd );
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridwidth = 2;
		constraints.gridy = 1;
		constraints.insets = new Insets( 0, 5, 0, 0 );
		gd.add( button, constraints );

		// add handler to toggle OK button state at startup
		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						okButton = gd.getButtons()[ 0 ];
						okButton.setEnabled( !fakeFirstItem );
					}
				}
			);

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// update selected storage type
		Prefs.set( STORAGE_PREF_KEY, selectedStorageType.toString() );

		// update selection history
		final String selection = gd.getNextChoice();
		storageSelectionHistory.get( selectedStorageType ).addToHistory( selection );

		return selection;
	}

	private List< String > getChoiceItems()
	{
		final List< String > choiceItems = new ArrayList<>( storageSelectionHistory.get( selectedStorageType ).getHistory() );
		fakeFirstItem = choiceItems.isEmpty();
		if ( fakeFirstItem )
			choiceItems.add( EMPTY_ITEM );
		return choiceItems;
	}

	private class StorageTypeListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			selectedStorageType = getAccessTypeByLabel( ( String ) event.getItem() );
			final List< String > choiceItems = getChoiceItems();
			choice.removeAll();
			for ( final String choiceItem : choiceItems )
				choice.add( choiceItem );
			okButton.setEnabled( !fakeFirstItem );
		}
	}

	private class BrowseListener implements ActionListener
	{
		private int lastItemIndex = -1;

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			final BrowseHandler browseHandler = storageBrowseHandlers.get( selectedStorageType );
			final String newSelected = browseHandler.select();
			if ( newSelected != null )
			{
				final List< String > choiceItems = new ArrayList<>();
				for ( int i = 0; i < choice.getItemCount(); ++i )
					choiceItems.add( choice.getItem( i ) );

				final String oldSelected = choiceItems.get( 0 );

				// put old selected item back on its position, or just remove it if was not present in the history
				if ( fakeFirstItem )
				{
					choiceItems.remove( 0 );
					if ( lastItemIndex != -1 )
						choiceItems.add( lastItemIndex, oldSelected );
				}

				// move new selected item to the top, or just add it if was not present if the history
				final int newSelectedIndex = choiceItems.indexOf( newSelected );
				if ( newSelectedIndex != -1 )
					choiceItems.remove( newSelectedIndex );
				choiceItems.add( 0, newSelected );

				fakeFirstItem = true;
				lastItemIndex = newSelectedIndex;
				if ( okButton != null )
					okButton.setEnabled( true );

				choice.removeAll();
				for ( final String item : choiceItems )
					choice.add( item );
				choice.select( 0 );
			}
		}
	}

	private interface BrowseHandler
	{
		String select();
	}

	private class FilesystemBrowseHandler implements BrowseHandler
	{
		@Override
		public String select()
		{
			File directory = new File( choice.getSelectedItem() );
			while ( directory != null && !directory.exists() )
				directory = directory.getParentFile();

			final JFileChooser directoryChooser = new JFileChooser( directory );
			directoryChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			directoryChooser.showOpenDialog( gd );
			final File selectedDirectory = directoryChooser.getSelectedFile();
			return selectedDirectory != null ? selectedDirectory.getAbsolutePath() : null;
		}
	}

	private class S3BrowseHandler implements BrowseHandler
	{
		@Override
		public String select()
		{
			throw new NotImplementedException( "TODO: S3BrowseHandler" );
		}
	}

	private class GoogleCloudBrowseHandler implements BrowseHandler
	{
		@Override
		public String select()
		{
			throw new NotImplementedException( "TODO: GoogleCloudBrowseHandler" );
		}
	}



	private static class SelectionHistory
	{
		private static final String DELIMETER = "|";
		private static final int MAX_ENTRIES = 10;

		private final String prefKey;
		private final List< String > history;

		public SelectionHistory( final String prefKey )
		{
			this.prefKey = prefKey;
			final String pref = Prefs.get( prefKey, "" );
			history = new ArrayList<>();
			if ( !pref.isEmpty() )
				history.addAll( Arrays.asList( pref.split( Pattern.quote( DELIMETER ) ) ) );
		}

		public List< String > getHistory()
		{
			return history;
		}

		public void addToHistory( final String item )
		{
			final int index = history.indexOf( item );
			if ( index == -1 )
			{
				history.add( 0, item );
				if ( history.size() > MAX_ENTRIES )
					history.remove( history.size() - 1 );
			}
			else
			{
				history.remove( index );
				history.add( 0, item );
			}

			Prefs.set( prefKey, String.join( DELIMETER, history ) );
		}
	}
}
