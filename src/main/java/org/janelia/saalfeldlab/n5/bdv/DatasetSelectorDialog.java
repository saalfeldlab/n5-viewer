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
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.bdv.googlecloud.GoogleCloudBrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.s3.S3BrowseHandler;

import com.amazonaws.services.s3.AmazonS3URI;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;

public class DatasetSelectorDialog
{
	public static class Selection
	{
		public final String n5Path;
		public final DataAccessType storageType;
		public final boolean readonly;

		private Selection( final String n5Path, final DataAccessType storageType, final boolean readonly )
		{
			this.n5Path = n5Path;
			this.storageType = storageType;
			this.readonly = readonly;
		}
	}

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

	private Map< DataAccessType, Checkbox > storageCheckboxes;
	private Map< DataAccessType, SelectionHistory > storageSelectionHistory;
	private Map< DataAccessType, BrowseHandler > storageBrowseHandlers;

	private DataAccessType selectedStorageType;
	private BrowseListener browseListener;
	private Choice choice;

	public Selection run()
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
						storages.get( DataAccessType.GOOGLE_CLOUD ),
						"Link"
					},
				1,
				storages.size() + 1,
				storages.get( selectedStorageType )
			);

		// add storage type change listener
		final StorageTypeListener storageListener = new StorageTypeListener();
		final LinkListener linkListener = new LinkListener();
		final Deque< Component > components = new ArrayDeque<>( Collections.singleton( gd ) );
		storageCheckboxes = new HashMap<>();
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
				final DataAccessType storageType = getAccessTypeByLabel( checkbox.getLabel() );
				if ( storageType != null )
				{
					storageCheckboxes.put( storageType, checkbox );
					checkbox.addItemListener( storageListener );
				}
				else
				{
					checkbox.addItemListener( linkListener );
				}
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

		final Checkbox readonlyCheckbox = new Checkbox( "Read-only", false );

		// add handler to toggle OK button state at startup
		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						final Button okButton = gd.getButtons()[ 0 ];
						browseListener.setOkButton( okButton );

						// add read-only checkbox
						final GridBagConstraints readonlyCheckboxConstraints = new GridBagConstraints();
						readonlyCheckboxConstraints.gridx = 2;
						readonlyCheckboxConstraints.gridy = 2;
						readonlyCheckboxConstraints.insets = new Insets( 15, 5, 0, 5 );
						gd.add( readonlyCheckbox, readonlyCheckboxConstraints );
					}
				}
			);

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// update selected storage type
		Prefs.set( STORAGE_PREF_KEY, selectedStorageType.toString() );

		// update selection history
		final String n5Path = gd.getNextChoice();
		storageSelectionHistory.get( selectedStorageType ).addToHistory( n5Path );

		return new Selection( n5Path, selectedStorageType, readonlyCheckbox.getState() );
	}

	private void updateSelectedStorageType()
	{
		storageCheckboxes.get( selectedStorageType ).setState( true );
	}

	private void updateSelectionHistory()
	{
		final List< String > choiceItems = getChoiceItems();
		choice.removeAll();
		for ( final String choiceItem : choiceItems )
			choice.add( choiceItem );
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
			updateSelectionHistory();
			updateBrowseListener();
		}
	}

	private class LinkListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addStringField( "Paste_link_here:", "", 50 );
			gd.showDialog();
			if ( gd.wasCanceled() )
			{
				updateSelectedStorageType();
				return;
			}

			final String linkStr = gd.getNextString();
			URI uri;
			try
			{
				uri = URI.create( linkStr );
				if ( uri.getScheme() == null )
					throw new NullPointerException();
			}
			catch ( final Exception e )
			{
				fallback( "Link cannot be parsed." );
				return;
			}

			final DataAccessType storageType;
			if ( uri.getScheme().equalsIgnoreCase( "http" ) || uri.getScheme().equalsIgnoreCase( "https" ) )
			{
				// s3 uri parser is capable of parsing http links, try to parse it first as an s3 uri
				AmazonS3URI s3Uri;
				try
				{
					s3Uri = new AmazonS3URI( uri );
				}
				catch ( final Exception e )
				{
					s3Uri = null;
				}

				if ( s3Uri != null )
				{
					if ( s3Uri.getBucket() == null || s3Uri.getBucket().isEmpty() || ( s3Uri.getKey() != null && !s3Uri.getKey().isEmpty() ) )
					{
						fallback( "N5 datasets on AWS S3 are stored in buckets. Please provide a link to a bucket." );
						return;
					}
					storageType = DataAccessType.AMAZON_S3;
					uri = DataAccessFactory.createBucketUri( DataAccessType.AMAZON_S3, s3Uri.getBucket() );
				}
				else
				{
					// might be a google cloud link
					final GoogleCloudStorageURI googleCloudUri;
					try
					{
						googleCloudUri = new GoogleCloudStorageURI( uri );
					}
					catch ( final Exception e )
					{
						fallback( "The link should point to AWS S3 bucket or Google Cloud Storage bucket." );
						return;
					}

					if ( googleCloudUri.getBucket() == null || googleCloudUri.getBucket().isEmpty() || ( googleCloudUri.getKey() != null && !googleCloudUri.getKey().isEmpty() ) )
					{
						fallback( "N5 datasets on Google Cloud are stored in buckets. Please provide a link to a bucket." );
						return;
					}
					storageType = DataAccessType.GOOGLE_CLOUD;
					uri = DataAccessFactory.createBucketUri( DataAccessType.GOOGLE_CLOUD, googleCloudUri.getBucket() );
				}
			}
			else
			{
				try
				{
					storageType = DataAccessFactory.getTypeByUri( uri );
				}
				catch ( final Exception e )
				{
					fallback( "The protocol is not supported." );
					return;
				}
			}

			final String correctedLink;
			if ( storageType == DataAccessType.FILESYSTEM )
			{
				correctedLink = Paths.get( uri ).toString();
			}
			else
			{
				if ( !uri.getPath().isEmpty() && !uri.getPath().equals( "/" ) )
				{
					fallback(
							storageType == DataAccessType.AMAZON_S3 ?
									"N5 datasets on AWS S3 are stored in buckets. Please provide a link to a bucket." :
									"N5 datasets on Google Cloud are stored in buckets. Please provide a link to a bucket."
						);
					return;
				}
				correctedLink = uri.toString() + ( uri.getPath().isEmpty() ? "/" : "" );
			}

			selectedStorageType = storageType;
			updateSelectedStorageType();
			updateSelectionHistory();
			updateBrowseListener();
			browseListener.setSelectedItem( correctedLink );
		}

		private void fallback( final String errorMessage )
		{
			IJ.error( "N5 Viewer", errorMessage );
			updateSelectedStorageType();
		}
	}
}
