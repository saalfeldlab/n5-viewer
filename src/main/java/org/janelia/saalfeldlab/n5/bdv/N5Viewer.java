/*-
 * #%L
 * N5 Viewer
 * %%
 * Copyright (C) 2017 - 2022 Igor Pisarev, Stephan Saalfeld
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.janelia.saalfeldlab.n5.bdv;

import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.janelia.saalfeldlab.control.mcu.MCUBDVControls;
import org.janelia.saalfeldlab.control.mcu.XTouchMiniMCUControlPanel;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.tools.boundingbox.BoxCrop;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalSpatialMetadata;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.tools.InitializeViewerState;
import bdv.tools.boundingbox.BoxSelectionOptions;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.util.Prefs;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerFrame;
import bdv.viewer.ViewerPanel;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import static bdv.BigDataViewer.createConverterToARGB;
import static bdv.BigDataViewer.wrapWithTransformedSource;


/**
 * {@link BigDataViewer}-based application for viewing N5 datasets.
 *
 * @author Igor Pisarev
 * @author John Bogovic
 */
public class N5Viewer {

	private int numTimepoints = 1;

	private boolean is2D = true;

	private final SharedQueue sharedQueue;

	private final BdvHandle bdv;

	public BdvHandle getBdv() {
		return bdv;
	}

	public SplitPanel getBdvSplitPanel() {
		return bdv.getSplitPanel();
	}

	public N5Viewer(final Frame parent, final DataSelection selection) throws IOException {
		this(parent, selection, true);
	}

	/**
	 * Creates a new N5Viewer with the given data sets.
	 * @param parentFrame parent frame, can be null
	 * @param dataSelection data sets to display
	 * @param wantFrame if true, use BdvHandleFrame and display a window. If false, use a BdvHandlePanel and do not display anything.
	 * @throws IOException
	 */
	public < T extends NumericType< T > & NativeType< T >,
					V extends Volatile< T > & NumericType< V >,
					R extends N5Reader >
			N5Viewer(final Frame parentFrame,
					 final DataSelection dataSelection,
					 final boolean wantFrame ) throws IOException
	{
		Prefs.showScaleBar( true );

		this.sharedQueue = new SharedQueue( Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) );

		// TODO: These setups are not used anymore, because BdvFunctions creates its own.
		//       They either need to be deleted from here or integrated somehow.
		final List< ConverterSetup > converterSetups = new ArrayList<>();

		final List< SourceAndConverter< T > > sourcesAndConverters = new ArrayList<>();

		final List<N5Metadata> selected = new ArrayList<>();
		for( final N5Metadata meta : dataSelection.metadata )
		{
			if( meta instanceof N5ViewerMultichannelMetadata )
			{
				final N5ViewerMultichannelMetadata mc = (N5ViewerMultichannelMetadata)meta;
				for( final MultiscaleMetadata<?> m : mc.getChildrenMetadata() )
					selected.add( m );
			}
			else if ( meta instanceof CanonicalMultichannelMetadata )
			{
				final CanonicalMultichannelMetadata mc = (CanonicalMultichannelMetadata)meta;
				for( final N5Metadata m : mc.getChildrenMetadata() )
					selected.add( m );
			}
			else
				selected.add( meta );
		}

		final List<N5Source<T>> sources = new ArrayList<>();
		final List<N5Source<V>> volatileSources = new ArrayList<>();

		buildN5Sources(dataSelection.n5, selected, sharedQueue, converterSetups, sourcesAndConverters, sources, volatileSources);

		BdvHandle bdvHandle = null;

		BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");
		if (is2D) {
			options = options.is2D();
		}

		for (final SourceAndConverter<?> sourcesAndConverter : sourcesAndConverters) {
			if (bdvHandle == null) {
				if (wantFrame) {
					// Create and show a BdvHandleFrame with the first source
					bdvHandle = BdvFunctions.show(sourcesAndConverter, BdvOptions.options()).getBdvHandle();
				}
				else {
					// Create a BdvHandlePanel, but don't show it
					bdvHandle = new BdvHandlePanel(parentFrame, options);
					// Add the first source to it
					BdvFunctions.show(sourcesAndConverter, BdvOptions.options().addTo(bdvHandle));
				}
			}
			else {
				// Subsequent sources are added to the existing handle
				BdvFunctions.show(sourcesAndConverter, BdvOptions.options().addTo(bdvHandle));
			}
		}
		this.bdv = bdvHandle;

