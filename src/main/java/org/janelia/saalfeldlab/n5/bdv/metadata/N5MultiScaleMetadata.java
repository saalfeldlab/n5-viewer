package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;

import java.util.Objects;

public class N5MultiScaleMetadata implements N5Metadata {

    public final String[] paths;

    public final AffineTransform3D[] transforms;

    public N5MultiScaleMetadata(final String[] paths, final AffineTransform3D[] transforms)
    {
        Objects.requireNonNull(paths);
        Objects.requireNonNull(transforms);
        for (final String path : paths)
            Objects.requireNonNull(path);
        for (final AffineTransform3D transform : transforms)
            Objects.requireNonNull(transform);

        this.paths = paths;
        this.transforms = transforms;
    }
}
