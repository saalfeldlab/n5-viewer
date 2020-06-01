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
package org.janelia.saalfeldlab.n5.bdv.dataaccess;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.cloud.storage.Storage;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadata;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.googlecloud.GoogleCloudClientBuilderWithDefaultCredentials;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.s3.AmazonS3ClientBuilderWithDefaultCredentials;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

public class DataAccessFactory
{
	private static final String localFileProtocol = "file";
	private static final String s3Protocol = "s3";
	private static final String googleCloudProtocol = "gs";

	private final DataAccessType type;
	private final AmazonS3 s3;
	private final Storage googleCloudStorage;

	public static DataAccessType getTypeByUri( final URI uri )
	{
		switch ( uri.getScheme().toLowerCase() )
		{
		case localFileProtocol:
			return DataAccessType.FILESYSTEM;
		case s3Protocol:
			return DataAccessType.AMAZON_S3;
		case googleCloudProtocol:
			return DataAccessType.GOOGLE_CLOUD;
		default:
			throw new NotImplementedException( "Factory for protocol " + uri.getScheme() + " is not implemented" );
		}
	}

	public static URI createBucketUri( final DataAccessType type, final String bucketName )
	{
		final String protocol;
		switch ( type )
		{
		case AMAZON_S3:
			protocol = s3Protocol;
			break;
		case GOOGLE_CLOUD:
			protocol = googleCloudProtocol;
			break;
		case FILESYSTEM:
			throw new IllegalArgumentException( "Not supported for filesystem storage" );
		default:
			throw new NotImplementedException( "Not implemented for type " + type );
		}
		return URI.create( protocol + "://" + bucketName + "/" );
	}

	public DataAccessFactory( final DataAccessType type ) throws DataAccessException
	{
		this.type = type;
		switch ( type )
		{
		case FILESYSTEM:
			s3 = null;
			googleCloudStorage = null;
			break;
		case AMAZON_S3:
			s3 = AmazonS3ClientBuilderWithDefaultCredentials.create();
			googleCloudStorage = null;
			break;
		case GOOGLE_CLOUD:
			s3 = null;
			googleCloudStorage = GoogleCloudClientBuilderWithDefaultCredentials.createStorage();
			break;
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}

	public String combinePaths( final String basePath, final String relativePath )
	{
		switch ( type )
		{
		case FILESYSTEM:
			return Paths.get( basePath, relativePath ).toString();
		case AMAZON_S3:
		case GOOGLE_CLOUD:
			return URI.create( basePath.endsWith( "/" ) || relativePath.startsWith( "/" ) ? basePath : basePath + "/" ).resolve( relativePath ).toString();
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}

	public N5Reader createN5Reader( final String basePath ) throws IOException
	{
		final GsonBuilder gsonBuilder = N5ExportMetadata.getGsonBuilder();
		switch ( type )
		{
		case FILESYSTEM:
			return new N5FSReader( basePath, gsonBuilder );
		case AMAZON_S3:
			final AmazonS3URI s3Uri = new AmazonS3URI( basePath );
			if ( s3Uri.getKey() != null && !s3Uri.getKey().isEmpty() )
				throw new IllegalArgumentException( "Object key is not null. Expected bucket name only (as N5 containers are represented by buckets in S3 implementation)" );
			return new N5AmazonS3Reader( s3, s3Uri.getBucket(), gsonBuilder );
		case GOOGLE_CLOUD:
			final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI( basePath );
			if ( googleCloudUri.getKey() != null && !googleCloudUri.getKey().isEmpty() )
				throw new IllegalArgumentException( "Object key is not null. Expected bucket name only (as N5 containers are represented by buckets in Google Cloud implementation)" );
			return new N5GoogleCloudStorageReader( googleCloudStorage, googleCloudUri.getBucket(), gsonBuilder );
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}
}
