package org.janelia.saalfeldlab.n5.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.axes.AxisMetadata;
import org.janelia.saalfeldlab.n5.metadata.axes.AxisUtils;
import org.janelia.saalfeldlab.n5.metadata.axes.DefaultAxisMetadata;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;
import net.imglib2.view.ViewTransforms;
import net.imglib2.view.Views;

public class MetadataSource <T extends NumericType<T> & NativeType<T>> implements Source<T> {

	private final N5Reader n5;
	private final N5DatasetMetadata metadata;
	private CachedCellImg<T, ?> imgRaw;
	
	private int timeDimension;
	private MixedTransform timeSlice;

	private int channelDimension;
	private int channelPos;
	private MixedTransform channelSlice;

	private AffineTransform3D sourceTransform;

	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata, int channelDim, int channelPos ) {
		this.n5 = n5;
		this.metadata = metadata;

		try {
			imgRaw = N5Utils.open(n5, metadata.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		if( metadata instanceof SpatialMetadata )
			sourceTransform = ((SpatialMetadata) metadata).spatialTransform3d();
		else
			sourceTransform = new AffineTransform3D();

		this.channelDimension = channelDim;
		this.channelPos = channelPos;

		if( metadata instanceof AxisMetadata )
			inferDimensions((AxisMetadata) metadata, channelDim );
		else
			inferDimensions( defaultAxes( metadata ), channelDim );
	}

	public int getChannelDimension() {
		return channelDimension;
	}

	public int getTimeDimension() {
		return timeDimension;
	}

	public CachedCellImg<T, ?> getRawImage()
	{
		return imgRaw;
	}

	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata ) {
		this( n5, metadata, -1, 0 );
	}
	
	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata, int channelPos ) {
		this( n5, metadata, -1, channelPos );
	}

	public static DefaultAxisMetadata defaultAxes( N5DatasetMetadata meta ) {
		int nd = meta.getAttributes().getNumDimensions();
		String[] labels = Arrays.stream( new String[] {"x", "y", "c", "z", "t" } ).limit(nd).toArray( String[]::new );
		String[] types = AxisUtils.getDefaultTypes(labels);
		String[] units = Stream.generate( () -> "pixel").limit(nd).toArray( String[]::new );
		return new DefaultAxisMetadata(meta.getPath(), labels, types, units);
	}

	/**
	 * Builds slicing transforms.
	 * <p>
	 * Up to three space dimensions are allowed. One time dimension is allowed. 
	 * One channel dimension is allowed.
	 *
	 * @param axes axis metadata
	 * @return are the axes valid, constrained as described above.
	 */
	public boolean inferDimensions( AxisMetadata axes, final int channel ) {

		final ArrayList<Integer> spaceDims = new ArrayList<>();
		final ArrayList<Integer> timeDims = new ArrayList<>();
		final ArrayList<Integer> channelDims = new ArrayList<>();

		// treat any non-space and non-time dimension as a channel dimension
		final int nd = axes.getAxisLabels().length;
		for( int i = 0; i < nd; i++ ) {
			final String type = axes.getAxisTypes()[i];
			if( type.equals("space") )
				spaceDims.add(i);
			else if( type.equals("time") )
				timeDims.add(i);
			else
				channelDims.add(i);
		}

		if( spaceDims.size() > 3 )
			return false;

		if( timeDims.size() > 1 )
			return false;
		else if( timeDims.size() == 0 ) {
			timeDimension = -1;
			timeSlice = null;
		}
		else
		{
			timeDimension = timeDims.get(0);
			timeSlice = ViewTransforms.hyperSlice(nd, timeDims.get(0), 0 );
		}

		if( channelDims.size() > 1 )
			return false;
		else if ( channelDims.size() == 0 ) {
			channelDimension = -1;
			channelSlice = null;
		}
		else {
			channelDimension = channelDims.get(0);
			channelSlice = ViewTransforms.hyperSlice(nd, channelDims.get(0), channel );
		}

		return true;
	}

	@Override
	public boolean isPresent(int t) {
		if( timeDimension < 0 )
			return t == 0;
		else
			return t >= 0 && t < imgRaw.dimension(timeDimension);
	}

	public int numTimePoints()
	{
		return timeDimension < 0 ? 1 : (int)imgRaw.dimension(timeDimension); 
	}

	@Override
	public RandomAccessibleInterval<T> getSource(int t, int level) {

		RandomAccessibleInterval<T> img = imgRaw;
		if( timeDimension >= 0 )
			img = Views.hyperSlice(img, timeDimension, t);

		if( channelDimension >= 0 )
			img = Views.hyperSlice(img, channelDimension, channelPos);

		return img;
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
		RandomAccessibleInterval<T> src = getSource(t,level);
		if( method.equals( Interpolation.NEARESTNEIGHBOR ))
			return Views.interpolate( Views.extendZero( src ), new NearestNeighborInterpolatorFactory<>() );
		else 
			return Views.interpolate( Views.extendZero( src ), new NLinearInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform(int t, int level, AffineTransform3D transform) {
		transform.set(sourceTransform);
	}

	@Override
	public T getType() {
		return Util.getTypeFromInterval(imgRaw);
	}

	@Override
	public String getName() {
		return metadata.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getNumMipmapLevels() {
		return 1;
	}
	
//	public Source<?> getSource() {
//
//		imgP = AxisUtils.permuteForImagePlus( imgRaw, (AxisMetadata) datasetMeta );
//
//		return null;
//	}

}
