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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.googlecloud.GoogleCloudBdvSettingsManager;
import org.janelia.saalfeldlab.n5.bdv.googlecloud.GoogleCloudClientBuilder;
import org.janelia.saalfeldlab.n5.bdv.s3.AmazonS3BdvSettingsManager;
import org.janelia.saalfeldlab.n5.bdv.s3.AmazonS3ClientBuilderWithProfileCredentials;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorage;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.cloud.storage.Storage;
import com.google.gson.GsonBuilder;

import bdv.BigDataViewer;

public class DataAccessFactory
{
	public static enum DataAccessType
	{
		FILESYSTEM,
		AMAZON_S3,
		GOOGLE_CLOUD
	}

	public static class DataAccessException extends Exception
	{
		private static final long serialVersionUID = -5034540320996772468L;
	}

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

	public DataAccessFactory( final DataAccessType type ) throws IOException, DataAccessException
	{
		this.type = type;
		switch ( type )
		{
		case FILESYSTEM:
			s3 = null;
			googleCloudStorage = null;
			break;
		case AMAZON_S3:
			s3 = AmazonS3ClientBuilderWithProfileCredentials.create();
			googleCloudStorage = null;
			break;
		case GOOGLE_CLOUD:
			s3 = null;
			googleCloudStorage = GoogleCloudClientBuilder.createStorage();
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

	public N5Reader createN5Reader( final String basePath )
	{
		final GsonBuilder gsonBuilder = N5ExportMetadata.getGsonBuilder();
		switch ( type )
		{
		case FILESYSTEM:
			return N5.openFSReader( basePath, gsonBuilder );
		case AMAZON_S3:
			final AmazonS3URI s3Uri = new AmazonS3URI( basePath );
			if ( s3Uri.getKey() != null && !s3Uri.getKey().isEmpty() )
				throw new IllegalArgumentException( "Object key is not null. Expected bucket name only (as N5 containers are represented by buckets in S3 implementation)" );
			return N5AmazonS3.openS3Reader( s3, s3Uri.getBucket(), gsonBuilder );
		case GOOGLE_CLOUD:
			final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI( basePath );
			if ( googleCloudUri.getKey() != null && !googleCloudUri.getKey().isEmpty() )
				throw new IllegalArgumentException( "Object key is not null. Expected bucket name only (as N5 containers are represented by buckets in Google Cloud implementation)" );
			return N5GoogleCloudStorage.openCloudStorageReader( googleCloudStorage, googleCloudUri.getBucket(), gsonBuilder );
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}

	public BdvSettingsManager createBdvSettingsManager( final BigDataViewer bdv, final String bdvSettingsPath )
	{
		switch ( type )
		{
		case FILESYSTEM:
			return new FSBdvSettingsManager( bdv, bdvSettingsPath );
		case AMAZON_S3:
			return new AmazonS3BdvSettingsManager( s3, bdv, new AmazonS3URI( bdvSettingsPath ) );
		case GOOGLE_CLOUD:
			return new GoogleCloudBdvSettingsManager( googleCloudStorage, bdv, new GoogleCloudStorageURI( bdvSettingsPath ) );
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}
}
