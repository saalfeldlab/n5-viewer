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

import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.BdvSettingsManager.InitBdvSettingsResult;
import org.janelia.saalfeldlab.n5.bdv.DatasetSelectorDialog.Selection;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.export.ProgressWriterConsole;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvOptions;
import bdv.util.Prefs;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerFrame;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;

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
		new N5Viewer().run( "" );
	}

	@Override
	public void run( final String args )
	{
		final Selection selection = new DatasetSelectorDialog().run();
		if ( selection == null )
			return;

		try
		{
			exec( selection.n5Path, selection.storageType, selection.readonly );
		}
		catch ( final IOException e )
		{
			IJ.handleException( e );
		}
	}

	final public static < T extends NumericType< T > & NativeType< T >, V extends Volatile< T > & NumericType< V > > void exec(
			final String n5Path,
			final DataAccessType storageType,
			final boolean readonly ) throws IOException
	{
		final DataAccessFactory dataAccessFactory;
		try
		{
			dataAccessFactory = new DataAccessFactory( storageType );
		}
		catch ( final DataAccessException e )
		{
			return;
		}

		final N5Reader n5 = dataAccessFactory.createN5Reader( n5Path );
		final N5ExportMetadataReader metadata = N5ExportMetadata.openForReading( n5 );

		final int numChannels = metadata.getNumChannels();
		if ( numChannels <= 0 )
		{
			IJ.error( "No channels found" );
			return;
		}

		final String displayName = metadata.getName() != null ? metadata.getName() : "";
		final int numTimepoints = 1;
		Prefs.showScaleBar( true );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sourcesAndConverters = new ArrayList<>();
		for ( int c = 0; c < numChannels; ++c )
		{
			final Source< V > volatileSource = N5MultiscaleSource.getVolatileSource( n5, c, displayName, sharedQueue );
			final V volatileType = volatileSource.getType();
			addSourceToListsGenericType( volatileSource, c + 1, numTimepoints, volatileType, converterSetups, sourcesAndConverters );
		}

		final BigDataViewer bdv = new BigDataViewer(
				converterSetups,
				sourcesAndConverters,
				null,
				numTimepoints,
				new CacheControl.CacheControls(),
				"N5 Viewer",
				new ProgressWriterConsole(),
				BdvOptions.options().values.getViewerOptions()
			);

		final String bdvSettingsPath = dataAccessFactory.combinePaths( n5Path, "bdv-settings.xml" );
		final BdvSettingsManager bdvSettingsManager = dataAccessFactory.createBdvSettingsManager( bdv, bdvSettingsPath );
		final InitBdvSettingsResult settingsLoadResult = bdvSettingsManager.initBdvSettings( readonly );

		if ( settingsLoadResult == InitBdvSettingsResult.CANCELED )
		{
			final ViewerFrame frame = bdv.getViewerFrame();
			frame.dispatchEvent( new WindowEvent( frame, WindowEvent.WINDOW_CLOSING ) );
			return;
		}

		if ( settingsLoadResult == InitBdvSettingsResult.LOADED_READ_ONLY || settingsLoadResult == InitBdvSettingsResult.NOT_LOADED_READ_ONLY )
			bdv.getViewerFrame().setTitle( "N5 Viewer (read-only)" );

		// set default display settings if BDV settings file does not exist or cannot be loaded
		if ( settingsLoadResult == InitBdvSettingsResult.NOT_LOADED || settingsLoadResult == InitBdvSettingsResult.NOT_LOADED_READ_ONLY )
		{
			final ARGBType[] colors = ColorGenerator.getColors( numChannels );
			final Pair< Double, Double > defaultDisplayRange = new ValuePair<>( 50., 150. );

			for ( int i = 0; i < bdv.getSetupAssignments().getConverterSetups().size(); ++i )
			{
				final ConverterSetup converterSetup = bdv.getSetupAssignments().getConverterSetups().get( i );
				converterSetup.setColor( colors[ i ] );
				converterSetup.setDisplayRange( defaultDisplayRange.getA(), defaultDisplayRange.getB() );

				final MinMaxGroup minMaxGroup = bdv.getSetupAssignments().getMinMaxGroup( converterSetup );
				minMaxGroup.getMinBoundedValue().setCurrentValue( defaultDisplayRange.getA() );
				minMaxGroup.getMaxBoundedValue().setCurrentValue( defaultDisplayRange.getB() );
			}

			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );
			bdv.getViewerFrame().repaint();

			if ( settingsLoadResult == InitBdvSettingsResult.NOT_LOADED )
				bdvSettingsManager.saveSettingsOnTimer();
		}

		InitializeViewerState.initTransform( bdv.getViewer() );
		bdv.getViewerFrame().setVisible( true );

		final List< Source< T > > sources = new ArrayList<>();
		for ( int c = 0; c < numChannels; ++c )
			sources.add( N5MultiscaleSource.getSource( n5, c, displayName ) );
		initCropController( bdv, sources );
	}

	private static < T extends NumericType< T > & NativeType< T > > void initCropController( final BigDataViewer bdv, final List< Source< T > > sources )
	{
		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();
		final InputTriggerConfig config = new InputTriggerConfig();

		final CropController< T > cropController = new CropController<>(
					bdv.getViewer(),
					sources,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config );

		bindings.addBehaviourMap( "crop", cropController.getBehaviourMap() );
		bindings.addInputTriggerMap( "crop", cropController.getInputTriggerMap() );
	}


	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param numTimepoints
	 *            the number of timepoints of the source.
	 * @param type
	 *            instance of the {@code img} type.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static < T > void addSourceToListsGenericType(
			final Source< T > source,
			final int setupId,
			final int numTimepoints,
			final T type,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ? > > sources )
	{
		if ( type instanceof RealType )
			addSourceToListsRealType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else if ( type instanceof ARGBType )
			addSourceToListsARGBType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else if ( type instanceof VolatileARGBType )
			addSourceToListsVolatileARGBType( ( Source ) source, setupId, ( List ) converterSetups, ( List ) sources );
		else
			throw new IllegalArgumentException( "Unknown source type. Expected RealType, ARGBType, or VolatileARGBType" );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link RealARGBColorConverter} and into
	 * a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static < T extends RealType< T > > void addSourceToListsRealType(
			final Source< T > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final T type = Util.getTypeFromInterval( source.getSource( 0, 0 ) );
		final double typeMin = Math.max( 0, Math.min( type.getMinValue(), 65535 ) );
		final double typeMax = Math.max( 0, Math.min( type.getMaxValue(), 65535 ) );
		final RealARGBColorConverter< T > converter = RealARGBColorConverter.create( source.getType(), typeMin, typeMax );
		converter.setColor( new ARGBType( 0xffffffff ) );

		final TransformedSource< T > ts = new TransformedSource<>( source );
		final SourceAndConverter< T > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link ScaledARGBConverter.ARGB} and
	 * into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static void addSourceToListsARGBType(
			final Source< ARGBType > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ARGBType > > sources )
	{
		final TransformedSource< ARGBType > ts = new TransformedSource<>( source );
		final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
		final SourceAndConverter< ARGBType > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with a {@link ScaledARGBConverter.ARGB} and
	 * into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param setupId
	 *            id of the new source for use in {@link SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static void addSourceToListsVolatileARGBType(
			final Source< VolatileARGBType > source,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< VolatileARGBType > > sources )
	{
		final TransformedSource< VolatileARGBType > ts = new TransformedSource<>( source );
		final ScaledARGBConverter.VolatileARGB converter = new ScaledARGBConverter.VolatileARGB( 0, 255 );
		final SourceAndConverter< VolatileARGBType > soc = new SourceAndConverter<>( ts, converter );

		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( setupId, converter );

		converterSetups.add( setup );
		sources.add( soc );
	}
}
