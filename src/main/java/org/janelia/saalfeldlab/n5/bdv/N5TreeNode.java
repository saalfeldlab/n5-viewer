package org.janelia.saalfeldlab.n5.bdv;

import java.util.ArrayList;
import java.util.List;

public class N5TreeNode {

    public final String path;
    public final String groupName;
    public final List<N5TreeNode> children = new ArrayList<>();
    public boolean isDataset;

    public N5TreeNode(final String path, final String groupName) {
        this.path = path;
        this.groupName = groupName;
    }

    @Override
    public String toString() {
        return groupName;
    }
}