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
package org.janelia.saalfeldlab.n5.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.axes.AxisMetadata;
import org.janelia.saalfeldlab.n5.metadata.axes.AxisSlicer;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class MetadataSource <T extends NumericType<T> & NativeType<T>> implements Source<T> {

	private final N5DatasetMetadata metadata;
	private CachedCellImg<T, ?> imgRaw;

	private int timeDimension;

	private int channelDimension;
	private int channelPos;

	private int nSpaceDims;
	private int nTimeDims;
	private int nChannelDims;
	private int nOtherDims;

	private AxisMetadata axes;

	private AffineTransform3D sourceTransform;

	private boolean isValid;

	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata, int channelDim, int channelPos ) {
		this.metadata = metadata;

		if( metadata instanceof SpatialMetadata )
			sourceTransform = ((SpatialMetadata) metadata).spatialTransform3d();
		else
			sourceTransform = new AffineTransform3D();

		this.channelDimension = channelDim;
		this.channelPos = channelPos;

//		if( metadata instanceof AxisMetadata )
//			inferDimensions((AxisMetadata) metadata, channelDim );
//		else
//			inferDimensions( defaultAxes( metadata ), channelDim );

		if( metadata instanceof AxisMetadata )
			axes = (AxisMetadata) metadata;
		else
			axes = defaultAxes( metadata );

		// TODO what to do if this fails?
		isValid = checkAxes( axes, metadata );

		this.timeDimension = getTimeIndex( axes );
		this.channelDimension = getChannelIndex( axes );

		if( isValid )
		{
			try {
				imgRaw = N5Utils.open(n5, metadata.getPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public MetadataSource( N5Reader n5, N5TreeNode node ) {
		this( n5, (N5DatasetMetadata)node.getMetadata());
	}

	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata ) {
		this( n5, metadata, -1, 0 );
	}

	public MetadataSource( N5Reader n5, N5DatasetMetadata metadata, int channelPos ) {
		this( n5, metadata, -1, channelPos );
	}

	public static List<MetadataSource<?>> buildMetadataSources( N5Reader n5, N5DatasetMetadata metadata ) {

		final MetadataSource<?> src0 = new MetadataSource<>( n5, metadata );
		if( !src0.isValid() )
			return null;

		final int nc = src0.getNumChannels();

		final List<MetadataSource<?>> sources = new ArrayList<>();
		sources.add(src0);

		for( int i = 1; i < nc; i++ ) {
			sources.add( new MetadataSource<>( n5, metadata, i ));
		}

		return sources;
	}

	public boolean isValid() {
		return isValid;
	}

	public int getChannelDimension() {
		return channelDimension;
	}

	public int getNumChannels() {
		return (int)imgRaw.dimension( channelDimension );
	}

	public int getChannelIndex() {
		return channelPos;
	}

	public int getTimeDimension() {
		return timeDimension;
	}

	public CachedCellImg<T, ?> getRawImage() {
		return imgRaw;
	}

	public static int getTimeIndex( AxisMetadata axes )
	{
		int[] idxs = axes.indexesOfType("time");
		if( idxs == null || idxs.length == 0 )
			return -1;
		else
			return idxs[0];
	}
	
	public static int getChannelIndex( AxisMetadata axes )
	{
		int[] idxs = axes.indexesOfType("channel");
		if( idxs == null || idxs.length == 0 )
			return -1;
		else
			return idxs[0];
	}

	public static DefaultAxisMetadata defaultAxes( N5DatasetMetadata meta ) {
		if( meta.getAttributes().getNumDimensions() <=3 &&
				(meta instanceof N5CosemMetadata || meta instanceof N5SingleScaleMetadata ))
			return defaultAxesSpatial( meta );
		else
			return defaultAxesIJ( meta );
	}

	/**
	 * The default axes for dialects that store only spatial data (n5viewer and cosem).
	 *
	 * @param meta the metadata
	 * @return axes
	 */
	public static DefaultAxisMetadata defaultAxesSpatial( N5DatasetMetadata meta ) {

		int nd = meta.getAttributes().getNumDimensions();
		if( nd > 3)
			return null;

		String[] labels = Arrays.stream( new String[] {"x", "y", "z" } ).limit(nd).toArray( String[]::new );
		String[] types = AxisUtils.getDefaultTypes(labels);
		String[] units = Stream.generate( () -> "pixel").limit(nd).toArray( String[]::new );

		return new DefaultAxisMetadata(meta.getPath(), labels, types, units);
	}

	/**
	 * The default axes for imageJ-like dialects with fixed axis-order XYCZT.
	 * 
	 * @param meta the metadata
	 * @return axes
	 */
	public static DefaultAxisMetadata defaultAxesIJ( N5DatasetMetadata meta ) {
		int nd = meta.getAttributes().getNumDimensions();
		String[] labels = Arrays.stream( new String[] {"x", "y", "c", "z", "t" } ).limit(nd).toArray( String[]::new );
		String[] types = AxisUtils.getDefaultTypes(labels);
		String[] units = Stream.generate( () -> "pixel").limit(nd).toArray( String[]::new );
		return new DefaultAxisMetadata(meta.getPath(), labels, types, units);
	}

	public boolean checkAxes( AxisMetadata axes, N5DatasetMetadata metadata ) {

		final long[] dims = metadata.getAttributes().getDimensions();
		final int ndData = dims.length;


		final int nd = axes.getAxisLabels().length;
		if( nd != ndData )
			return false;

		nSpaceDims = 0;
		nTimeDims = 0;
		nChannelDims = 0;
		nOtherDims = 0;

		for( int i = 0; i < nd; i++ ) {
			final String type = axes.getAxisTypes()[i];
			if( type.equals("space") )
				nSpaceDims++;
			else if( type.equals("time") )
				nTimeDims++;
			else if( type.equals("channel") )
				nChannelDims++;
			else
				nOtherDims++;
		}

		if( nSpaceDims > 3 )
			return false;

		if( nTimeDims > 1 )
			return false;

		return true;
	}

	/**
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
		}
		else
		{
			timeDimension = timeDims.get(0);
		}

		if( channelDims.size() > 1 )
			return false;
		else if ( channelDims.size() == 0 ) {
			channelDimension = -1;
		}
		else {
			channelDimension = channelDims.get(0);
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

		AxisSlicer slicer = new AxisSlicer( axes );
		for( int i = 0; i < axes.getAxisLabels().length; i++ )
		{
			final String type = axes.getAxisTypes()[i];
			final String label = axes.getAxisLabels()[i];

			if( type.equals("space"))
				continue;
			else if( type.equals("time"))
				slicer.slice(label, t);
			else if ( type.equals("channel"))
				slicer.slice(label, channelPos);
			else
				slicer.slice(label, 0);
		}

		return slicer.apply(imgRaw);
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
