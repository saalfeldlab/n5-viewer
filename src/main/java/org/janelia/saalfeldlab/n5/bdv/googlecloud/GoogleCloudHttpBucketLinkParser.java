package org.janelia.saalfeldlab.n5.bdv.googlecloud;

import java.net.URI;

public class GoogleCloudHttpBucketLinkParser
{
	public static final class NotGoogleCloudLinkException extends Exception
	{
		private static final long serialVersionUID = 5623195321060543259L;
	}

	public static final class NotBucketLinkException extends Exception
	{
		private static final long serialVersionUID = 3550665474025420988L;
	}

	private static final String googleCloudHost = "googleapis.com";
	private static final String storagePathPrefix = "/storage/v1/b/";

	public static String parseBucketName( final URI uri ) throws NotGoogleCloudLinkException, NotBucketLinkException
	{
		if ( !uri.getScheme().equalsIgnoreCase( "http" ) && !uri.getScheme().equalsIgnoreCase( "https" ) )
			throw new NotGoogleCloudLinkException();

		if ( !uri.getHost().equalsIgnoreCase( googleCloudHost ) && !uri.getHost().equalsIgnoreCase( "www." + googleCloudHost ) )
			throw new NotGoogleCloudLinkException();

		final String path = uri.getPath();
		if ( !path.toLowerCase().startsWith( storagePathPrefix ) )
			throw new NotGoogleCloudLinkException();

		final String bucket = path.substring( storagePathPrefix.length(), path.endsWith( "/" ) ? path.length() - 1 : path.length() );
		if ( bucket.contains( "/" ) )
			throw new NotBucketLinkException();

		return bucket;
	}
}
