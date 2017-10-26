package org.janelia.saalfeldlab.n5.bdv;

import java.net.URI;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3;

import com.amazonaws.services.s3.AmazonS3URI;
import com.google.gson.GsonBuilder;

public abstract class N5ReaderFactory
{
	private static final String localFileProtocol = "file";
	private static final String s3Protocol = "s3";

	public static N5Reader createN5Reader( final URI baseUri, final GsonBuilder gsonBuilder )
	{
		final String protocol = baseUri.getScheme();

		if ( protocol == null || protocol.equals( localFileProtocol ) )
			return N5.openFSReader( baseUri.toString(), gsonBuilder );

		if ( protocol.equals( s3Protocol ) )
		{
			final AmazonS3URI s3Uri = new AmazonS3URI( baseUri );
			if ( s3Uri.getKey() != null && !s3Uri.getKey().isEmpty() )
				throw new IllegalArgumentException( "Object key is not null. Expected bucket name only (as N5 containers are represented by buckets in S3 implementation)" );

			return N5AmazonS3.openS3Reader( s3Uri.getBucket(), gsonBuilder );
		}

		throw new NotImplementedException( "Factory for protocol " + baseUri.getScheme() + " is not implemented" );
	}
}
