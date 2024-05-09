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

import static bdv.BigDataViewer.createConverterToARGB;
import static bdv.BigDataViewer.wrapWithTransformedSource;

import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.ActionMap;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.janelia.saalfeldlab.control.mcu.MCUBDVControls;
import org.janelia.saalfeldlab.control.mcu.XTouchMiniMCUControlPanel;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5URI;
import org.janelia.saalfeldlab.n5.bdv.tools.boundingbox.BoxCrop;
import org.janelia.saalfeldlab.n5.ij.N5Importer.N5ViewerReaderFun;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.MetadataSource;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5MetadataUtils;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.GenericMetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisUtils;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.DefaultAxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMultichannelMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalSpatialMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
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
import bdv.util.RandomAccessibleIntervalMipmapSource4D;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerFrame;
import bdv.viewer.ViewerPanel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.lazy.Lazy;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * {@link BigDataViewer}-based application for viewing N5 datasets.
 *
 * @author Igor Pisarev
 * @author John Bogovic
 */
public class N5Viewer {

	private int numTimepoints = 1;

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
	 *
	 * @param <T>
	 *            the image data type
	 * @param <V>
	 *            the image volatile data type
	 * @param <R>
	 *            the n5 reader type
	 * @param parentFrame
	 *            parent frame, can be null
	 * @param dataSelection
	 *            data sets to display
	 * @param wantFrame
	 *            if true, use BdvHandleFrame and display a window. If false,
	 *            use a BdvHandlePanel and do not display anything.
	 * @throws IOException
	 *             if data could not be read
	 */
	public <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, R extends N5Reader> N5Viewer(
			final Frame parentFrame,
			final DataSelection dataSelection,
			final boolean wantFrame)
			throws IOException {

		Prefs.showScaleBar(true);

		this.sharedQueue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		final List<N5Metadata> selected = new ArrayList<>();
		for (final N5Metadata meta : dataSelection.metadata) {
			if (meta instanceof N5ViewerMultichannelMetadata) {
				final N5ViewerMultichannelMetadata mc = (N5ViewerMultichannelMetadata)meta;
				for (final MultiscaleMetadata<?> m : mc.getChildrenMetadata())
					selected.add(m);
			} else if (meta instanceof CanonicalMultichannelMetadata) {
				final CanonicalMultichannelMetadata mc = (CanonicalMultichannelMetadata)meta;
				for (final N5Metadata m : mc.getChildrenMetadata())
					selected.add(m);
			} else
				selected.add(meta);
		}

		final N5Reader n5 = dataSelection.n5;
		this.bdv = show(n5, selected, wantFrame, parentFrame );
	}

	public <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, R extends N5Reader> void addData(
			final DataSelection selection) throws IOException {

		final ArrayList<ConverterSetup> converterSetups = new ArrayList<>();
		final ArrayList<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();

		final List<N5Metadata> selected = new ArrayList<>();
		for (final N5Metadata meta : selection.metadata) {
			if (meta instanceof N5ViewerMultichannelMetadata) {
				final N5ViewerMultichannelMetadata mc = (N5ViewerMultichannelMetadata)meta;
				selected.addAll(Arrays.asList(mc.getChildrenMetadata()));
			} else if (meta instanceof CanonicalMultichannelMetadata) {
				final CanonicalMultichannelMetadata mc = (CanonicalMultichannelMetadata)meta;
				selected.addAll(Arrays.asList(mc.getChildrenMetadata()));
			} else
				selected.add(meta);
		}

		final BdvOptions opts = BdvOptions.options();
		numTimepoints = buildN5Sources(
				selection.n5,
				selected,
				sharedQueue,
				converterSetups,
				sourcesAndConverters,
				opts );

		for (final SourceAndConverter<?> sourcesAndConverter : sourcesAndConverters) {
			BdvFunctions.show(sourcesAndConverter, numTimepoints, opts.addTo(bdv));
		}
	}

