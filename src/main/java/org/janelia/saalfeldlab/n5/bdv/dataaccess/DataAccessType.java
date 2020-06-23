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

import com.amazonaws.services.s3.AmazonS3URI;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;

import java.io.File;
import java.net.URI;

public enum DataAccessType
{
	FILESYSTEM,
	AMAZON_S3,
	GOOGLE_CLOUD;


	public static DataAccessType detectType( final String link )
	{
		// check if it is a valid directory path
		if ( new File( link ).isDirectory() )
			return FILESYSTEM;

		final URI uri;
		try
		{
			uri = URI.create( link );
		}
		catch ( final IllegalArgumentException e )
		{
			// not a valid input
			return null;
		}

		// try parsing as S3 link
		AmazonS3URI s3Uri;
		try
		{
			s3Uri = new AmazonS3URI( uri );
		}
		catch ( final Exception e )
		{
			s3Uri = null;
		}
		if ( s3Uri != null )
			return AMAZON_S3;

		// try parsing as Google Cloud link
		GoogleCloudStorageURI googleCloudUri;
		try
		{
			googleCloudUri = new GoogleCloudStorageURI( uri );
		}
		catch ( final Exception e )
		{
			googleCloudUri = null;
		}
		if ( googleCloudUri != null )
			return GOOGLE_CLOUD;

		// not a valid input
		return null;
	}
}
