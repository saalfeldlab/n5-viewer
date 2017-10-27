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

import java.net.URI;
import java.nio.file.Paths;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.gson.GsonBuilder;

import bdv.BigDataViewer;

public class DataAccessFactory
{
	public enum DataAccessType
	{
		FILESYSTEM,
		AMAZON_S3
	}

	private static final String localFileProtocol = "file";
	private static final String s3Protocol = "s3";

	private final DataAccessType type;
	private final AmazonS3 s3;

	public static DataAccessType getTypeByPath( final String path )
	{
		final URI uri = URI.create( path );

		if ( uri.getScheme() == null )
			return DataAccessType.FILESYSTEM;

		switch ( uri.getScheme() )
		{
		case localFileProtocol:
			return DataAccessType.FILESYSTEM;
		case s3Protocol:
			return DataAccessType.AMAZON_S3;
		default:
			throw new NotImplementedException( "Factory for protocol " + uri.getScheme() + " is not implemented" );
		}
	}

	public DataAccessFactory( final DataAccessType type )
	{
		this.type = type;
		switch ( type )
		{
		case FILESYSTEM:
			s3 = null;
			break;
		case AMAZON_S3:
			s3 = AmazonS3ClientBuilder.standard().build();
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
		default:
			throw new NotImplementedException( "Factory for type " + type + " is not implemented" );
		}
	}
}
