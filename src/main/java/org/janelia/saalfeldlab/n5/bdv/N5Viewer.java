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
import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.BdvSettingsManager.InitBdvSettingsResult;
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
	final public static void main( final String... args ) throws IOException
	{
		new ImageJ();
		exec( args[ 0 ] );
	}

	@Override
	public void run( final String args )
	{
		final String n5Path = new DatasetSelectorDialog().run();
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
		final DataAccessFactory dataAccessFactory = new DataAccessFactory( DataAccessFactory.getTypeByPath( n5Path ) );

		final N5Reader n5 = dataAccessFactory.createN5Reader( n5Path );
		final N5ExportMetadataReader metadata = N5ExportMetadata.openForReading( n5 );

		final int numChannels = metadata.getNumChannels();
		if ( numChannels <= 0 )
		{
			IJ.error( "No channels found" );
			return;
		}

		final String displayName = metadata.getName() != null ? metadata.getName() : "";

		final BdvOptions bdvOptions = BdvOptions.options();
		bdvOptions.frameTitle( "N5 Viewer" );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final List< Source< T > > sources = new ArrayList<>();
		final List< BdvStackSource< V > > stackSources = new ArrayList<>();

		for ( int c = 0; c < numChannels; ++c )
		{
			final Source< T > source = N5Source.getSource( n5, c, displayName );
			final Source< V > volatileSource = N5Source.getVolatileSource( n5, c, displayName, sharedQueue );

			// show in BDV
			final BdvStackSource< V > stackSource = BdvFunctions.show( volatileSource, bdvOptions );

			sources.add( source );
			stackSources.add( stackSource );

			// reuse BDV handle
			bdvOptions.addTo( stackSource.getBdvHandle() );
		}

		final BdvHandle bdvHandle = bdvOptions.values.addTo().getBdvHandle();

		final InitBdvSettingsResult settingsLoadResult;
		if ( bdvHandle instanceof BdvHandleFrame )
		{
			final BdvHandleFrame bdvHandleFrame = ( BdvHandleFrame ) bdvHandle;
			final String bdvSettingsPath = dataAccessFactory.combinePaths( n5Path, "bdv-settings.xml" );
			final BdvSettingsManager bdvSettingsManager = dataAccessFactory.createBdvSettingsManager( bdvHandleFrame.getBigDataViewer(), bdvSettingsPath );
			settingsLoadResult = bdvSettingsManager.initBdvSettings();
		}
		else
		{
			throw new RuntimeException( "Cannot get BigDataViewer instance" );
		}

		if ( settingsLoadResult == InitBdvSettingsResult.CANCELED )
		{
			bdvHandle.close();
			return;
		}

		if ( settingsLoadResult == InitBdvSettingsResult.LOADED_READ_ONLY || settingsLoadResult == InitBdvSettingsResult.NOT_LOADED_READ_ONLY )
		{
			final BdvHandleFrame bdvHandleFrame = ( BdvHandleFrame ) bdvHandle;
			bdvHandleFrame.getBigDataViewer().getViewerFrame().setTitle( "N5 Viewer (read-only)" );
		}

		if ( settingsLoadResult == InitBdvSettingsResult.NOT_LOADED || settingsLoadResult == InitBdvSettingsResult.NOT_LOADED_READ_ONLY )
		{
			// set default display settings if BDV settings files does not exist cannot be loaded
			final ARGBType[] colors = ColorGenerator.getColors( numChannels );
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
}
