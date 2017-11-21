package org.janelia.saalfeldlab.n5.bdv.s3;

import java.awt.Button;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import org.janelia.saalfeldlab.n5.bdv.BrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory.DataAccessType;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;

import fiji.util.gui.GenericDialogPlus;

public class S3BrowseHandler implements BrowseHandler
{
	private java.awt.List bucketsList;
	private Button okButton;

	@Override
	public String select()
	{
		final AmazonS3 s3;
		try
		{
			s3 = AmazonS3ClientBuilderWithProfileCredentials.create();
		}
		catch ( final DataAccessException e )
		{
			return null;
		}

		final List< Bucket > buckets = s3.listBuckets();

		bucketsList = new java.awt.List();
		for ( final Bucket bucket : buckets )
			bucketsList.add( bucket.getName() );
		bucketsList.addItemListener( new BucketsListener() );

		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
		gd.addMessage( "Select AWS S3 bucket:" );
		gd.addComponent( bucketsList );

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
		return DataAccessFactory.createBucketUri( DataAccessType.AMAZON_S3, selectedBucketName ).toString();
	}

	private class BucketsListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent event )
		{
			okButton.setEnabled( bucketsList.getSelectedIndex() != -1 );
		}
	}
}
