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

import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5Reader;
import bdv.viewer.Source;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public class MetadataSources <T extends NumericType<T> & NativeType<T>> {

	private final N5Reader n5;
	private final N5DatasetMetadata metadata;

	private int nChannels;
	private int channelDim;

	private List<MetadataSource<T>> sources;

	public MetadataSources( N5Reader n5, N5DatasetMetadata metadata ) {
		this.n5 = n5;
		this.metadata = metadata;

		MetadataSource<T> src = new MetadataSource<>( n5, metadata );
		channelDim = src.getChannelDimension();
		nChannels = channelDim < 0 ? 1 : (int)src.getRawImage().dimension( channelDim );
		sources = buildSources();
	}

	public List<MetadataSource<T>> buildSources() {

		sources = new ArrayList<MetadataSource<T>>();
		for( int i = 0; i < nChannels; i++ ) {
			sources.add( new MetadataSource<T>( n5, metadata, channelDim, i ));
		}
		return sources;
	}

	public List<MetadataSource<T>> getSources() {
		return sources;
	}

}
