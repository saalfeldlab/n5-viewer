package org.janelia.saalfeldlab.n5.bdv.tools.boundingbox;

import static bdv.tools.boundingbox.BoxSelectionOptions.TimepointSelection.NONE;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.tools.boundingbox.AbstractTransformedBoxModel.IntervalChangedListener;
import bdv.tools.boundingbox.BoxDisplayModePanel;
import bdv.tools.boundingbox.BoxSelectionOptions;
import bdv.tools.boundingbox.TransformedRealBoxSelectionDialog;
import bdv.util.MipmapTransforms;
import bdv.tools.boundingbox.BoxSelectionOptions.TimepointSelection;
import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.ConverterSetups;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.util.Util;

public class BoxCrop extends TransformedRealBoxSelectionDialog implements ClickBehaviour
{
	private static final long serialVersionUID = -1857317799215108065L;

	public static final String EXPORT_CURRENT = "Current";
	public static final String EXPORT_VISIBLE = "Visible";

	private final AbstractViewerPanel viewer;
	private final List< SourceAndConverter< ? > > sources;

	private final String name;
	private final String[] defaultTriggers;

	private JComboBox<Integer> scaleLevelDropdown;
	private JComboBox<String> exportedSourcesDropddown;
	private JCheckBox concatenateSourcesCheck;
	private JLabel information;

	private SourceAndConverter< ? > currSrc;
	private int selectedLevel;
	private RealInterval selectedInterval;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	private TimepointSelection timePointSelection = TimepointSelection.NONE;

	public BoxCrop(
			final AbstractViewerPanel viewer,
			final ConverterSetups converterSetups,
			final int setupId,
			final InputTriggerConfig keyConfig,
			final TriggerBehaviourBindings triggerbindings,
			final BoxSelectionOptions options,
			final AffineTransform3D boxTransform,
			final RealInterval initialInterval,
			final RealInterval rangeInterval,
			final String name,
			final String... defaultTriggers )
	{
		super( viewer, converterSetups, setupId, keyConfig, triggerbindings, boxTransform, initialInterval, rangeInterval, options );
		this.viewer = viewer;
		sources = viewer.state().getSources();

		this.name = name;
		this.defaultTriggers = defaultTriggers;
		timePointSelection = options.values.getTimepointSelection();

		inputAdder = keyConfig.inputTriggerAdder( inputTriggerMap, "crop" );
		register();

		super.model.intervalChangedListeners().add( new IntervalChangedListener() {
			@Override
			public void intervalChanged()
			{
				updateInformation();
			}
		});

		// add actions
		super.buttons.onOk( () -> crop() );
	}

	@Override
	public void click( int x, int y )
	{
		// creating the box source makes it the current source.
		// so store the current source here, and make it current 
		currSrc = viewer.state().getCurrentSource();
		updateScales();

		final Source<?> src = sources.get( 0 ).getSpimSource();
		final int estBestScale = MipmapTransforms.getBestMipMapLevel( viewer.state().getViewerTransform(), src, 0 );
		System.out.println( "estBestscale:" + estBestScale );
		System.out.println( "nmipmaps: " + src.getNumMipmapLevels() );
		final int bestScale  = estBestScale <= src.getNumMipmapLevels() - 1 ? estBestScale : src.getNumMipmapLevels() - 1;
		scaleLevelDropdown.setSelectedIndex( bestScale );

		final double[] boxMin = new double[ 3 ];
		final double[] boxMax = new double[ 3 ];

		// interval min / max
		final Interval srcItvl = src.getSource( 0, 0 );
		srcItvl.realMin( boxMin );
		srcItvl.realMax( boxMax );

		// world (physical) min / max
		final AffineTransform3D srcXfm = new AffineTransform3D();
		src.getSourceTransform( 0, 0, srcXfm );
		srcXfm.apply( boxMin, boxMin );
		srcXfm.apply( boxMax, boxMax );

		final RealInterval initItvl = initialBoxFromView( viewer, src );
		model.setInterval( initItvl );

		updateInformation();
		setVisible( true );
	}

