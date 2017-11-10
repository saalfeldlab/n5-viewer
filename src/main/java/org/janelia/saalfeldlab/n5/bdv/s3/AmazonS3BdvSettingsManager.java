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
package org.janelia.saalfeldlab.n5.bdv.s3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.janelia.saalfeldlab.n5.bdv.BdvSettingsManager;
import org.janelia.saalfeldlab.n5.bdv.BdvSettingsManager.InitBdvSettingsResult;
import org.jdom2.JDOMException;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;

import bdv.BigDataViewer;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

/**
 *	TODO: locking for safe concurrent access?
 */
public class AmazonS3BdvSettingsManager extends BdvSettingsManager
{
	private final AmazonS3 s3;
	private final AmazonS3URI bdvSettingsS3Uri;
	private final TransferManager s3TransferManager;

	public AmazonS3BdvSettingsManager( final AmazonS3 s3, final BigDataViewer bdv, final AmazonS3URI bdvSettingsS3Uri )
	{
		super( bdv );
		this.s3 = s3;
		this.bdvSettingsS3Uri = bdvSettingsS3Uri;
		s3TransferManager = TransferManagerBuilder.standard().withS3Client( s3 ).build();
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
		boolean canWrite = false;
		final AccessControlList permissions = s3.getBucketAcl( bdvSettingsS3Uri.getBucket() );
		for ( final Grant grant : permissions.getGrantsAsList() )
			if ( grant.getPermission().equals( Permission.FullControl ) || grant.getPermission().equals( Permission.Write ) )
				canWrite = true;

		if ( !canWrite )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addMessage( "You do not have write permissions for saving the viewer settings such as bookmarks, contrast, etc." + System.lineSeparator() + "Would you like to open the dataset anyway (read-only)?" );
			gd.showDialog();
			if ( gd.wasCanceled() )
				return InitBdvSettingsResult.CANCELED;
		}

		if ( s3.doesObjectExist( bdvSettingsS3Uri.getBucket(), bdvSettingsS3Uri.getKey() ) )
		{
			// download to temporary file, then load it in BDV
			Path tempPath = null;
			try
			{
				tempPath = Files.createTempFile( "n5viewer-s3-", ".xml" );
				final Download s3Download = s3TransferManager.download( bdvSettingsS3Uri.getBucket(), bdvSettingsS3Uri.getKey(), tempPath.toFile() );
				s3Download.waitForCompletion();
				bdv.loadSettings( tempPath.toString() );
			}
			catch ( final IOException | InterruptedException | JDOMException e )
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
		// save to temporary file, then upload to S3
		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-s3-", ".xml" );
			bdv.saveSettings( tempPath.toString() );
			final Upload s3Upload = s3TransferManager.upload( bdvSettingsS3Uri.getBucket(), bdvSettingsS3Uri.getKey(), tempPath.toFile() );
			s3Upload.waitForCompletion();
		}
		catch ( final IOException | InterruptedException e )
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
