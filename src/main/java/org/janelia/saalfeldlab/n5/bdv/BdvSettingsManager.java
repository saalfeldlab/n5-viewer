package org.janelia.saalfeldlab.n5.bdv;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;

import org.jdom2.JDOMException;

import bdv.BigDataViewer;
import ij.IJ;

public class BdvSettingsManager
{
	private static final int SAVE_INTERVAL = 5 * 60 * 1000; // save the settings every 5 min

	private final BigDataViewer bdv;
	private final String bdvSettingsFilepath;

	private Timer timer;

	public BdvSettingsManager( final BigDataViewer bdv, final String bdvSettingsFilepath )
	{
		this.bdv = bdv;
		this.bdvSettingsFilepath = bdvSettingsFilepath;
	}

	public boolean initBdvSettings()
	{
		if ( timer != null )
			throw new RuntimeException( "Settings have already been initialized" );

		boolean bdvSettingsLoaded = false;
		if ( Files.exists( Paths.get( bdvSettingsFilepath ) ) )
			bdvSettingsLoaded = loadSettings();

		timer = new Timer();
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run()
				{
					saveSettings();
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
					timer.cancel();
					saveSettings();
					timer = null;
				}
			}
		);

		// add all existing listeners back, after the custom listener has been added
		for ( final WindowListener bdvWindowListener : bdvWindowListeners )
			bdv.getViewerFrame().addWindowListener( bdvWindowListener );

		return bdvSettingsLoaded;
	}

	private boolean loadSettings()
	{
		try
		{
			bdv.loadSettings( bdvSettingsFilepath );
			return true;
		}
		catch ( final IOException | JDOMException e )
		{
			IJ.handleException( e );
			return false;
		}
	}

	private boolean saveSettings()
	{
		try
		{
			bdv.saveSettings( bdvSettingsFilepath );

			final File bdvSettingsFile = new File( bdvSettingsFilepath );
			bdvSettingsFile.setReadable( true, false );
			bdvSettingsFile.setWritable( true, false );

			return true;
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
			return false;
		}
	}
}
