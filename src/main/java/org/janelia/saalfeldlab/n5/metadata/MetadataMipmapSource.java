package org.janelia.saalfeldlab.n5.metadata;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5Source;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public class MetadataMipmapSource <T extends NumericType<T> & NativeType<T>> extends N5Source<T>  {

	private N5Reader n5;
	private MultiscaleMetadata<?> metadata;

	private int channelDim;
	private int channelPos;

	public MetadataMipmapSource( N5Reader n5, MultiscaleMetadata<?> metadata, int channelDim, int channelPos ) {
		super( 
				getType( n5, metadata ),
				metadata.getName(),
				getImgs( n5, metadata ),
				metadata.spatialTransforms3d());
		
		this.n5 = n5;
		this.metadata = metadata;
		
		this.channelDim = channelDim;
		this.channelPos = channelPos;
	}
	
	public static RandomAccessibleInterval[] getImgs( N5Reader n5, MultiscaleMetadata<?> metadata )
	{
		final RandomAccessibleInterval[] imgs = new RandomAccessibleInterval[ metadata.childrenMetadata.length ];
		for( int i = 0; i < metadata.childrenMetadata.length; i++ )
			try {
				imgs[i] = N5Utils.open(n5, metadata.getChildrenMetadata()[i].getPath());
			} catch (IOException e) { }

		return imgs;
	}
	
	public static <T extends NumericType<T> & NativeType<T>> T getType( N5Reader n5, MultiscaleMetadata<?> metadata )
	{
		CachedCellImg<T, ?> img;
		try {
			img = N5Utils.open(n5, metadata.getChildrenMetadata()[0].getPath());
			return Util.getTypeFromInterval(img);
		} catch (IOException e) {
		}
		return null;
	}

	public MetadataMipmapSource( N5Reader n5, MultiscaleMetadata<?> metadata ) {
		this( n5, metadata, -1, 0 );
	}
	
	public MetadataMipmapSource( N5Reader n5, MultiscaleMetadata<?> metadata, int channelPos ) {
		this( n5, metadata, -1, channelPos );
	}

}
