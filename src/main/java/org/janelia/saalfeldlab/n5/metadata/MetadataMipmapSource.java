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

import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata;

import bdv.util.RandomAccessibleIntervalMipmapSource;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public class MetadataMipmapSource<T extends NumericType<T> & NativeType<T>> extends RandomAccessibleIntervalMipmapSource<T> {

	private N5Reader n5;
	private MultiscaleMetadata<?> metadata;

	private int channelDim;
	private int channelPos;

	private static VoxelDimensions voxelDimensions(
			final MultiscaleMetadata<?> metadata) {

		final double[] unit = {1, 1, 1};
		metadata.spatialTransform3d().apply(unit, unit);
		return new FinalVoxelDimensions(metadata.unit(), unit);
	}

	public MetadataMipmapSource(
			final N5Reader n5,
			final MultiscaleMetadata<?> metadata,
			final int channelDim,
			final int channelPos) {

		super(
				getImgs(n5, metadata),
				getType(n5, metadata),
				metadata.spatialTransforms3d(),
				voxelDimensions(metadata),
				metadata.getName(),
				true);

		this.n5 = n5;
		this.metadata = metadata;

		this.channelDim = channelDim;
		this.channelPos = channelPos;
	}

	public static RandomAccessibleInterval[] getImgs(final N5Reader n5, final MultiscaleMetadata<?> metadata) {

		final int N = metadata.getChildrenMetadata().length;
		final RandomAccessibleInterval[] imgs = new RandomAccessibleInterval[N];
		for (int i = 0; i < N; i++)
			try {
				imgs[i] = N5Utils.open(n5, metadata.getChildrenMetadata()[i].getPath());
			} catch (final N5Exception e) {}

		return imgs;
	}

	public static <T extends NumericType<T> & NativeType<T>> T getType(
			final N5Reader n5,
			final MultiscaleMetadata<?> metadata) {

		CachedCellImg<T, ?> img;
		try {
			img = N5Utils.open(n5, metadata.getChildrenMetadata()[0].getPath());
			return Util.getTypeFromInterval(img);
		} catch (final N5Exception e) {}
		return null;
	}

	public MetadataMipmapSource(final N5Reader n5, final MultiscaleMetadata<?> metadata) {

		this(n5, metadata, -1, 0);
	}

	public MetadataMipmapSource(final N5Reader n5, final MultiscaleMetadata<?> metadata, final int channelPos) {

		this(n5, metadata, -1, channelPos);
	}
}
