package org.janelia.saalfeldlab.n5.bdv.dataaccess.googlecloud;

import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.n5.bdv.BrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GoogleCloudBrowseHandler implements BrowseHandler
{
	private List< Project > projects;
	private List< Bucket > buckets;
	private java.awt.List projectsList;
	private java.awt.List bucketsList;
	private Button okButton;

	@Override
	public String select()
	{
		final ResourceManager resourceManager;
		try
		{
			resourceManager = GoogleCloudClientBuilderWithDefaultCredentials.createResourceManager();
		}
		catch ( final DataAccessException e )
		{
			return null;
		}

		// query a list of user's projects first
		projects = new ArrayList<>();
		final Iterator< Project > projectIterator = resourceManager.list().iterateAll().iterator();
		if ( !projectIterator.hasNext() )
		{
			IJ.showMessage( "No Google Cloud projects found." );
			return null;
		}
		while ( projectIterator.hasNext() )
			projects.add( projectIterator.next() );

		// add project names as list items
		projectsList = new java.awt.List();
		for ( final Project project : projects )
			projectsList.add( project.getName() );
		projectsList.addItemListener( new ProjectsListener() );

		bucketsList = new java.awt.List();
		bucketsList.addItemListener( new BucketsListener() );

		final Panel listsPanel = new Panel();
		listsPanel.add( projectsList );
		listsPanel.add( bucketsList );

		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
		gd.addMessage( "Select Google Cloud project and bucket:" );
		gd.addComponent( listsPanel );

		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						okButton = gd.getButtons()[ 0 ];
						okButton.setEnabled( false );
					}
				}
			);

		gd.showDialog();
		if ( gd.wasCanceled() )
			return null;

		final String selectedBucketName = buckets.get( bucketsList.getSelectedIndex() ).getName();
		return DataAccessFactory.createBucketUri( DataAccessType.GOOGLE_CLOUD, selectedBucketName ).toString();
	}

	private class ProjectsListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			okButton.setEnabled( false );
			final int selectedProjectIndex = projectsList.getSelectedIndex();
			if ( selectedProjectIndex == -1 )
			{
				bucketsList.removeAll();
				return;
			}

			final String selectedProjectId = projects.get( selectedProjectIndex ).getProjectId();
			final Storage storage = new GoogleCloudStorageClient( selectedProjectId ).create();

			buckets = new ArrayList<>();
			final Iterator< Bucket > bucketIterator = storage.list().iterateAll().iterator();
			while ( bucketIterator.hasNext() )
				buckets.add( bucketIterator.next() );

			bucketsList.removeAll();
			for ( final Bucket bucket : buckets )
				bucketsList.add( bucket.getName() );
		}
	}

	private class BucketsListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			okButton.setEnabled( projectsList.getSelectedIndex() != -1 );
		}
	}
}
