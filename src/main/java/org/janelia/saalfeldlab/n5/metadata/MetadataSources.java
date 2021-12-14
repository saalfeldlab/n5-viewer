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
