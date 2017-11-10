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
package org.janelia.saalfeldlab.n5.bdv.googlecloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.janelia.saalfeldlab.n5.bdv.BdvSettingsManager;
import org.jdom2.JDOMException;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import bdv.BigDataViewer;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

/**
 *	TODO: locking for safe concurrent access?
 */
public class GoogleCloudBdvSettingsManager extends BdvSettingsManager
{
	private final Storage storage;
	private final BlobId bdvSettingsBlobId;

	public GoogleCloudBdvSettingsManager( final Storage storage, final BigDataViewer bdv, final BlobId bdvSettingsBlobId )
	{
		super( bdv );
		this.storage = storage;
		this.bdvSettingsBlobId = bdvSettingsBlobId;
	}

	@Override
	public synchronized InitBdvSettingsResult initBdvSettings()
	{
		if ( saveSettingsTimer != null )
			throw new RuntimeException( "Settings have already been initialized" );

		final InitBdvSettingsResult result = loadSettings();
		if ( result == InitBdvSettingsResult.LOADED || result == InitBdvSettingsResult.NOT_LOADED )
			setUpSettingsSaving();
		return result;
	}

	@Override
	protected void saveSettingsOnTimer()
	{
		saveSettings();
	}

	@Override
	protected void saveSettingsOnWindowClosing()
	{
		saveSettings();
		saveSettingsTimer.cancel();
		saveSettingsTimer = null;
	}

	private InitBdvSettingsResult loadSettings()
	{
//		TODO: check permissions
		final boolean canWrite = true;

		if ( !canWrite )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addMessage( "You do not have write permissions for saving the viewer settings such as bookmarks, contrast, etc." + System.lineSeparator() + "Would you like to open the dataset anyway (read-only)?" );
			gd.showDialog();
			if ( gd.wasCanceled() )
				return InitBdvSettingsResult.CANCELED;
		}

		final Blob blob = storage.get( bdvSettingsBlobId );
		if ( blob != null && blob.exists() )
		{
			// download to temporary file, then load it in BDV
			Path tempPath = null;
			try
			{
				tempPath = Files.createTempFile( "n5viewer-google-cloud-", ".xml" );
				blob.downloadTo( tempPath );
				bdv.loadSettings( tempPath.toString() );
			}
			catch ( final IOException | JDOMException e )
			{
				IJ.handleException( e );
			}
			finally
			{
				if ( tempPath != null )
					tempPath.toFile().delete();
			}
			return canWrite ? InitBdvSettingsResult.LOADED : InitBdvSettingsResult.LOADED_READ_ONLY;
		}
		else
		{
			return canWrite ? InitBdvSettingsResult.NOT_LOADED : InitBdvSettingsResult.NOT_LOADED_READ_ONLY;
		}
	}

	private void saveSettings()
	{
		// save to temporary file, then upload to Google Cloud
		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-google-cloud-", ".xml" );
			bdv.saveSettings( tempPath.toString() );
			final BlobInfo blobInfo = BlobInfo.newBuilder( bdvSettingsBlobId ).build();
			final byte[] bytes = Files.readAllBytes( tempPath );
			storage.create( blobInfo, bytes );
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
		}
		finally
		{
			if ( tempPath != null )
				tempPath.toFile().delete();
		}
	}
}