		if (bdv != null) {
			final ViewerPanel viewerPanel = bdv.getViewerPanel();
			if (viewerPanel != null) {
				viewerPanel.setNumTimepoints(numTimepoints);
				initCropController( sources );
				// Delay initTransform until the viewer is shown because it needs to have a size.
				viewerPanel.addComponentListener(new ComponentAdapter() {
					boolean needsInit = true;
					@Override
					public void componentShown(final ComponentEvent e) {
						if (needsInit) {
							InitializeViewerState.initTransform(viewerPanel);
							needsInit = false;
						}
					}
				});
			}
		}

		if( bdv instanceof BdvHandleFrame )
		{
			// add crop to menu bar
			final BdvHandleFrame bdvFrame = (BdvHandleFrame)bdv;
			final ViewerFrame viewerFrame = bdvFrame.getBigDataViewer().getViewerFrame();
			final JMenuBar menuBar = viewerFrame.getJMenuBar();
			final ActionMap actionMap = viewerFrame.getKeybindings().getConcatenatedActionMap();

			final JMenu toolsMenu = menuBar.getMenu( 2 );
			final JMenuItem cropItem = new JMenuItem( actionMap.get( "crop" ));
			cropItem.setText( "Extract to ImageJ" );
			toolsMenu.add( cropItem );

			/* create XTouchMini midi controller */
			try {
				final XTouchMiniMCUControlPanel controlPanel = XTouchMiniMCUControlPanel.build();
				new MCUBDVControls(
						bdv.getBdvHandle().getViewerPanel(),
						controlPanel);

				((JFrame)SwingUtilities.getWindowAncestor(bdv.getBdvHandle().getViewerPanel())).addWindowListener(new WindowAdapter(){

				    @Override
					public void windowClosing(final WindowEvent e){
				    	controlPanel.close();
				    }

				});

			} catch (final Exception e) {}
		}
	}

	public < T extends NumericType< T > & NativeType< T >,
				V extends Volatile< T > & NumericType< V >,
				R extends N5Reader >
			void addData( final DataSelection selection ) throws IOException
	{
		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< T > > sourcesAndConverters = new ArrayList<>();

		final List<N5Metadata> selected = new ArrayList<>();
		for( final N5Metadata meta : selection.metadata )
		{
			if( meta instanceof N5ViewerMultichannelMetadata )
			{
				final N5ViewerMultichannelMetadata mc = (N5ViewerMultichannelMetadata)meta;
				selected.addAll(Arrays.asList(mc.getChildrenMetadata()));
			}
			else if ( meta instanceof CanonicalMultichannelMetadata )
			{
				final CanonicalMultichannelMetadata mc = (CanonicalMultichannelMetadata)meta;
				selected.addAll(Arrays.asList(mc.getChildrenMetadata()));
			}
			else
				selected.add( meta );
		}

		final List<N5Source<T>> sources = new ArrayList<>();
		final List<N5Source<V>> volatileSources = new ArrayList<>();

		buildN5Sources(selection.n5, selected, sharedQueue, converterSetups, sourcesAndConverters, sources, volatileSources);


		for (final SourceAndConverter<?> sourcesAndConverter : sourcesAndConverters) {
			BdvFunctions.show(sourcesAndConverter, BdvOptions.options().addTo(bdv));
		}
	}

	public < T extends NumericType< T > & NativeType< T >,
					V extends Volatile< T > & NumericType< V >> void buildN5Sources(
		final N5Reader n5,
		final List<N5Metadata> selectedMetadata,
		final SharedQueue sharedQueue,
		final List< ConverterSetup > converterSetups,
		final List< SourceAndConverter< T > > sourcesAndConverters,
		final List<N5Source<T>> sources,
		final List<N5Source<V>> volatileSources) throws IOException
	{
		final ArrayList<MetadataSource<?>> additionalSources = new ArrayList<>();

		int i;
		for ( i = 0; i < selectedMetadata.size(); ++i )
		{
			String[] datasetsToOpen = null;
			AffineTransform3D[] transforms = null;

			final N5Metadata metadata = selectedMetadata.get( i );
			final String srcName = metadata.getName();
			if (metadata instanceof N5SingleScaleMetadata) {
				final N5SingleScaleMetadata singleScaleDataset = (N5SingleScaleMetadata) metadata;
				final String[] tmpDatasets= new String[]{ singleScaleDataset.getPath() };
				final AffineTransform3D[] tmpTransforms = new AffineTransform3D[]{ singleScaleDataset.spatialTransform3d() };

				final MultiscaleDatasets msd = MultiscaleDatasets.sort( tmpDatasets, tmpTransforms );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5MultiScaleMetadata) {
				final N5MultiScaleMetadata multiScaleDataset = (N5MultiScaleMetadata) metadata;
				datasetsToOpen = multiScaleDataset.getPaths();
				transforms = multiScaleDataset.spatialTransforms3d();
			} else if (metadata instanceof N5CosemMetadata ) {
				final N5CosemMetadata singleScaleCosemDataset = (N5CosemMetadata) metadata;
				datasetsToOpen = new String[]{ singleScaleCosemDataset.getPath() };
				transforms = new AffineTransform3D[]{ singleScaleCosemDataset.spatialTransform3d() };
			} else if (metadata instanceof CanonicalSpatialMetadata ) {
				final CanonicalSpatialMetadata canonicalDataset = (CanonicalSpatialMetadata) metadata;
				datasetsToOpen = new String[]{ canonicalDataset.getPath() };
				transforms = new AffineTransform3D[]{ canonicalDataset.getSpatialTransform().spatialTransform3d() };
			} else if (metadata instanceof N5CosemMultiScaleMetadata ) {
				final N5CosemMultiScaleMetadata multiScaleDataset = (N5CosemMultiScaleMetadata) metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets.sort( multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d() );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof CanonicalMultiscaleMetadata ) {
				final CanonicalMultiscaleMetadata multiScaleDataset = (CanonicalMultiscaleMetadata) metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets.sort( multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d() );
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			}
			else if( metadata instanceof N5DatasetMetadata ) {
				final List<MetadataSource<?>> addTheseSources = MetadataSource.buildMetadataSources(n5, (N5DatasetMetadata)metadata);
				if( addTheseSources != null )
					additionalSources.addAll(addTheseSources);
			}
			else {
				datasetsToOpen = new String[]{ metadata.getPath() };
				transforms = new AffineTransform3D[] { new AffineTransform3D() };
			}

			if( datasetsToOpen == null || datasetsToOpen.length == 0 )
				continue;

			// is2D should be true at the end of this loop if all sources are 2D
			is2D = true;

			@SuppressWarnings( "rawtypes" )
			final RandomAccessibleInterval[] images = new RandomAccessibleInterval[datasetsToOpen.length];
			for ( int s = 0; s < images.length; ++s )
			{
				final CachedCellImg<?, ?> vimg = N5Utils.openVolatile( n5, datasetsToOpen[s] );
				if( vimg.numDimensions() == 2 )
				{
					images[ s ] = Views.addDimension(vimg, 0, 0);
					is2D = is2D && true;
				}
				else
				{
					images[ s ] = vimg;
					is2D = is2D && false;
				}
			}

			final RandomAccessibleInterval[] vimages = new RandomAccessibleInterval[images.length];
			for (int s = 0; s < images.length; ++s) {
				final CacheHints cacheHints = new CacheHints(LoadingStrategy.VOLATILE, 0, true);
				vimages[s] = VolatileViews.wrapAsVolatile(images[s], sharedQueue, cacheHints);
			}
			// TODO: Ideally, the volatile views should use a caching strategy
			//   where blocks are enqueued with reverse resolution level as
			//   priority. However, this would require to predetermine the number
			//   of resolution levels, which would man a lot of duplicated code
			//   for analyzing selectedMetadata. Instead, wait until SharedQueue
			//   supports growing numPriorities, then revisit.
			//   See https://github.com/imglib/imglib2-cache/issues/18.
			//   Probably it should look like this:
//			sharedQueue.ensureNumPriorities(images.length);
//			for (int s = 0; s < images.length; ++s) {
//				final int priority = images.length - 1 - s;
//				final CacheHints cacheHints = new CacheHints(LoadingStrategy.BUDGETED, priority, false);
//				vimages[s] = VolatileViews.wrapAsVolatile(images[s], sharedQueue, cacheHints);
//			}

			@SuppressWarnings("unchecked")
			final T type = (T) Util.getTypeFromInterval(images[0]);
			final N5Source<T> source = new N5Source<>(
					type,
					srcName,
					images,
					transforms);

			@SuppressWarnings("unchecked")
			final V volatileType = (V) VolatileTypeMatcher.getVolatileTypeForType(type);
			final N5Source<V> volatileSource = new N5Source<>(
					volatileType,
					srcName,
					vimages,
					transforms);

			sources.add(source);
			volatileSources.add(volatileSource);

			addSourceToListsGenericType(source, volatileSource, i + 1, converterSetups, sourcesAndConverters);
		}

		for( final MetadataSource src : additionalSources ) {
			if( src.numTimePoints() > numTimepoints )
				numTimepoints = src.numTimePoints();

			addSourceToListsGenericType( src, i + 1, converterSetups, sourcesAndConverters );
		}
	}

	private < T extends NumericType< T > & NativeType< T > > void initCropController( final List< ? extends Source< T > > sources )
	{
		final TriggerBehaviourBindings bindings = bdv.getBdvHandle().getTriggerbindings();

		final InputTriggerConfig config;
		ViewerFrame viewerFrame = null;
		if( bdv instanceof BdvHandleFrame )
		{
			final BdvHandleFrame bdvFrame = (BdvHandleFrame)bdv;
			config = bdvFrame.getBigDataViewer().getKeymapManager().getForwardSelectedKeymap().getConfig();
			viewerFrame = bdvFrame.getBigDataViewer().getViewerFrame();
		}
		else
		{
			config = new InputTriggerConfig();
		}

		final Source< T > src = sources.get( 0 );
		final double[] boxMin = new double[ 3 ];
		final double[] boxMax = new double[ 3 ];

		// interval min / max
		final Interval itvl = src.getSource( 0, 0 );
		itvl.realMin( boxMin );
		itvl.realMax( boxMax );

		// world (physical) min / max
		final AffineTransform3D srcXfm = new AffineTransform3D();
		src.getSourceTransform( 0, 0, srcXfm );
		srcXfm.apply( boxMin, boxMin );
		srcXfm.apply( boxMax, boxMax );

		final FinalRealInterval srcItvlWorld = new FinalRealInterval( boxMin, boxMax );
		final BoxCrop cropController = new BoxCrop(
					bdv.getViewerPanel(),
					bdv.getConverterSetups(),
					0,
					config,
					bindings,
					BoxSelectionOptions.options(),
					new AffineTransform3D(),
					srcItvlWorld, srcItvlWorld,
					"crop", "SPACE");

		bindings.addBehaviourMap( "crop", cropController.getBehaviourMap() );
		bindings.addInputTriggerMap( "crop", cropController.getInputTriggerMap() );

		final CropController< T > cropControllerLegacy = new CropController<>(
				bdv.getViewerPanel(),
				sources,
				config,
				bdv.getKeybindings(),
				config );

		bindings.addBehaviourMap( "cropLegacy", cropControllerLegacy.getBehaviourMap() );
		bindings.addInputTriggerMap( "cropLegacy", cropControllerLegacy.getInputTriggerMap() );

		if( viewerFrame != null )
		{
			// set action for crop item in menu bar
			final InputActionBindings inputActionBindings = viewerFrame.getKeybindings();
			final Actions actions = new Actions(config, "bdv");
			actions.install( inputActionBindings, "crop" );
			actions.runnableAction( () -> { cropController.click( 0, 0); },
				"crop", "SPACE" );
		}

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
	 *            id of the new source for use in {@code SetupAssignments}.
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
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		addSourceToListsGenericType( source, null, setupId, converterSetups, sources );
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
	 *            id of the new source for use in {@code SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static < T, V extends Volatile< T > > void addSourceToListsGenericType(
			final Source< T > source,
			final Source< V > volatileSource,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final T type = source.getType();
		if ( type instanceof RealType || type instanceof ARGBType || type instanceof VolatileARGBType )
			addSourceToListsNumericType( ( Source ) source, ( Source ) volatileSource, setupId, converterSetups, ( List ) sources );
		else
			throw new IllegalArgumentException( "Unknown source type. Expected RealType, ARGBType, or VolatileARGBType" );
	}

	/**
	 * Add the given {@code source} to the lists of {@code converterSetups}
	 * (using specified {@code setupId}) and {@code sources}. For this, the
	 * {@code source} is wrapped with an appropriate {@link Converter} to
	 * {@link ARGBType} and into a {@link TransformedSource}.
	 *
	 * @param source
	 *            source to add.
	 * @param volatileSource
	 *            corresponding volatile source.
	 * @param setupId
	 *            id of the new source for use in {@code SetupAssignments}.
	 * @param converterSetups
	 *            list of {@link ConverterSetup}s to which the source should be
	 *            added.
	 * @param sources
	 *            list of {@link SourceAndConverter}s to which the source should
	 *            be added.
	 */
	private static < T extends NumericType< T >, V extends Volatile< T > & NumericType< V > > void addSourceToListsNumericType(
			final Source< T > source,
			final Source< V > volatileSource,
			final int setupId,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		final SourceAndConverter< V > vsoc = ( volatileSource == null )
				? null
				: new SourceAndConverter<>( volatileSource, createConverterToARGB( volatileSource.getType() ) );
		final SourceAndConverter< T > soc = new SourceAndConverter<>( source, createConverterToARGB( source.getType() ), vsoc );
		final SourceAndConverter< T > tsoc = wrapWithTransformedSource( soc );

		converterSetups.add( BigDataViewer.createConverterSetup( tsoc, setupId ) );
		sources.add( tsoc );
	}
}
