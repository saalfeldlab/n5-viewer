package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;

public class N5SingleScaleMetadata implements N5Metadata {

    public final AffineTransform3D transform;

    public N5SingleScaleMetadata(final AffineTransform3D transform)
    {
        this.transform = transform;
    }
}