	public static RealInterval initialBoxFromView( AbstractViewerPanel viewer, Source<?> src )
	{
		final Dimension dispSz = viewer.getDisplayComponent().getSize();
		final RealPoint minCorner = new RealPoint( 3 );
		final RealPoint maxCorner = new RealPoint( dispSz.width, dispSz.height, 0 );
		viewer.state().getViewerTransform().applyInverse( minCorner, minCorner );
		viewer.state().getViewerTransform().applyInverse( maxCorner, maxCorner );

		final Interval srcItvl = src.getSource( 0, 0 );
		AffineTransform3D srcXfm = new AffineTransform3D();
		src.getSourceTransform( 0, 0, srcXfm );
		final RealInterval srcBboxWorld = transformedBoundingBox( srcXfm, srcItvl );

		final int nd = srcItvl.numDimensions();
		final double[] min = new double[ nd ];
		final double[] max = new double[ nd ];
		double maxWidth = 0;
		double minFrac = Double.MAX_VALUE;

		double w, f;
		double avgFrac = 0; // the average across
		for( int d = 0; d < nd; d++ )
		{
			// compute half of the viewer width (center to corner is half the width)
			// need abs, since world coordinates of maxcorner in viewer space may be smaller than
			// world coordinates of viewer min corner
			w = Math.abs( maxCorner.getDoublePosition( d ) - minCorner.getDoublePosition( d )) / 2;
			maxWidth =  w > maxWidth ? w : maxWidth;

			// fraction of whole image
			f = w / (srcBboxWorld.realMax( d ) - srcBboxWorld.realMin( d ));
			avgFrac += f;
			minFrac =  f < minFrac ? f : minFrac;
		}
		avgFrac = avgFrac / nd;

		for( int d = 0; d < nd; d++ )
		{
			w = maxWidth / 2;
			f = w / (srcBboxWorld.realMax( d ) - srcBboxWorld.realMin( d ));
			if( f > avgFrac )
				w = avgFrac * (srcBboxWorld.realMax( d ) - srcBboxWorld.realMin( d ));

			double center = 0.5 * ( minCorner.getDoublePosition( d ) + maxCorner.getDoublePosition( d ) );
			min[ d ] = center - w;
			max[ d ] = center + w;
		}

		return new FinalRealInterval( min, max );
	}

	public void register()
	{
		behaviourMap.put( name, this );
		inputAdder.put( name, defaultTriggers );
	}

	/**
	 * Adds {@link #boxSelectionPanel} etc to {@link #content}.
	 * Override in subclasses to add more / different stuff.
	 */
	protected JPanel createContent()
	{
		final JPanel content = new JPanel();

		final GridBagLayout layout = new GridBagLayout();
		layout.columnWidths = new int[] { 80 };
		layout.columnWeights = new double[] { 1. };
		content.setLayout( layout );

		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.gridx = 0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.insets = new Insets( 5, 5, 5, 5 );

		information = new JLabel("");
		content.add( information, gbc );

		gbc.gridy++;
		content.add( new JLabel("Scale level"), gbc );

		gbc.gridx = 1;
		scaleLevelDropdown = new JComboBox< Integer >( new Integer[] { 0 } );
		scaleLevelDropdown.addActionListener( (e) -> { 
			updateInformation();
			this.selectedLevel = scaleLevelDropdown.getSelectedIndex();
			/*
			 * I tried instead calling scaleLevelDropwodn.getSelectedIndex()
			 * in the crop methods, but when called there, it always returns zero.
			 * So instead, I'm forced to store the value here. I'm not happy about this. -John
			 */
		});
		content.add( scaleLevelDropdown, gbc );

		gbc.gridy++;
		gbc.gridx = 0;
		content.add( new JLabel("Images to export"), gbc );

		gbc.gridx = 1;
		exportedSourcesDropddown = new JComboBox<>( new String[] { EXPORT_CURRENT, EXPORT_VISIBLE });
		content.add( exportedSourcesDropddown, gbc );

		gbc.gridy++;
		concatenateSourcesCheck = new JCheckBox( "Concatenate export" );
		content.add( concatenateSourcesCheck, gbc );

		gbc.gridx = 0;
		gbc.gridy++;
		final JLabel lblTitle = new JLabel( "Selection:" );
		lblTitle.setFont( content.getFont().deriveFont( Font.BOLD ) );
		content.add( lblTitle, gbc );

		gbc.gridy++;
		final JPanel boundsPanel = new JPanel();
		boundsPanel.setLayout( new BoxLayout( boundsPanel, BoxLayout.PAGE_AXIS ) );
		boundsPanel.add( boxSelectionPanel );
		if ( timePointSelection != NONE )
			boundsPanel.add( timepointSelectionPanel );
		content.add( boundsPanel, gbc );

		gbc.gridy++;
		gbc.anchor = GridBagConstraints.BASELINE_LEADING;
		gbc.fill = GridBagConstraints.NONE;
		final BoxDisplayModePanel boxModePanel = new BoxDisplayModePanel( boxEditor.boxDisplayMode() );
		content.add( boxModePanel, gbc );

		return content;
	}

