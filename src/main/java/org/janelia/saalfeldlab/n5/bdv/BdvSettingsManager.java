package org.janelia.saalfeldlab.n5.bdv;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Timer;
import java.util.TimerTask;

import org.jdom2.JDOMException;

import bdv.BigDataViewer;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

public class BdvSettingsManager
{
	public static enum InitBdvSettingsResult
	{
		LOADED,
		LOADED_READ_ONLY,
		NOT_LOADED,
		NOT_LOADED_READ_ONLY,
		CANCELED
	}

	private static class FileLockedException extends Exception
	{
		private static final long serialVersionUID = 4947244760247759947L;
	}

	private static final int SAVE_INTERVAL = 5 * 60 * 1000; // save the settings every 5 min

	private final BigDataViewer bdv;
	private final String bdvSettingsFilepath;

	private Timer timer;

	private FileChannel fileChannel;
	private FileLock fileLock;

	public BdvSettingsManager( final BigDataViewer bdv, final String bdvSettingsFilepath )
	{
		this.bdv = bdv;
		this.bdvSettingsFilepath = bdvSettingsFilepath;
	}

	public synchronized InitBdvSettingsResult initBdvSettings()
	{
		if ( timer != null )
			throw new RuntimeException( "Settings have already been initialized" );

		final InitBdvSettingsResult result = loadSettings();
		if ( result == InitBdvSettingsResult.CANCELED )
			return result;

		if ( fileLock != null )
			setUpSettingsSaving();

		return result;
	}

	private void setUpSettingsSaving()
	{
		timer = new Timer();
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run()
				{
					saveSettingsLocking();
				}
			},
			SAVE_INTERVAL,
			SAVE_INTERVAL
		);

		// save the settings on window closing
		// workaround to make the custom window listener for saving BDV settings get called before the default listener which deletes all sources
		final WindowListener[] bdvWindowListeners = bdv.getViewerFrame().getWindowListeners();
		for ( final WindowListener bdvWindowListener : bdvWindowListeners )
			bdv.getViewerFrame().removeWindowListener( bdvWindowListener );

		bdv.getViewerFrame().addWindowListener(
			new WindowAdapter()
			{
				@Override
				public void windowClosing( final WindowEvent event )
				{
					saveSettingsLocking();

					// release the file lock
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
						fileLock = null;
					}

					// stop the timer
					timer.cancel();
					timer = null;
				}
			}
		);

		// add all existing listeners back, after the custom listener has been added
		for ( final WindowListener bdvWindowListener : bdvWindowListeners )
			bdv.getViewerFrame().addWindowListener( bdvWindowListener );
	}

	private InitBdvSettingsResult loadSettings()
	{
		try
		{
			return loadSettingsLocking() ? InitBdvSettingsResult.LOADED : InitBdvSettingsResult.NOT_LOADED;
		}
		catch ( final FileLockedException e )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
			gd.addMessage( "Someone else is currently browsing this dataset." + System.lineSeparator() + "Would you like to open it anyway (read-only)?" );
			gd.showDialog();
			if ( gd.wasCanceled() )
				return InitBdvSettingsResult.CANCELED;

			return loadSettingsNonLocking() ? InitBdvSettingsResult.LOADED_READ_ONLY : InitBdvSettingsResult.NOT_LOADED_READ_ONLY;
		}
	}

	private boolean loadSettingsLocking() throws FileLockedException
	{
		// open file channel
		final boolean openExisting = Files.exists( Paths.get( bdvSettingsFilepath ) );
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
		catch ( final OverlappingFileLockException e )
		{
			fileLock = null;
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
			throw new FileLockedException();
		}

		// set permissions to grant everyone access to the dataset
		final File bdvSettingsFile = new File( bdvSettingsFilepath );
		bdvSettingsFile.setReadable( true, false );
		bdvSettingsFile.setWritable( true, false );

		// lock has been acquired, load the settings if they exist
		if ( !openExisting )
			return false;

		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-", null );
			Files.copy( Channels.newInputStream( fileChannel ), tempPath, StandardCopyOption.REPLACE_EXISTING );
			fileChannel.position( 0 );
			bdv.loadSettings( tempPath.toString() );
			Files.delete( tempPath );
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
			if ( tempPath != null )
				tempPath.toFile().delete();
			return false;
		}
	}

	private boolean loadSettingsNonLocking()
	{
		Path tempPath = null;
		try ( final FileChannel fileChannel = FileChannel.open( Paths.get( bdvSettingsFilepath ), StandardOpenOption.READ ) )
		{
			tempPath = Files.createTempFile( "n5viewer-", null );
			Files.copy( Channels.newInputStream( fileChannel ), tempPath, StandardCopyOption.REPLACE_EXISTING );
			bdv.loadSettings( tempPath.toString() );
			Files.delete( tempPath );
			return true;
		}
		catch ( final IOException | JDOMException e )
		{
			IJ.handleException( e );
			if ( tempPath != null )
				tempPath.toFile().delete();
			return false;
		}
	}

	private boolean saveSettingsLocking()
	{
		Path tempPath = null;
		try
		{
			tempPath = Files.createTempFile( "n5viewer-", null );
			bdv.saveSettings( tempPath.toString() );
			Files.copy( tempPath, Channels.newOutputStream( fileChannel ) );
			fileChannel.position( 0 );
			Files.delete( tempPath );
			return true;
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
			if ( tempPath != null )
				tempPath.toFile().delete();
			return false;
		}
	}
}
