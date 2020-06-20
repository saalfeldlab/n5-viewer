package org.janelia.saalfeldlab.n5.bdv.metadata;

import com.google.gson.GsonBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.bdv.AffineTransform3DJsonAdapter;

// NB: marker interface
public interface N5Metadata {

    static GsonBuilder getGsonBuilder()
    {
        final GsonBuilder gsonBuilder = new GsonBuilder();
        registerGsonTypeAdapters( gsonBuilder );
        return gsonBuilder;
    }

    static void registerGsonTypeAdapters( final GsonBuilder gsonBuilder )
    {
        gsonBuilder.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );
    }
}