	@Override
	public void setVisible( boolean visible )
	{
		updateScales();
		super.setVisible( visible );
	}

	public void updateScales()
	{
		final int numScales; 
		if( currSrc != null )
			numScales = currSrc.getSpimSource().getNumMipmapLevels();
		else 
			numScales = sources.get( 0 ).getSpimSource().getNumMipmapLevels();

		scaleLevelDropdown.setModel( new DefaultComboBoxModel<Integer>( 
				IntStream.range( 0, numScales ).mapToObj( x -> new Integer(x) ).toArray( Integer[]::new )));

		pack();
	}

	public <T extends NativeType<T>> void updateInformation()
	{
		@SuppressWarnings( "unchecked" )
		final Source< T > src = ( Source< T > ) sources.get( 0 ).getSpimSource();
		final T t = Util.getTypeFromInterval( src.getSource( 0, 0 ));

		final Interval pixItvl = getPixelInterval( src, selectedLevel );
		final long numBytes = estimateBytes( pixItvl, t, selectedLevel );
		final String byteString = humanReadableByteCountSI( numBytes );

		if ( pixItvl.numDimensions() == 2 )
			information.setText( String.format( "Output %d x %d (%s)",
					pixItvl.dimension( 0 ), pixItvl.dimension( 1 ), byteString ) );
		else if ( pixItvl.numDimensions() == 3 )
			information.setText( String.format( "Output %d x %d x %d (%s)",
					pixItvl.dimension( 0 ), pixItvl.dimension( 1 ), pixItvl.dimension( 2 ), byteString ));

		repaint();
	}

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	public Interval getPixelInterval( Source<?> src, int scale )
	{
		final AffineTransform3D srcXfm = new AffineTransform3D();
		src.getSourceTransform( 0, scale, srcXfm );

		final RealInterval requestedInterval = model.getInterval();

		// get pixel interval from real interval
		final FinalRealInterval bbox = transformedBoundingBox( srcXfm.inverse(), requestedInterval );
		final long[] pixMin = new long[bbox.numDimensions()];
		final long[] pixMax = new long[bbox.numDimensions()];
		for( int d = 0; d < bbox.numDimensions(); d++ )
		{
			pixMin[d] = (long)Math.floor( bbox.realMin( d ) );
			pixMax[d] = (long)Math.ceil( bbox.realMax( d ) );
		}
		return new FinalInterval( pixMin, pixMax );
	}

