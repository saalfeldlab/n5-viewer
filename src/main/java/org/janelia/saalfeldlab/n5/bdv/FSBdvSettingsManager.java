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

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import org.jdom2.JDOMException;

import bdv.BigDataViewer;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

public class FSBdvSettingsManager extends BdvSettingsManager
{
	private static Map< String, FileChannel > lockedFiles = new HashMap<>();

	private final String bdvSettingsFilepath;
	private FileChannel fileChannel;
	private FileLock fileLock;

	public FSBdvSettingsManager( final BigDataViewer bdv, final String bdvSettingsFilepath )
	{
		super( bdv );
		this.bdvSettingsFilepath = bdvSettingsFilepath;
	}

	@Override
	public synchronized InitBdvSettingsResult initBdvSettings( final boolean readonly )
	{
		if ( saveSettingsTimer != null )
			throw new RuntimeException( "Settings have already been initialized" );

		final InitBdvSettingsResult result;
		synchronized ( lockedFiles )
		{
			result = loadSettings( readonly );
			if ( result == InitBdvSettingsResult.LOADED || result == InitBdvSettingsResult.NOT_LOADED )
				lockedFiles.put( bdvSettingsFilepath, fileChannel );
		}

		if ( result == InitBdvSettingsResult.CANCELED )
			return result;

		if ( fileLock != null )
			setUpSettingsSaving();

		return result;
	}

	@Override
	protected void saveSettingsOnTimer()
	{
		saveSettingsLocking();
	}

	@Override
	protected void saveSettingsOnWindowClosing()
	{
		synchronized ( lockedFiles )
		{
			if ( fileChannel != null )
			{
				synchronized ( fileChannel )
				{
					saveSettingsLocking();

					// release the file lock
					try
					{
						fileChannel.close();
					}
					catch ( final IOException e )
					{
						IJ.handleException( e );
					}
					fileChannel = null;
					fileLock = null;
				}
			}

			if ( saveSettingsTimer != null )
			{
				// stop the timer
				saveSettingsTimer.cancel();
				saveSettingsTimer = null;
			}

			lockedFiles.remove( bdvSettingsFilepath );
		}
	}

	private InitBdvSettingsResult loadSettings( final boolean readonly )
	{
		if ( readonly )
			return loadSettingsNonLocking() ? InitBdvSettingsResult.LOADED_READ_ONLY : InitBdvSettingsResult.NOT_LOADED_READ_ONLY;

		try
		{
			return loadSettingsLocking() ? InitBdvSettingsResult.LOADED : InitBdvSettingsResult.NOT_LOADED;
		}
		catch ( final AccessException e )
		{
			final String message;
			switch ( e.reason )
			{
			case LOCKED:
				message = "Someone else is currently browsing this dataset." + System.lineSeparator() + "Would you like to open it anyway (read-only)?";
				break;
			case LOCKED_SAME_PROCESS:
				message = "This dataset is already opened in another window." + System.lineSeparator() + "Would you like to open it anyway (read-only)?";
				break;
			case NOT_WRITABLE:
				message = "You do not have write permissions for saving the viewer settings such as bookmarks, contrast, etc." + System.lineSeparator() + "Would you like to open the dataset anyway (read-only)?";
				break;
			default:
				throw new NotImplementedException( "The file access error " + e.reason + " is not handled properly" );
			}

			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addMessage( message );
			gd.showDialog();
			if ( gd.wasCanceled() )
				return InitBdvSettingsResult.CANCELED;

			return loadSettingsNonLocking() ? InitBdvSettingsResult.LOADED_READ_ONLY : InitBdvSettingsResult.NOT_LOADED_READ_ONLY;
		}
	}

