package org.janelia.saalfeldlab.n5.bdv;


import java.awt.Frame;
import java.awt.Insets;
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
import org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem.CoordinateSystemCard;
import org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem.CoordinateSystemContext;
import org.janelia.saalfeldlab.n5.ij.N5Importer.N5ViewerReaderFun;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.ui.DataSelection;
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.GenericMetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.OmeNgffSceneParser;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.scene.NgffScene;

import org.janelia.saalfeldlab.n5.universe.metadata.axes.AxisMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.canonical.CanonicalMultichannelMetadata;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.tools.InitializeViewerState;
import bdv.tools.boundingbox.BoxSelectionOptions;
import bdv.tools.brightness.ConverterSetup;
import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.util.Prefs;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerFrame;
import bdv.viewer.ViewerPanel;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

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

	/**
	 * Shows whatever is at {@code group}, discovering its metadata with n5-viewer's
	 * own parsers ({@link N5ViewerCreator#n5vParsers}) rather than the defaults in
	 * {@link org.janelia.saalfeldlab.n5.universe.N5MetadataUtils}, which do not
	 * include {@link OmeNgffSceneParser} and so cannot recognize an
	 * {@link NgffScene}.
	 *
	 * @param n5
	 *            the reader
	 * @param group
	 *            the group to show
	 * @param wantFrame
	 *            if true, use BdvHandleFrame and display a window. If false, use
	 *            a BdvHandlePanel and do not display anything.
	 * @param parentFrame
	 *            parent frame, can be null
	 * @return the bdv handle
	 */
	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(N5Reader n5, final String group, final boolean wantFrame, final Frame parentFrame) {

		final N5TreeNode root = N5DatasetDiscoverer.discover(n5,
				Arrays.asList(N5ViewerCreator.n5vParsers),
				Arrays.asList(N5ViewerCreator.n5vGroupParsers));

		final N5Metadata metadata = root == null
				? null
				: root.getDescendant(group).map(N5TreeNode::getMetadata).orElse(null);

		return show(n5, Collections.singletonList(metadata), wantFrame, parentFrame);
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

	/**
	 * Shows the given metadata, detecting the coordinate systems it declares
	 * (see {@link CoordinateSystemContext#fromMetadata(N5Reader, List)}). When any
	 * are found, the sources are built so that they can be re-transformed, and a
	 * coordinate-systems card is added to the side panel naming them, from which
	 * the user selects the coordinate system to view the sources in.
	 *
	 * @param n5
	 *            the reader
	 * @param metadata
	 *            the metadata to show
	 * @param wantFrame
	 *            if true, use BdvHandleFrame and display a window. If false, use
	 *            a BdvHandlePanel and do not display anything.
	 * @param parentFrame
	 *            parent frame, can be null
	 * @return the bdv handle
	 */
	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(N5Reader n5, List<N5Metadata> metadata, final boolean wantFrame, final Frame parentFrame) {

		final List<N5Metadata> selected = unwrapMultichannelSelections(new DataSelection(n5, metadata));
		final SharedQueue sharedQueue = new SharedQueue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		final List<ConverterSetup> converterSetups = new ArrayList<>();
		final List<SourceAndConverter<T>> sourcesAndConverters = new ArrayList<>();

		// detect the coordinate systems the metadata itself declares; when there
		// are several, build through the context so the card's selection can
		// re-transform the sources (a null context builds them as before)
		final CoordinateSystemContext context = CoordinateSystemContext.fromMetadata(n5, selected);

		final BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");
		int numTimepoints;
		try {
			numTimepoints = context != null
					? N5VSources.buildN5Sources( n5, selected, context, sharedQueue, converterSetups, sourcesAndConverters, options)
					: buildN5Sources( n5, selected, sharedQueue, converterSetups, sourcesAndConverters, options);

		} catch (final IOException e1) {
			e1.printStackTrace();
			return null;
		}

		return show(sourcesAndConverters, numTimepoints, options, wantFrame, parentFrame, context);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options) {

		return show(sourcesAndConverters, numTimepoints, options, true, null, null);
	}

	/**
	 * Shows the given sources and, when {@code context} is non-null, adds a
	 * side-panel card listing the detected coordinate-system names.
	 *
	 * @param sourcesAndConverters
	 *            the sources to show
	 * @param numTimepoints
	 *            the number of timepoints
	 * @param options
	 *            bdv options
	 * @param context
	 *            the coordinate-system context backing the coordinate-systems
	 *            card, or {@code null} for no card
	 * @return the bdv handle
	 */
	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options, final CoordinateSystemContext context) {

		return show(sourcesAndConverters, numTimepoints, options, true, null, context);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options, final boolean wantFrame, final Frame parentFrame) {

		return show(sourcesAndConverters, numTimepoints, options, wantFrame, parentFrame, null);
	}

	public static <T extends NumericType<T> & NativeType<T>> BdvHandle show(final List<SourceAndConverter<T>> sourcesAndConverters, final int numTimepoints,
			final BdvOptions options, final boolean wantFrame, final Frame parentFrame, final CoordinateSystemContext context) {

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

		if (bdv != null && context != null)
			addCoordinateSystemsCard(bdv, context);

		return bdv;
	}

	/**
	 * Adds the read-only coordinate-systems card to the viewer's side panel and
	 * reveals the (initially collapsed) side panel. Runs on the EDT.
	 *
	 * @param bdv
	 *            the viewer handle
	 * @param context
	 *            the context whose coordinate-system names are listed
	 */
	private static void addCoordinateSystemsCard(final BdvHandle bdv, final CoordinateSystemContext context) {

		SwingUtilities.invokeLater(() -> {
			final Runnable repaint = () -> {
				final ViewerPanel viewerPanel = bdv.getViewerPanel();
				if (viewerPanel != null)
					viewerPanel.requestRepaint();
			};
			final CoordinateSystemCard card = new CoordinateSystemCard(context, repaint);
			bdv.getCardPanel().addCard(
					CoordinateSystemCard.CARD_KEY,
					CoordinateSystemCard.CARD_TITLE,
					card,
					true,
					new Insets(0, 0, 0, 0));
			bdv.getSplitPanel().setCollapsed(false);
		});
	}

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>> int buildN5Sources(
			final N5Reader n5,
			final DataSelection dataSelection,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return N5VSources.buildN5Sources(n5, dataSelection, sharedQueue, converterSetups, sourcesAndConverters, options);
	}

	public static <T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V>, M extends AxisMetadata & N5Metadata> int buildN5Sources(
			final N5Reader n5,
			final List<N5Metadata> selectedMetadata,
			final SharedQueue sharedQueue,
			final List<ConverterSetup> converterSetups,
			final List<SourceAndConverter<T>> sourcesAndConverters,
			final BdvOptions options ) throws IOException {

		return N5VSources.buildN5Sources(n5, selectedMetadata, sharedQueue, converterSetups, sourcesAndConverters, options);
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

}