	public <T extends NumericType<T> & NativeType<T>> ImagePlus[] crop()
	{
		// need to reset the current source
		viewer.state().setCurrentSource( currSrc );

		final String exportOption = ( String ) exportedSourcesDropddown.getSelectedItem();
		final List< SourceAndConverter<T>> srcList = new ArrayList<>();
		if( exportOption.equals( EXPORT_CURRENT ))
			srcList.add( ( SourceAndConverter< T > ) viewer.state().getCurrentSource());
		else 
		{
			final Set< SourceAndConverter< ? > > visibleSources = viewer.state().getVisibleSources();
			System.out.println( "num visible sources: " + visibleSources.size() );
			// exclude the box display source which has a Void type
			visibleSources.removeIf( x -> { return x.getSpimSource().getType() == null; } );
			System.out.println( "num visible sources: " + visibleSources.size() );
			visibleSources.forEach( x -> srcList.add( (SourceAndConverter<T>)x ) );
		}

		// if exporting to a single stack, check that the types are all equal
		boolean doStack = concatenateSourcesCheck.isSelected();
		if( concatenateSourcesCheck.isEnabled() )
		{
			// check that types are the same and that affines are the same
			AffineTransform3D first = new AffineTransform3D();
			AffineTransform3D comp = new AffineTransform3D();

			Object t = Util.getTypeFromInterval( srcList.get( 0 ).getSpimSource().getSource( 0, 0 ) );
			srcList.get( 0 ).getSpimSource().getSourceTransform( 0, selectedLevel, first );
			for( int i = 1; i < srcList.size(); i++ )	
			{
				Object s = Util.getTypeFromInterval( srcList.get( i ).getSpimSource().getSource( 0, 0 ) );
				if( !s.getClass().equals( t.getClass() ))
				{
					// alternatively, return and throw an error
					// TODO warn
					System.out.println("cant stack - different types");
					doStack = false;
					break;
//					return null;
				}

				srcList.get( i ).getSpimSource().getSourceTransform( 0, selectedLevel, comp );
				if( !affineAlmostEqual( first, comp, 1e-9 ))
				{
					System.out.println("cant stack - different affines");
					doStack = false;
					break;
				}
			}
		}

		final List< RandomAccessibleInterval<T>> imgList = new ArrayList<>();
		for( SourceAndConverter< T > sac : srcList )
			imgList.add( cropSource( sac.getSpimSource() ));

		if( doStack )
		{
			final RandomAccessibleInterval< T > imgTmp = Views.stack( imgList );
			final RandomAccessibleInterval< T > imgP = Views.moveAxis( imgTmp, imgTmp.numDimensions() - 1, 2 );
			final ImagePlus imp = ImageJFunctions.wrap( imgP, "concatenated_crop" );
			updateDisplayRange( imp, srcList.get( 0 ));
			updateResolution( imp, srcList.get( 0 ), selectedLevel );
			updateOffset( imp, model.getInterval() );
			imp.show();
			return new ImagePlus[] { imp };
		}
		else
		{
			final ImagePlus[] results = new ImagePlus[ imgList.size() ];
			for( int i = 0; i < imgList.size(); i++ )
			{
				final RandomAccessibleInterval< T > imgTmp = imgList.get( i );
				final RandomAccessibleInterval< T > img;
				if( imgTmp.numDimensions() == 3 )
					img = Views.moveAxis( Views.addDimension( imgTmp, 0, 0 ), 2, 3 );
				else
					img = imgTmp;

				final ImagePlus imp = ImageJFunctions.wrap( img, srcList.get( i ).getSpimSource().getName() + "+_crop" );
				updateDisplayRange( imp, srcList.get( i ) );
				updateResolution( imp, srcList.get( i ), selectedLevel );
				updateOffset( imp, model.getInterval() );
				results[i] = imp;
				imp.show();
			}
			return results;
		}
	}

	/**
	 * Modifies the displayrange of the ImagePlus using the provided SourceAndConverter,
	 * if possible
	 * 
	 * @param imp the ImagePlus
	 * @param sac the SourceAndConverter
	 */
	private static void updateDisplayRange( ImagePlus imp, SourceAndConverter<?> sac )
	{
		final Converter<?,?> conv = sac.getConverter();
		if( conv instanceof RealARGBColorConverter )
		{
			@SuppressWarnings( "rawtypes" )
			final RealARGBColorConverter rc = (RealARGBColorConverter)conv;
			imp.setDisplayRange( rc.getMin(), rc.getMax());
		}
	}

	private static void updateOffset( ImagePlus imp, RealInterval itvl )
	{
		imp.getCalibration().xOrigin = itvl.realMin( 0 );
		imp.getCalibration().yOrigin = itvl.realMin( 1 );
		imp.getCalibration().zOrigin = itvl.realMin( 2 );
	}