	private boolean loadSettingsLocking() throws AccessException
	{
		// check if already opened in another window
		synchronized ( lockedFiles )
		{
			if ( lockedFiles.containsKey( bdvSettingsFilepath ) )
				throw new AccessException( AccessException.Reason.LOCKED_SAME_PROCESS );
		}

		// check if settings file already exists
		final boolean openExisting = Files.exists( Paths.get( bdvSettingsFilepath ) );

		// check if the path is writable
		if ( ( openExisting && !Files.isWritable( Paths.get( bdvSettingsFilepath ) ) ) || ( !openExisting && !Files.isWritable( Paths.get( bdvSettingsFilepath ).getParent() ) ) )
			throw new AccessException( AccessException.Reason.NOT_WRITABLE );

		// open file channel
		try
		{
			fileChannel = FileChannel.open( Paths.get( bdvSettingsFilepath ), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE );
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
			return false;
		}

		// try to acquire file lock
		try
		{
			fileLock = fileChannel.tryLock();
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
		}

		// if the file is already locked, should wait until the lock is released
		if ( fileLock == null )
		{
			if ( fileChannel != null )
			{
				try
				{
					fileChannel.close();
				}
				catch ( final IOException e )
				{
					IJ.handleException( e );
				}
				fileChannel = null;
			}
			throw new AccessException( AccessException.Reason.LOCKED );
		}

		// set permissions to grant everyone access to the dataset
		final File bdvSettingsFile = new File( bdvSettingsFilepath );
		bdvSettingsFile.setReadable( true, false );
		bdvSettingsFile.setWritable( true, false );

		// lock has been acquired, load the settings if they exist
		if ( !openExisting )
			return false;

		try
		{
			loadSettings( fileChannel );
			return true;
		}
		catch ( final IOException | JDOMException e )
		{
			IJ.handleException( e );
			if ( fileChannel != null )
			{
				try
				{
					fileChannel.close();
				}
				catch ( final IOException e1 )
				{
					IJ.handleException( e1 );
				}
				fileChannel = null;
				fileLock = null;
			}
			return false;
		}
	}

	private boolean loadSettingsNonLocking()
	{
		if ( !Files.exists( Paths.get( bdvSettingsFilepath ) ) )
			return false;

		synchronized ( lockedFiles )
		{
			if ( lockedFiles.containsKey( bdvSettingsFilepath ) )
			{
				final FileChannel fileChannel = lockedFiles.get( bdvSettingsFilepath );
				synchronized ( fileChannel )
				{
					try
					{
						loadSettings( fileChannel );
						return true;
					}
					catch ( final IOException | JDOMException e )
					{
						IJ.handleException( e );
						return false;
					}
				}
			}
			else
			{
				try ( final FileChannel fileChannel = FileChannel.open( Paths.get( bdvSettingsFilepath ), StandardOpenOption.READ ) )
				{
					loadSettings( fileChannel );
					return true;
				}
				catch ( final IOException | JDOMException e )
				{
					IJ.handleException( e );
					return false;
				}
			}
		}
	}

	private boolean saveSettingsLocking()
	{
		synchronized ( fileChannel )
		{
			try
			{
				saveSettings( fileChannel );
				return true;
			}
			catch ( final IOException e )
			{
				IJ.handleException( e );
				return false;
			}
		}
	}

	private void loadSettings( final FileChannel fileChannel ) throws IOException, JDOMException
	{
		// have to use temporary file and then copy its contents to the original file using already opened file channel, otherwise the lock goes away (on unix)
		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-", ".xml" );
			Files.copy( Channels.newInputStream( fileChannel ), tempPath, StandardCopyOption.REPLACE_EXISTING );
			fileChannel.position( 0 );
			bdv.loadSettings( tempPath.toString() );
		}
		finally
		{
			if ( tempPath != null )
				tempPath.toFile().delete();
		}
	}

	private void saveSettings( final FileChannel fileChannel ) throws IOException
	{
		// have to use temporary file and then copy its contents to the original file using already opened file channel, otherwise the lock goes away (on unix)
		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-", ".xml" );
			bdv.saveSettings( tempPath.toString() );
			Files.copy( tempPath, Channels.newOutputStream( fileChannel ) );
			fileChannel.truncate( fileChannel.position() );
			fileChannel.position( 0 );
		}
		finally
		{
			if ( tempPath != null )
				tempPath.toFile().delete();
		}
	}
}
