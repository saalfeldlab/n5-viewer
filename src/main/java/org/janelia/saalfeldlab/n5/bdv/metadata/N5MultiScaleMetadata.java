package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;

public class N5MultiScaleMetadata implements N5Metadata {

    public final String[] paths;

    public final AffineTransform3D[] transforms;

    public N5MultiScaleMetadata(final String[] paths, final AffineTransform3D[] transforms)
    {
        this.paths = paths;
        this.transforms = transforms;
    }
}
