package org.janelia.saalfeldlab.n5.bdv;

import org.janelia.saalfeldlab.n5.N5Reader;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class N5DatasetDiscoverer {

    public static N5TreeNode run(final N5Reader n5) throws IOException
    {
        final N5TreeNode root = new N5TreeNode("/", "/");
        discover(n5, root);
        trim(root);
        return root;
    }

    private static void discover(final N5Reader n5, final N5TreeNode node) throws IOException
    {
        if (n5.datasetExists(node.path)) {
            node.isDataset = true;
        } else {
            for (final String childGroup : n5.list(node.path)) {
                final String childPath = Paths.get(node.path, childGroup).toString();
                final N5TreeNode childNode = new N5TreeNode(childPath, childGroup);
                node.children.add(childNode);
                discover(n5, childNode);
                node.isMultiscale = isMultiscale(node);
            }
        }
    }

    /**
     * Removes branches of the N5 container tree that do not contain datasets.
     *
     * @param node
     * @return
     *      {@code true} if the branch contains a dataset, {@code false} otherwise
     */
    private static boolean trim(final N5TreeNode node)
    {
        if (node.children.isEmpty())
            return node.isDataset;

        boolean ret = false;
        for (final Iterator<N5TreeNode> it = node.children.iterator(); it.hasNext();)
        {
            final N5TreeNode childNode = it.next();
            if (!trim(childNode))
                it.remove();
            else
                ret = true;
        }
        return ret;
    }

    public static DefaultMutableTreeNode toJTreeNode(final N5TreeNode n5Node)
    {
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(n5Node);
        for (final N5TreeNode n5ChildNode : n5Node.children)
            node.add(toJTreeNode(n5ChildNode));
        return node;
    }

    private static boolean isMultiscale(final N5TreeNode node)
    {
        final Set<String> childrenSet = new HashSet<>();
        for (final N5TreeNode childNode : node.children)
        {
            if (!childNode.isDataset)
                return false;
            childrenSet.add(childNode.groupName);
        }

        for (int i = 0; i < childrenSet.size(); ++i)
            if (!childrenSet.contains("s" + i))
                return false;

        return true;
    }
}
