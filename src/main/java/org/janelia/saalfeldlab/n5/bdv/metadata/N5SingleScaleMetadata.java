package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;

public class N5SingleScaleMetadata implements N5Metadata {

    public final String path;

    public final AffineTransform3D transform;

    public N5SingleScaleMetadata(final String path, final AffineTransform3D transform)
    {
        this.path = path;
        this.transform = transform;
    }
}