	public static List<N5Metadata> unwrapMultichannelSelections( final DataSelection dataSelection )
	{
		final List<N5Metadata> selected = new ArrayList<>();
		for (final N5Metadata meta : dataSelection.metadata) {
			if (meta instanceof N5ViewerMultichannelMetadata ||
				meta instanceof CanonicalMultichannelMetadata ||
				meta instanceof GenericMetadataGroup  ) {

				@SuppressWarnings("rawtypes")
				final N5MetadataGroup mc = (N5MetadataGroup)meta;
				for (final N5Metadata m : mc.getChildrenMetadata())
					selected.add(m);
			} else
				selected.add(meta);
		}

		return selected;
	}

	public static BdvHandle show(final String uri) {

		try {
			return show(new N5URI(uri));
		} catch (final URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static BdvHandle show( final N5URI uri ) {

		return show( new N5Factory().openReader(uri.getContainerPath()),
				uri.getGroupPath() != null ? uri.getGroupPath() : "/",
				true, null);
	}

	public static BdvHandle show(String n5root, final String group) {

		return show(new N5Factory().openReader(n5root), group, true, null);
	}

	public static BdvHandle show(N5Reader n5, final String group) {

		return show(n5, group, true, null);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(N5Reader n5, final String group, final boolean wantFrame, final Frame parentFrame) {

		return show(n5, Collections.singletonList(N5MetadataUtils.parseMetadata(n5, group)), wantFrame, parentFrame);
	}

	public static BdvHandle show(N5Reader n5, List<N5Metadata> metadata) {

		return show(n5, metadata, true, null);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final String[] uris, final BdvOptions options) {

		return show(uris, options, true, null);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final String[] uris, final BdvOptions options, final boolean wantFrame, final Frame parentFrame) {

		final SharedQueue sharedQueue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		final List<ConverterSetup> converterSetups = new ArrayList<>();
		final List<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();
		int numTimepoints = 1;

		// find unique containers in the uris and make a DataSelection for each
		final HashMap<String,N5Reader> n5Readers = new HashMap<>();
		final HashMap<N5Reader,List<String>> selectionsByContainer = new HashMap<>();

		final N5ViewerReaderFun n5fun = new N5ViewerReaderFun();
		for( final String uri : uris )
		{
			N5URI n5uri;
			try {
				n5uri = new N5URI(uri);
			} catch (final URISyntaxException e) {
				System.err.println("Could not parse url: " + uri);
				continue;
			}

			if( !n5Readers.containsKey(n5uri.getContainerPath()))
			{
				// make a reader for this container and track it
				final N5Reader n5 = n5fun.apply(n5uri.getContainerPath());
				n5Readers.put(n5uri.getContainerPath(), n5);

				// start a list of paths for this container
				selectionsByContainer.put(n5, new ArrayList<>());
				selectionsByContainer.get(n5).add(N5URI.normalizeGroupPath(n5uri.getGroupPath()));
			}
			else
				selectionsByContainer.get(n5Readers.get(n5uri.getContainerPath()))
						.add(N5URI.normalizeGroupPath(n5uri.getGroupPath()));
		}

		// if this is called, can assume metadata have not been parsed yet. so parse now - once for each container.
		for( final N5Reader n5 : selectionsByContainer.keySet())
		{
			final N5TreeNode containerRoot = N5DatasetDiscoverer.discover(n5,
					Arrays.asList(N5ViewerCreator.n5vParsers),
					Arrays.asList(N5ViewerCreator.n5vGroupParsers));

			final List<N5Metadata> metadataList = selectionsByContainer.get(n5).stream()
					.map(x -> {
						return containerRoot.getDescendant(x).map(n -> n.getMetadata());
					})
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toList());

			final DataSelection selection = new DataSelection(n5, metadataList );
			try {
				numTimepoints = Math.max(numTimepoints,
						buildN5Sources(n5, selection, sharedQueue, converterSetups, sourcesAndConverters, options));
			} catch (final IOException e) {
				System.err.println("Could not load from: " + n5.getURI().toString());
			}
		}

		return show(sourcesAndConverters, numTimepoints, options, wantFrame, parentFrame);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(N5Reader n5, List<N5Metadata> metadata, final boolean wantFrame, final Frame parentFrame) {

		final DataSelection selection = new DataSelection(n5, metadata);
		final SharedQueue sharedQueue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		final List<ConverterSetup> converterSetups = new ArrayList<>();
		final List<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();

		final BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");
		int numTimepoints;
		try {
			numTimepoints = buildN5Sources(
					n5,
					selection,
					sharedQueue,
					converterSetups,
					sourcesAndConverters,
					options);

		} catch (final IOException e1) {
			e1.printStackTrace();
			return null;
		}

		return show(sourcesAndConverters, numTimepoints, options, wantFrame, parentFrame);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options) {

		return show(sourcesAndConverters, numTimepoints, options, true, null);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options, final boolean wantFrame, final Frame parentFrame) {

		BdvHandle bdvHandle = null;
		for (final SourceAndConverter<?> sourcesAndConverter : sourcesAndConverters) {
			if (bdvHandle == null) {
				if (wantFrame) {
					// Create and show a BdvHandleFrame with the first source
					bdvHandle = BdvFunctions.show(sourcesAndConverter, numTimepoints, options).getBdvHandle();
				} else {
					// Create a BdvHandlePanel, but don't show it
					bdvHandle = new BdvHandlePanel(parentFrame, options);
					// Add the first source to it
					BdvFunctions.show(sourcesAndConverter, numTimepoints, options.addTo(bdvHandle));
				}
			}
			else {
				// Subsequent sources are added to the existing handle
				BdvFunctions.show(sourcesAndConverter, numTimepoints, options.addTo(bdvHandle));
			}
		}

		final BdvHandle bdv = bdvHandle;
		if (bdv != null) {
			final ViewerPanel viewerPanel = bdv.getViewerPanel();
			if (viewerPanel != null) {
				viewerPanel.setNumTimepoints(numTimepoints);
				initCropController(bdv, sourcesAndConverters);
				// Delay initTransform until the viewer is shown because it
				// needs to have a size.
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

		if (bdv instanceof BdvHandleFrame) {
			// add crop to menu bar
			final BdvHandleFrame bdvFrame = (BdvHandleFrame)bdv;
			final ViewerFrame viewerFrame = bdvFrame.getBigDataViewer().getViewerFrame();
			final JMenuBar menuBar = viewerFrame.getJMenuBar();
			final ActionMap actionMap = viewerFrame.getKeybindings().getConcatenatedActionMap();

			final JMenu toolsMenu = menuBar.getMenu(2);
			final JMenuItem cropItem = new JMenuItem(actionMap.get("crop"));
			cropItem.setText("Extract to ImageJ");
			toolsMenu.add(cropItem);

			/* create XTouchMini midi controller */
			try {
				final XTouchMiniMCUControlPanel controlPanel = XTouchMiniMCUControlPanel.build();
				new MCUBDVControls(
						bdv.getBdvHandle().getViewerPanel(),
						controlPanel);

				((JFrame)SwingUtilities.getWindowAncestor(bdv.getBdvHandle().getViewerPanel()))
						.addWindowListener(new WindowAdapter() {

							@Override
							public void windowClosing(final WindowEvent e) {

								controlPanel.close();
							}

						});
			} catch (final Exception e) {}
		}

		return bdv;
	}

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> int buildN5Sources(
			final N5Reader n5,
			final DataSelection dataSelection,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return buildN5Sources(n5,
				unwrapMultichannelSelections(dataSelection),
				sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, M extends AxisMetadata & N5Metadata> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		final ArrayList<MetadataSource<?>> additionalSources = new ArrayList<>();

		// is2D should be true at the end of this loop if all sources are 2D
		boolean is2D = true;
		int numTimepoints = 1;

		int i;
		for (i = 0; i < selectedMetadata.size(); ++i) {
			String[] datasetsToOpen = null;
			AffineTransform3D[] transforms = null;

			final N5Metadata metadata = selectedMetadata.get(i);
			final String srcName = metadata.getName();


			// TODO: simplify this if/elseif block: much of these ifwall cases can be combined
			if (metadata instanceof N5SingleScaleMetadata) {
				final N5SingleScaleMetadata singleScaleDataset = (N5SingleScaleMetadata)metadata;
				final String[] tmpDatasets = new String[]{singleScaleDataset.getPath()};
				final AffineTransform3D[] tmpTransforms = new AffineTransform3D[]{
						singleScaleDataset.spatialTransform3d()};

				final MultiscaleDatasets msd = MultiscaleDatasets.sort(tmpDatasets, tmpTransforms);
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5MultiScaleMetadata) {
				final N5MultiScaleMetadata multiScaleDataset = (N5MultiScaleMetadata)metadata;
				datasetsToOpen = multiScaleDataset.getPaths();
				transforms = multiScaleDataset.spatialTransforms3d();
			} else if (metadata instanceof N5CosemMetadata) {
				final N5CosemMetadata singleScaleCosemDataset = (N5CosemMetadata)metadata;
				datasetsToOpen = new String[]{singleScaleCosemDataset.getPath()};
				transforms = new AffineTransform3D[]{singleScaleCosemDataset.spatialTransform3d()};
			} else if (metadata instanceof CanonicalSpatialMetadata) {
				final CanonicalSpatialMetadata canonicalDataset = (CanonicalSpatialMetadata)metadata;
				datasetsToOpen = new String[]{canonicalDataset.getPath()};
				transforms = new AffineTransform3D[]{canonicalDataset.getSpatialTransform().spatialTransform3d()};
			} else if (metadata instanceof OmeNgffMetadata) {
				final OmeNgffMetadata multiScaleDataset = (OmeNgffMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof N5CosemMultiScaleMetadata) {
				final N5CosemMultiScaleMetadata multiScaleDataset = (N5CosemMultiScaleMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof CanonicalMultiscaleMetadata) {
				final CanonicalMultiscaleMetadata multiScaleDataset = (CanonicalMultiscaleMetadata)metadata;
				final MultiscaleDatasets msd = MultiscaleDatasets
						.sort(multiScaleDataset.getPaths(), multiScaleDataset.spatialTransforms3d());
				datasetsToOpen = msd.getPaths();
				transforms = msd.getTransforms();
			} else if (metadata instanceof SpatialMetadata) {

				datasetsToOpen = new String[]{metadata.getPath()};
				transforms = new AffineTransform3D[]{ ((SpatialMetadata)metadata).spatialTransform3d() };
			} else if (metadata instanceof N5DatasetMetadata) {
				final List<MetadataSource<?>> addTheseSources = MetadataSource
						.buildMetadataSources(n5, (N5DatasetMetadata)metadata);
				if (addTheseSources != null)
					additionalSources.addAll(addTheseSources);
			} else {
				datasetsToOpen = new String[]{metadata.getPath()};
				transforms = new AffineTransform3D[]{new AffineTransform3D()};
			}

			if (datasetsToOpen == null || datasetsToOpen.length == 0)
				continue;

			@SuppressWarnings("rawtypes")
			final RandomAccessibleInterval[] images = new RandomAccessibleInterval[datasetsToOpen.length];
			String unit = "pixel";
			for (int s = 0; s < images.length; ++s) {

				@SuppressWarnings("unchecked")
				final RandomAccessibleInterval<T> img = (RandomAccessibleInterval<T>)loadImage(n5, datasetsToOpen[s]);

				final RandomAccessibleInterval< ? > imagejImg;
				if (metadata instanceof AxisMetadata)
				{
					imagejImg = AxisUtils.permuteForImagePlus(img, (M)metadata);
					unit = unitFromAxes(((AxisMetadata)metadata).getAxes());
				}
				else if( metadata instanceof N5SingleScaleMetadata )
				{
					final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)metadata );
					imagejImg = AxisUtils.permuteForImagePlus( img, axes );
					unit = ((N5SingleScaleMetadata)metadata).unit();
				}
				else if( isN5ViewerMultiscale(metadata))
				{
					final DefaultAxisMetadata axes = AxisUtils.defaultN5ViewerAxes( (N5SingleScaleMetadata)(((N5MultiScaleMetadata)metadata).getChildrenMetadata()[0]) );
					imagejImg = AxisUtils.permuteForImagePlus( img, axes );
					unit = unitFromAxes(axes.getAxes());
				}
				else if( isCosemMultiscale(metadata))
				{
					final N5CosemMultiScaleMetadata cosemMulti = ((N5CosemMultiScaleMetadata)metadata);
					final N5CosemMetadata cosemMeta = cosemMulti.getChildrenMetadata()[0];
					imagejImg = permuteForImagePlus(img, transforms[s], cosemMeta);
					unit = cosemMeta.unit();
				}
				else
				{
					final NgffSingleScaleAxesMetadata ngffMeta = isNgffMultiscale(metadata);
					if( ngffMeta != null ) {
						imagejImg = permuteForImagePlus(img, transforms[s], ngffMeta);
						unit = ngffMeta.unit();
					}
					else
					{
						RandomAccessibleInterval< ? > imgTmp = img;
						while( imgTmp.numDimensions() < 5 )
							imgTmp = Views.addDimension(imgTmp, 0, 0 );
						imagejImg = imgTmp;
					}
				}
				images[s] = imagejImg;

				is2D &= imagejImg.dimension(3) == 1;
				numTimepoints = (int)Math.max(numTimepoints, imagejImg.dimension(4));
			}

			// TODO: Ideally, the volatile views should use a caching strategy
			// where blocks are enqueued with reverse resolution level as
			// priority. However, this would require to predetermine the number
			// of resolution levels, which would man a lot of duplicated code
			// for analyzing selectedMetadata. Instead, wait until SharedQueue
			// supports growing numPriorities, then revisit.
			// See https://github.com/imglib/imglib2-cache/issues/18.
			// Probably it should look like this:
//			sharedQueue.ensureNumPriorities(images.length);
//			for (int s = 0; s < images.length; ++s) {
//				final int priority = images.length - 1 - s;
//				final CacheHints cacheHints = new CacheHints(LoadingStrategy.BUDGETED, priority, false);
//				vimages[s] = VolatileViews.wrapAsVolatile(images[s], sharedQueue, cacheHints);
//			}

			@SuppressWarnings("unchecked")
			final T type = (T)Util.getTypeFromInterval(images[0]);

			// this could / should be generalized
			final double rx = transforms[0].get(0, 0);
			final double ry = transforms[0].get(1, 1);
			final double rz = transforms[0].get(2, 2);

			/* there still can be many channels */
			@SuppressWarnings("unchecked")
			final List<Pair<Source<T>, Source<V>>> sourcePairs = createSource(
					type,
					srcName,
					images,
					transforms,
					sharedQueue,
					new FinalVoxelDimensions(unit, rx, ry, rz));

			for (final Pair<Source<T>, Source<V>> sourcePair : sourcePairs) {
				addSourceToListsGenericType(sourcePair.getA(), sourcePair.getB(), i + 1, converterSetups, sourcesAndConverters);
			}
		}

		for (final MetadataSource src : additionalSources) {
			if (src.numTimePoints() > numTimepoints)
				numTimepoints = src.numTimePoints();

			addSourceToListsGenericType(src, i + 1, converterSetups, sourcesAndConverters);
		}

		if (is2D)
			options.is2D();

		return numTimepoints;
	}

	/**
	 * Returns an image with dimensions in a canonical order XYCZY. Also
	 * permutes the given pixel to physical transform in-place.
	 *
	 * @param <T>
	 *            the type
	 * @param img
	 *            the image
	 * @param transform
	 *            the pixel to physical transfom
	 * @param meta
	 *            axis metadata
	 * @return a possibly permuted image
	 */
	protected static <T, M extends N5Metadata, A extends AxisMetadata & N5Metadata> RandomAccessibleInterval<T> permuteForImagePlus(
			final RandomAccessibleInterval<T> img,
			AffineTransform3D transform,
			final A meta) {

		final int[] p = AxisUtils.findImagePlusPermutation(meta);
		AxisUtils.fillPermutation(p);

		RandomAccessibleInterval<T> imgTmp = img;
		while (imgTmp.numDimensions() < 5)
			imgTmp = Views.addDimension(imgTmp, 0, 0);

		if (AxisUtils.isIdentityPermutation(p))
			return imgTmp;

		// update spatial transformation
		// exchange rows and columns of permutation matrix appropriately
		final int[] spatialPermutation = new int[]{p[0], p[1], p[3]};
		final AffineGet permTform = AxisUtils.axisPermutationTransform(spatialPermutation);
		transform.concatenate(permTform.inverse()).preConcatenate(permTform);

		return AxisUtils.permute(imgTmp, AxisUtils.invertPermutation(p));
	}

	/*
	 * If the image is of type {@link LabelMultisetType} to {@link UnsignedLongType}.
	 */
	protected static <T extends NumericType<T> & NativeType<T>> RandomAccessibleInterval<?> loadImage(
			final N5Reader n5, final String dataset) {

		final CachedCellImg<?, ?> img = N5Utils.openVolatile(n5, dataset);
		final Object t = Util.getTypeFromInterval(img);
		if( t instanceof LabelMultisetType ) {

			final CachedCellImg<LabelMultisetType, ?> lmsImg = (CachedCellImg<LabelMultisetType, ?>)img;
			return convertLabelMultisetCache(lmsImg);

			// TODO compare to the below
//			return (CachedCellImg<T, ?>)convertLabelMultisetLazy(
//					(CachedCellImg<LabelMultisetType, ?>)img);

//			return (RandomAccessibleInterval<T>)convertLabelMultisetVolatile(
//					(CachedCellImg<LabelMultisetType, ?>)img);
		}

		if (OmeNgffMultiScaleMetadata.fOrder(n5.getDatasetAttributes(dataset)))
			return AxisUtils.reverseDimensions(img);
		else
			return (RandomAccessibleInterval<T>)img;
	}

	private static RandomAccessibleInterval<VolatileUnsignedLongType> convertLabelMultisetVolatile( final CachedCellImg<LabelMultisetType,?> lmsImg ) {

		// TODO this isn't working (VolatileViews throws a NPE), but have not yet investigated why
		// see ViewCosem in n5-utils for something similar

		final RandomAccessibleInterval<Volatile<LabelMultisetType>> vimg = VolatileViews.wrapAsVolatile( lmsImg );
		return Converters.convert2(vimg,
				(a, b) -> {
					b.set(a.get().argMax());
					b.setValid(a.isValid());
				},
				VolatileUnsignedLongType::new);
	}

	private static CachedCellImg<UnsignedLongType, ?> convertLabelMultisetLazy(final CachedCellImg<LabelMultisetType, ?> lmsImg) {

		// use Lazy.generate to convert and cache
		final int[] cellDims = new int[lmsImg.numDimensions()];
		lmsImg.getCellGrid().cellDimensions(cellDims);

		return Lazy.generate(lmsImg, cellDims, new UnsignedLongType(),
				AccessFlags.setOf(AccessFlags.VOLATILE),
				x -> {
					final IntervalView<LabelMultisetType> in = (IntervalView<LabelMultisetType>)Views.interval(lmsImg, x);
					final Cursor<LabelMultisetType> inc = in.cursor();
					final Cursor<UnsignedLongType> outc = Views.flatIterable(x).cursor();
					while (outc.hasNext())
						outc.next().set(inc.next().argMax());
				});
	}

	private static CachedCellImg<UnsignedLongType, ?> convertLabelMultisetCache(final CachedCellImg<LabelMultisetType, ?> lmsImg) {

		final int[] cellDims = new int[lmsImg.numDimensions()];
		lmsImg.getCellGrid().cellDimensions(cellDims);

		return new ReadOnlyCachedCellImgFactory()
				.create(lmsImg.dimensionsAsLongArray(), new UnsignedLongType(),
						out -> {
							final IntervalView<LabelMultisetType> in = (IntervalView<LabelMultisetType>)Views.interval(lmsImg, out);
							final Cursor<LabelMultisetType> inc = in.cursor();
							final Cursor<UnsignedLongType> outc = out.cursor();
							while (outc.hasNext())
								outc.next().set(inc.next().argMax());
						},
						new ReadOnlyCachedCellImgOptions()
								.cellDimensions(cellDims)
								.volatileAccesses(true));
	}

	private static String unitFromAxes(Axis[] axes) {

		final Optional<Axis> axisOpt = Arrays.stream(axes)
				.filter(x -> x.getType().equals(Axis.SPACE)).findFirst();

		if (axisOpt.isPresent())
			return axisOpt.get().getUnit();

		return "pixel";
	}

	private static boolean isN5ViewerMultiscale( final N5Metadata metadata )
	{
		if(metadata instanceof N5MultiScaleMetadata )
		{
			final N5MultiScaleMetadata ms = (N5MultiScaleMetadata)metadata;
			final N5SingleScaleMetadata[] children = ms.getChildrenMetadata();
			if( children.length > 0 )
				return children[0] instanceof N5SingleScaleMetadata;
		}
		return false;
	}

	private static boolean isCosemMultiscale( final N5Metadata metadata )
	{
		if(metadata instanceof N5CosemMultiScaleMetadata )
		{
			final N5CosemMultiScaleMetadata ms = (N5CosemMultiScaleMetadata)metadata;
			final N5CosemMetadata[] children = ms.getChildrenMetadata();
			if( children.length > 0 )
				return children[0] instanceof N5CosemMetadata;
		}
		return false;
	}

	private static NgffSingleScaleAxesMetadata isNgffMultiscale(final N5Metadata metadata) {

		if (metadata instanceof OmeNgffMetadata) {

			final OmeNgffMetadata ngff = (OmeNgffMetadata)metadata;
			final OmeNgffMultiScaleMetadata[] ms = ngff.multiscales;

			// TODO when do we not just take the first one?
			final NgffSingleScaleAxesMetadata[] children = ms[0].getChildrenMetadata();
			if( children.length > 0 )
				if( children[0] instanceof NgffSingleScaleAxesMetadata)
					return children[0];

		}
		else if(metadata instanceof OmeNgffMultiScaleMetadata )
		{
			final OmeNgffMultiScaleMetadata ms = (OmeNgffMultiScaleMetadata)metadata;
			final NgffSingleScaleAxesMetadata[] children = ms.getChildrenMetadata();
			if( children.length > 0 )
				if( children[0] instanceof NgffSingleScaleAxesMetadata)
					return children[0];

		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T extends NumericType<T> & NativeType<T>, V extends NumericType<V> & NativeType<V>> List<Pair<Source<T>, Source<V>>> createSource(
			final T type,
			final String srcName,
			final RandomAccessibleInterval<T>[] images,
			final AffineTransform3D[] transforms,
			final SharedQueue sharedQueue,
			final VoxelDimensions vd) {

		final long nChannels = images[0].dimension(2);

		final ArrayList<Pair<Source<T>, Source<V>>> sourcePairs = new ArrayList<>();
		for ( int c = 0; c < nChannels; ++c ) {

			final RandomAccessibleInterval<T>[] channels = new RandomAccessibleInterval[images.length];
			for (int level = 0; level < images.length; ++level)
				channels[level] = Views.hyperSlice(images[level], 2, c);

			final RandomAccessibleIntervalMipmapSource4D<T> source = new RandomAccessibleIntervalMipmapSource4D<>(
					channels, type, transforms, vd, srcName, true);

			// TODO fix generics
			final ValuePair<Source<T>, Source<V>> pair = new ValuePair(
					source,
					source.asVolatile(sharedQueue));
			sourcePairs.add(pair);
		}
		return sourcePairs;
	}

	private static <T extends NumericType<T> & NativeType<T>> void initCropController(
			final BdvHandle bdv,
			final List<? extends SourceAndConverter<T>> sourceAndConverers) {

		final TriggerBehaviourBindings bindings = bdv.getBdvHandle().getTriggerbindings();

		final InputTriggerConfig config;
		ViewerFrame viewerFrame = null;
		if (bdv instanceof BdvHandleFrame) {
			final BdvHandleFrame bdvFrame = (BdvHandleFrame)bdv;
			config = bdvFrame.getBigDataViewer().getKeymapManager().getForwardSelectedKeymap().getConfig();
			viewerFrame = bdvFrame.getBigDataViewer().getViewerFrame();
		} else {
			config = new InputTriggerConfig();
		}

		final Source<T> src = sourceAndConverers.get(0).getSpimSource();
		final double[] boxMin = new double[3];
		final double[] boxMax = new double[3];

		// interval min / max
		final Interval itvl = src.getSource(0, 0);
		itvl.realMin(boxMin);
		itvl.realMax(boxMax);

		// world (physical) min / max
		final AffineTransform3D srcXfm = new AffineTransform3D();
		src.getSourceTransform(0, 0, srcXfm);
		srcXfm.apply(boxMin, boxMin);
		srcXfm.apply(boxMax, boxMax);

		final FinalRealInterval srcItvlWorld = new FinalRealInterval(boxMin, boxMax);
		final BoxCrop cropController = new BoxCrop(
				bdv.getViewerPanel(),
				bdv.getConverterSetups(),
				0,
				config,
				bindings,
				BoxSelectionOptions.options(),
				new AffineTransform3D(),
				srcItvlWorld,
				srcItvlWorld,
				"crop",
				"SPACE");

		bindings.addBehaviourMap("crop", cropController.getBehaviourMap());
		bindings.addInputTriggerMap("crop", cropController.getInputTriggerMap());

		final List<Source<T>> sources = sourceAndConverers.stream().map( SourceAndConverter::getSpimSource).collect(Collectors.toList());
		final CropController<T> cropControllerLegacy = new CropController<>(
				bdv.getViewerPanel(),
				sources,
				config,
				bdv.getKeybindings(),
				config);

		bindings.addBehaviourMap("cropLegacy", cropControllerLegacy.getBehaviourMap());
		bindings.addInputTriggerMap("cropLegacy", cropControllerLegacy.getInputTriggerMap());

		if (viewerFrame != null) {
			// set action for crop item in menu bar
			final InputActionBindings inputActionBindings = viewerFrame.getKeybindings();
			final Actions actions = new Actions(config, "bdv");
			actions.install(inputActionBindings, "crop");
			actions.runnableAction(() -> {
				cropController.click(0, 0);
			},
					"crop",
					"SPACE");
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
	private static <T> void addSourceToListsGenericType(
			final Source<T> source,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		addSourceToListsGenericType(source, null, setupId, converterSetups, sources);
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
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static <T, V extends Volatile<T>> void addSourceToListsGenericType(
			final Source<T> source,
			final Source<V> volatileSource,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		final T type = source.getType();
		if (type instanceof RealType || type instanceof ARGBType || type instanceof VolatileARGBType)
			addSourceToListsNumericType(
					(Source)source,
					(Source)volatileSource,
					setupId,
					converterSetups,
					(List)sources);
		else
			throw new IllegalArgumentException("Unknown source type. Expected RealType, ARGBType, or VolatileARGBType");
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
	private static <T extends NumericType<T>, V extends Volatile<T> & NumericType<V>> void addSourceToListsNumericType(
			final Source<T> source,
			final Source<V> volatileSource,
			final int setupId,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sources) {

		final SourceAndConverter<V> vsoc = (volatileSource == null)
				? null
				: new SourceAndConverter<>(volatileSource, createConverterToARGB(volatileSource.getType()));
		final SourceAndConverter<T> soc = new SourceAndConverter<>(
				source,
				createConverterToARGB(source.getType()),
				vsoc);
		final SourceAndConverter<T> tsoc = wrapWithTransformedSource(soc);

		converterSetups.add(BigDataViewer.createConverterSetup(tsoc, setupId));
		sources.add(tsoc);
	}
}
