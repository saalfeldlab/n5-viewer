package org.janelia.saalfeldlab.n5.bdv;

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.N5Reader;

import java.io.IOException;
import java.util.*;

public class N5ViewerDataSelection
{
    public interface DatasetSelector {

        SelectedDataset selectDataset(N5Reader n5, N5TreeNode datasetNode) throws DatasetParsingException, IOException;
    }

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


    public static class DatasetParsingException extends Exception
    {
        public DatasetParsingException() {
            super();
        }

        public DatasetParsingException(String message) {
            super(message);
        }

        public DatasetParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        public DatasetParsingException(Throwable cause) {
            super(cause);
        }
    }


    public final String n5Path;
    public final List<SelectedDataset> datasets;

    public N5ViewerDataSelection(final String n5Path, final List<SelectedDataset> datasets)
    {
        this.n5Path = n5Path;
        this.datasets = Collections.unmodifiableList(datasets);
    }
}
