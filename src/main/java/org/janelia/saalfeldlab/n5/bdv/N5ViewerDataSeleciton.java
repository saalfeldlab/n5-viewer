package org.janelia.saalfeldlab.n5.bdv;

import net.imglib2.realtransform.AffineTransform3D;

import java.util.Collections;
import java.util.List;

public class N5ViewerDataSeleciton
{
    public interface SelectedDataset
    {
        // marker interface
    }

    public static class SingleScaleDataset implements SelectedDataset
    {
        public final String path;
        public final AffineTransform3D transform;

        public SingleScaleDataset(final String path, final AffineTransform3D transform)
        {
            this.path = path;
            this.transform = transform;
        }
    }

    public static class MultiScaleDataset implements SelectedDataset
    {
        public final String[] paths;
        public final AffineTransform3D[] transforms;

        public MultiScaleDataset(final String[] paths, final AffineTransform3D[] transforms)
        {
            this.paths = paths;
            this.transforms = transforms;
        }
    }

    public final String n5Path;
    public final List<SelectedDataset> datasets;

    public N5ViewerDataSeleciton(final String n5Path, final List<SelectedDataset> datasets)
    {
        this.n5Path = n5Path;
        this.datasets = Collections.unmodifiableList(datasets);
    }
}
