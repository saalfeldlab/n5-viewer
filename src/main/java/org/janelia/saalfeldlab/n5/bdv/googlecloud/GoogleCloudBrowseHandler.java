package org.janelia.saalfeldlab.n5.bdv.googlecloud;

import java.awt.Panel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.n5.bdv.BrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory;

import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

public class GoogleCloudBrowseHandler implements BrowseHandler
{
	private GoogleCloudOAuth oauth;
	private List< Project > projects;
	private List< Bucket > buckets;
	private java.awt.List projectsList;
	private java.awt.List bucketsList;

	@Override
	public String select()
	{
		try
		{
			oauth = DataAccessFactory.createGoogleCloudOAuth(
					Arrays.asList(
							GoogleCloudResourceManagerClient.ProjectsScope.READ_ONLY,
							GoogleCloudStorageClient.StorageScope.READ_WRITE
						)
				);
		}
		catch ( final IOException e)
		{
			IJ.handleException( e );
		}

		// query a list of user's projects first
		final ResourceManager resourceManager = new GoogleCloudResourceManagerClient(
				oauth.getAccessToken(),
				oauth.getClientSecrets(),
				oauth.getRefreshToken()
			).create();

		// list projects
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
		bucketsList = new java.awt.List();
		projectsList = new java.awt.List();
		for ( final Project project : projects )
			projectsList.add( project.getName() );
		projectsList.addItemListener( new GoogleCloudProjectListener() );

		final Panel listsPanel = new Panel();
		listsPanel.add( projectsList );
		listsPanel.add( bucketsList );

		final GenericDialogPlus projectsDialog = new GenericDialogPlus( "N5 Viewer" );
		projectsDialog.addMessage( "Select Google Cloud project and bucket:" );
		projectsDialog.addComponent( listsPanel );
		projectsDialog.showDialog();
		if ( projectsDialog.wasCanceled() )
			return null;

		final String selectedBucketName = buckets.get( bucketsList.getSelectedIndex() ).getName();
		return createGoogleCloudBucketUri( selectedBucketName ).toString();
	}

	private static URI createGoogleCloudBucketUri( final String bucketName )
	{
		return URI.create( DataAccessFactory.googleCloudProtocol + "://" + bucketName + "/" );
	}

	private class GoogleCloudProjectListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			final int selectedProjectIndex = projectsList.getSelectedIndex();
			if ( selectedProjectIndex >= projects.size() )
			{
				bucketsList.removeAll();
				return;
			}

			final String selectedProjectId = projects.get( selectedProjectIndex ).getProjectId();

			final Storage storage = new GoogleCloudStorageClient(
					oauth.getAccessToken(),
					oauth.getClientSecrets(),
					oauth.getRefreshToken()
				).create( selectedProjectId );

			buckets = new ArrayList<>();
			final Iterator< Bucket > bucketIterator = storage.list().iterateAll().iterator();
			while ( bucketIterator.hasNext() )
				buckets.add( bucketIterator.next() );

			bucketsList.removeAll();
			for ( final Bucket bucket : buckets )
				bucketsList.add( bucket.getName() );
		}
	}
}
