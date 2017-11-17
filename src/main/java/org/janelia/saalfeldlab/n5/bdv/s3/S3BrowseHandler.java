package org.janelia.saalfeldlab.n5.bdv.s3;

import java.net.URI;
import java.util.List;

import org.janelia.saalfeldlab.n5.bdv.BrowseHandler;
import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

public class S3BrowseHandler implements BrowseHandler
{
	private static final String credentialsDocsLink = "http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html";

	@Override
	public String select()
	{
		final DefaultAWSCredentialsProviderChain provider = new DefaultAWSCredentialsProviderChain();
		try
		{
			final AWSCredentials credentials = provider.getCredentials();
			if ( credentials == null )
				throw new NullPointerException();
		}
		catch ( final Exception e )
		{
			IJ.error(
					"N5 Viewer",
					"<html>Could not find AWS credentials. Please initialize them using one of the methods listed here:<br/>"
							+ "<a href=\"" + credentialsDocsLink + "\">" + credentialsDocsLink + "</a></html>"
				);
			return null;
		}

		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

		final List< Bucket > buckets = s3.listBuckets();
		final java.awt.List bucketsList = new java.awt.List();
		for ( final Bucket bucket : buckets )
			bucketsList.add( bucket.getName() );

		final GenericDialogPlus projectsDialog = new GenericDialogPlus( "N5 Viewer" );
		projectsDialog.addMessage( "Select AWS S3 bucket:" );
		projectsDialog.addComponent( bucketsList );
		projectsDialog.showDialog();
		if ( projectsDialog.wasCanceled() )
			return null;

		final String selectedBucketName = buckets.get( bucketsList.getSelectedIndex() ).getName();
		return createS3BucketUri( selectedBucketName ).toString();
	}

	private static URI createS3BucketUri( final String bucketName )
	{
		return URI.create( DataAccessFactory.s3Protocol + "://" + bucketName + "/" );
	}
}
