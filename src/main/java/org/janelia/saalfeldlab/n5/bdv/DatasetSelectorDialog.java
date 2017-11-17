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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory.DataAccessType;
import org.janelia.saalfeldlab.n5.bdv.googlecloud.GoogleCloudBrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.s3.S3BrowseHandler;

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

	private Map< DataAccessType, SelectionHistory > storageSelectionHistory;
	private Map< DataAccessType, BrowseHandler > storageBrowseHandlers;

	private DataAccessType selectedStorageType;
	private BrowseListener browseListener;
	private Choice choice;

	public String run()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );

		// load selection history for all storage types
		storageSelectionHistory = new HashMap<>();
		for ( final Entry< DataAccessType, String > entry : storageHistoryPrefKeys.entrySet() )
			storageSelectionHistory.put( entry.getKey(), new SelectionHistory( entry.getValue() ) );

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
		gd.addChoice( "N5_dataset_path: ", getChoiceItems().toArray( new String[ 0 ] ), "" );
		choice = ( Choice ) gd.getChoices().get( 0 );

		// hack to stretch the choice component horizontally
		gd.remove( choice );
		final GridBagConstraints choiceConstraints = new GridBagConstraints();
		choiceConstraints.fill = GridBagConstraints.HORIZONTAL;
		choiceConstraints.gridx = 1;
		choiceConstraints.gridy = 1;
		choiceConstraints.insets = new Insets( 5, 0, 5, 0 );
		gd.add( choice, choiceConstraints);

		// create browse handlers
		storageBrowseHandlers = new HashMap<>();
		storageBrowseHandlers.put( DataAccessType.FILESYSTEM, new FilesystemBrowseHandler( gd, choice ) );
		storageBrowseHandlers.put( DataAccessType.AMAZON_S3, new S3BrowseHandler() );
		storageBrowseHandlers.put( DataAccessType.GOOGLE_CLOUD, new GoogleCloudBrowseHandler() );

		// add browse button & listener
		browseListener = new BrowseListener();
		browseListener.setChoice( choice );
		updateBrowseListener();

		final Button browseButton = new Button( "Browse..." );
		browseButton.addActionListener( browseListener );
		browseButton.addKeyListener( gd );
		final GridBagConstraints browseButtonConstraints = new GridBagConstraints();
		browseButtonConstraints.gridwidth = 2;
		browseButtonConstraints.gridy = 1;
		browseButtonConstraints.insets = new Insets( 0, 5, 0, 0 );
		gd.add( browseButton, browseButtonConstraints );

		// add handler to toggle OK button state at startup
		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						final Button okButton = gd.getButtons()[ 0 ];
						browseListener.setOkButton( okButton );
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
		return new ArrayList<>( storageSelectionHistory.get( selectedStorageType ).getHistory() );
	}

	private void updateBrowseListener()
	{
		browseListener.update( storageBrowseHandlers.get( selectedStorageType ) );
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
			updateBrowseListener();
		}
	}
}
