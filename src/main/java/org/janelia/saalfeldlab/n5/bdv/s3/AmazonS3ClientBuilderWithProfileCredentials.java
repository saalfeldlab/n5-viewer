package org.janelia.saalfeldlab.n5.bdv.s3;

import org.janelia.saalfeldlab.n5.bdv.DataAccessFactory.DataAccessException;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import ij.IJ;

public class AmazonS3ClientBuilderWithProfileCredentials
{
	private static final String credentialsDocsLink = "http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html";

	public static AmazonS3 create() throws DataAccessException
	{
		try
		{
			return AmazonS3ClientBuilder.standard().withCredentials( new ProfileCredentialsProvider() ).build();
		}
		catch ( final Exception e )
		{
			IJ.error(
					"N5 Viewer",
					"<html>Could not find AWS credentials/region. Please initialize them using one of the methods listed here:<br/>"
							+ "<a href=\"" + credentialsDocsLink + "\">" + credentialsDocsLink + "</a></html>"
				);
			throw new DataAccessException();
		}
	}
}
