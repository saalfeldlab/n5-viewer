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

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Timer;
import java.util.TimerTask;

import bdv.BigDataViewer;

public abstract class BdvSettingsManager
{
	public static enum InitBdvSettingsResult
	{
		LOADED,
		LOADED_READ_ONLY,
		NOT_LOADED,
		NOT_LOADED_READ_ONLY,
		CANCELED
	}

	protected static class AccessException extends Exception
	{
		private static final long serialVersionUID = 3295627776916190862L;

		public static enum Reason
		{
			LOCKED,
			LOCKED_SAME_PROCESS,
			NOT_WRITABLE
		}

		public final Reason reason;

		public AccessException( final Reason reason )
		{
			this.reason = reason;
		}
	}

	protected static final int SAVE_INTERVAL = 5 * 60 * 1000; // save the settings every 5 min

	protected final BigDataViewer bdv;
	protected Timer saveSettingsTimer;

	public BdvSettingsManager( final BigDataViewer bdv )
	{
		this.bdv = bdv;
	}

	public abstract InitBdvSettingsResult initBdvSettings( final boolean readonly );

	protected abstract void saveSettingsOnTimer();
	protected abstract void saveSettingsOnWindowClosing();

	protected void setUpSettingsSaving()
	{
		saveSettingsTimer = new Timer();
		saveSettingsTimer.schedule(
			new TimerTask()
			{
				@Override
				public void run()
				{
					saveSettingsOnTimer();
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
					saveSettingsOnWindowClosing();
				}
			}
		);

		// add all existing listeners back, after the custom listener has been added
		for ( final WindowListener bdvWindowListener : bdvWindowListeners )
			bdv.getViewerFrame().addWindowListener( bdvWindowListener );
	}
}
