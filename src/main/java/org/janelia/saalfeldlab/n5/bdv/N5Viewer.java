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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.jdom2.JDOMException;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;

/**
 * {@link BigDataViewer}-based application for exploring {@link N5} datasets that conform to the {@link N5ExportMetadata} format (multichannel, multiscale).
 * Takes a root path to an N5 container as a command line argument or via Fiji's Plugins &gt; BigDataViewer &gt; N5 Viewer.
 *
 * @author Igor Pisarev
 */

public class N5Viewer implements PlugIn
{
	protected static String n5Path = "";

	final public static void main( final String... args ) throws IOException
	{
		new ImageJ();
		exec( args[ 0 ] );
	}

	@Override
	public void run( final String args )
	{
		n5Path = new DatasetSelectorDialog().run();
		if ( n5Path == null )
			return;

		try
		{
			exec( n5Path );
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
		}
	}

	final public static < T extends NumericType< T > & NativeType< T >, V extends Volatile< T > & NumericType< V > > void exec( final String n5Path ) throws IOException
	{
		final BdvOptions bdvOptions = BdvOptions.options();
		bdvOptions.frameTitle( "N5 Viewer" );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final N5Reader n5 = N5.openFSReader( n5Path );
		final N5ExportMetadata metadata = new N5ExportMetadata( n5Path );
		final String displayName = metadata.getName() != null ? metadata.getName() : Paths.get( n5Path ).getFileName().toString();

		final List< Source< T > > sources = new ArrayList<>();
		final List< BdvStackSource< V > > stackSources = new ArrayList<>();

		for ( int c = 0; c < metadata.getNumChannels(); ++c )
		{
			final Source< T > source = N5Source.getSource( n5, metadata, c, displayName );
			final Source< V > volatileSource = N5Source.getVolatileSource( n5, metadata, c, displayName, sharedQueue );

			// show in BDV
			final BdvStackSource< V > stackSource = BdvFunctions.show( volatileSource, bdvOptions );

			sources.add( source );
			stackSources.add( stackSource );

			// reuse BDV handle
			bdvOptions.addTo( stackSource.getBdvHandle() );
		}

		final BdvHandle bdvHandle = bdvOptions.values.addTo().getBdvHandle();

		final boolean bdvSettingsLoaded;

		// load existing BDV settings and set up listeners to save them on close
		if ( bdvHandle instanceof BdvHandleFrame )
		{
			final BdvHandleFrame bdvHandleFrame = ( BdvHandleFrame ) bdvHandle;
			final String bdvSettingsFilepath = Paths.get( n5Path, "bdv-settings.xml" ).toString();
			bdvSettingsLoaded = initBdvSettings( bdvHandleFrame.getBigDataViewer(), bdvSettingsFilepath );
		}
		else
		{
			bdvSettingsLoaded = false;
		}

		if ( !bdvSettingsLoaded )
		{
			// set default display settings if BDV settings files does not exist cannot be loaded
			final ARGBType[] colors = getColors( metadata.getNumChannels() );
			for ( int i = 0; i < stackSources.size(); ++i )
			{
				final BdvStackSource< V > stackSource = stackSources.get( i );
				stackSource.setColor( colors[ i ] );
				// TODO: estimate the appropriate display range from the displayed data
				stackSource.setDisplayRange( 100, 1000 );
			}
		}

		initCropController( bdvHandle, sources );
	}

	private static boolean initBdvSettings( final BigDataViewer bdv, final String bdvSettingsFilepath )
	{
		boolean bdvSettingsLoaded = false;
		if ( Files.exists( Paths.get( bdvSettingsFilepath ) ) )
			bdvSettingsLoaded = loadSettings( bdv, bdvSettingsFilepath );

		// save the settings every 5 min
		final int settingsSaveInterval = 5 * 60 * 1000;
		final Timer timer = new Timer();
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run()
				{
					saveSettings( bdv, bdvSettingsFilepath );
				}
			},
			settingsSaveInterval,
			settingsSaveInterval
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
					saveSettings( bdv, bdvSettingsFilepath );
				}
			}
		);

		// add all existing listeners back, after the custom listener has been added
		for ( final WindowListener bdvWindowListener : bdvWindowListeners )
			bdv.getViewerFrame().addWindowListener( bdvWindowListener );

		return bdvSettingsLoaded;
	}

	private static boolean loadSettings( final BigDataViewer bdv, final String path )
	{
		try
		{
			bdv.loadSettings( path );
			return true;
		}
		catch ( final IOException | JDOMException e )
		{
			IJ.handleException( e );
			return false;
		}
	}

	private static boolean saveSettings( final BigDataViewer bdv, final String path )
	{
		try
		{
			bdv.saveSettings( path );
			Paths.get( path ).toFile().setReadable( true, false );
			Paths.get( path ).toFile().setWritable( true, false );
			return true;
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
			return false;
		}
	}

	private static < T extends NumericType< T > & NativeType< T > > void initCropController( final BdvHandle bdvHandle, final List< Source< T > > sources )
	{
		final TriggerBehaviourBindings bindings = bdvHandle.getTriggerbindings();
		final InputTriggerConfig config = new InputTriggerConfig();

		final CropController< T > cropController = new CropController<>(
					bdvHandle.getViewerPanel(),
					sources,
					config,
					bdvHandle.getKeybindings(),
					config );

		bindings.addBehaviourMap( "crop", cropController.getBehaviourMap() );
		bindings.addInputTriggerMap( "crop", cropController.getInputTriggerMap() );
	}

	private static ARGBType[] getColors( final int numChannels )
	{
		assert numChannels >= 0;
		if ( numChannels <= 0 )
			return new ARGBType[ 0 ];
		else if ( numChannels == 1 )
			return new ARGBType[] { new ARGBType( 0xffffffff ) };

		final int[] predefinedColors = new int[] {
				ARGBType.rgba( 0xff, 0, 0xff, 0xff ),
				ARGBType.rgba( 0, 0xff, 0, 0xff ),
				ARGBType.rgba( 0, 0, 0xff, 0xff ),
				ARGBType.rgba( 0xff, 0, 0, 0xff ),
				ARGBType.rgba( 0xff, 0xff, 0, 0xff ),
				ARGBType.rgba( 0, 0xff, 0xff, 0xff ),
		};

		final ARGBType[] colors = new ARGBType[ numChannels ];
		Random rnd = null;

		for ( int c = 0; c < numChannels; ++c )
		{
			if ( c < predefinedColors.length )
			{
				colors[ c ] = new ARGBType( predefinedColors[ c ] );
			}
			else
			{
				if ( rnd == null )
					rnd = new Random();

				colors[ c ] = new ARGBType( ARGBType.rgba( rnd.nextInt( 1 << 7) << 1, rnd.nextInt( 1 << 7) << 1, rnd.nextInt( 1 << 7) << 1, 0xff ) );
			}
		}

		return colors;
	}
}