	private static void updateResolution( ImagePlus imp, SourceAndConverter<?> sac, int level )
	{
		AffineTransform3D tmp = new AffineTransform3D();
		sac.getSpimSource().getSourceTransform( 0, level, tmp );
		imp.getCalibration().pixelWidth = (tmp.get( 0, 0 ) + tmp.get( 0, 1 ) + tmp.get( 0, 2 )) / 3;
		imp.getCalibration().pixelHeight = (tmp.get( 1, 0 ) + tmp.get( 1, 1 ) + tmp.get( 1, 2 )) / 3;
		imp.getCalibration().pixelDepth = (tmp.get( 2, 0 ) + tmp.get( 2, 1 ) + tmp.get( 2, 2 )) / 3;
	}

	public <T extends NumericType<T> & NativeType<T>> RandomAccessibleInterval<T> cropSource( Source<T> src )
	{
		final RandomAccessibleInterval< T > img = src.getSource( 0, selectedLevel );
		Interval pixItvl = getPixelInterval( src, selectedLevel );
		final IntervalView< T > cropImg = Views.interval( Views.extendZero( img ), pixItvl );
		return cropImg;
	}

	public static FinalRealInterval transformedBoundingBox( RealTransform xfm, RealInterval interval )
	{
		if( xfm == null )
			return new FinalRealInterval( interval );

		int nd = interval.numDimensions();
		double[] pt = new double[ nd ];
		double[] ptxfm = new double[ nd ];

		double[] min = new double[ nd ];
		double[] max = new double[ nd ];
		Arrays.fill( min, Double.MAX_VALUE );
		Arrays.fill( max, Double.MIN_VALUE );

		long[] unitInterval = new long[ nd ];
		Arrays.fill( unitInterval, 2 );

		IntervalIterator it = new IntervalIterator( unitInterval );
		while( it.hasNext() )
		{
			it.fwd();
			for( int d = 0; d < nd; d++ )
			{
				if( it.getLongPosition( d ) == 0 )
					pt[ d ] = interval.realMin( d );
				else
					pt[ d ] = interval.realMax( d );
			}

			xfm.apply( pt, ptxfm );

			for( int d = 0; d < nd; d++ )
			{
				long lo = (long)Math.floor( ptxfm[d] );
				long hi = (long)Math.ceil( ptxfm[d] );

				if( lo < min[ d ])
					min[ d ] = lo;

				if( hi > max[ d ])
					max[ d ] = hi;
			}
		}
		return new FinalRealInterval( min, max );
	}
	
	/*
	 * https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
	 */
	protected static String humanReadableByteCountSI(long bytes) {
	    if (-1000 < bytes && bytes < 1000) {
	        return bytes + " B";
	    }
	    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
	    while (bytes <= -999_950 || bytes >= 999_950) {
	        bytes /= 1000;
	        ci.next();
	    }
	    return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}

	private <T extends NativeType<T>> long estimateBytes( Interval itvl, T t, int level )
	{
		NativeType g;
		if( t instanceof Volatile )
			g = (NativeType)((Volatile<?>)t).get();
		else
			g = t;

		final DataType dataType = N5Utils.dataType( g );
		final String typeString = dataType.toString();
		final long N = Intervals.numElements( itvl );
		long nBytes = -1;
		if( typeString.endsWith( "8" ))
			nBytes = N;
		else if( typeString.endsWith( "16" ))
			nBytes = N*2;
		else if( typeString.endsWith( "32" ))
			nBytes = N*4;
		else if( typeString.endsWith( "64" ))
			nBytes = N*8;

		return nBytes;
	}
	
	private static boolean affineAlmostEqual( AffineTransform3D a, AffineTransform3D b, double relativeThreshold )
	{
		final double avgNorm = frobeniusNorm( a ) * frobeniusNorm( b ) * 0.5;
		final double[] avals = a.getRowPackedCopy();
		final double[] bvals = b.getRowPackedCopy();
		for ( int i = 0; i < avals.length; i++ )
		{
			final double absDiff = Math.abs(avals[i] - bvals[i]);
			if( absDiff > relativeThreshold * avgNorm )
				return false;
		}
		return true;
	}
	
	private static double frobeniusNorm( AffineTransform3D a )
	{
		double norm = 0;
		for( int i = 0; i < 3; i++ ) 
		{
			for( int j = 0; j < 3; j++ )
			{
				final double v = a.get( i, j );
				norm += (v * v);
			}
		}
		return Math.sqrt( norm );
	}

}
