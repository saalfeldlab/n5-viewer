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

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.export.ProgressWriterConsole;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.TransformedSource;
import bdv.util.*;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
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
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link BigDataViewer}-based application for browsing N5 datasets.
 * The datasets are expected to be structured according to the {@link N5ExportMetadata} format (multichannel, multiscale).
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
		final DatasetSelectorDialog dialog = new DatasetSelectorDialog();
		dialog.run(selection -> {
			try {
				exec(selection);
			} catch (final IOException e) {
				IJ.handleException(e);
			}
		});
	}

	public static < T extends NumericType< T > & NativeType< T >, V extends Volatile< T > & NumericType< V > > void exec(
			final N5ViewerDataSeleciton selection ) throws IOException
	{
		final DataAccessType storageType = DataAccessType.detectType( selection.n5Path );
		if ( storageType == null )
		{
			IJ.error( "Cannot open the link" );
		}

		final DataAccessFactory dataAccessFactory;
		try
		{
			dataAccessFactory = new DataAccessFactory( storageType );
		}
		catch ( final DataAccessException e )
		{
			return;
		}

		final N5Reader n5 = dataAccessFactory.createN5Reader( selection.n5Path );

		final int numSources = selection.datasets.size();
		final int numTimepoints = 1;
		Prefs.showScaleBar( true );

		final SharedQueue sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sourcesAndConverters = new ArrayList<>();
		for ( int c = 0; c < numSources; ++c )
		{
			final Source< V > volatileSource = N5MultiscaleSource.getVolatileSource( n5, c, "source " + (c + 1), sharedQueue );
			final V volatileType = volatileSource.getType();
			addSourceToListsGenericType( volatileSource, c + 1, numTimepoints, volatileType, converterSetups, sourcesAndConverters );
		}

//		final List< Source< T > > nonVolatileSources = new ArrayList<>();
//		for ( int c = 0; c < numChannels; ++c )
//			nonVolatileSources.add( N5MultiscaleSource.getSource( n5, c, displayName ) );
//
//		final ARGBType[] colors = ColorGenerator.getColors( numChannels );
//		for ( int i = 0; i < numChannels; ++i )
//		{
//			Bounds range = InitializeViewerState.estimateSourceRange( nonVolatileSources.get( i ),0, 0.05, 0.999 );
//			converterSetups.get( i ).setDisplayRange( range.getMinBound(), range.getMaxBound() );
//			converterSetups.get( i ).setColor( colors[ i ] );
//		}

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

		InitializeViewerState.initTransform( bdv.getViewer() );

		bdv.getViewer().state().setDisplayMode( DisplayMode.FUSED );
		bdv.getViewerFrame().setVisible( true );

//		initCropController( bdv, nonVolatileSources );
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
