/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.n5.bdv;

import ij.IJ;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DatasetSelectorDialog
{
    private static class SelectedListElement
    {
        private final String path;
        private final N5ViewerDataSeleciton.SelectedDataset selectedDataset;

        SelectedListElement(final String path, final N5ViewerDataSeleciton.SelectedDataset selectedDataset)
        {
            this.path = path;
            this.selectedDataset = selectedDataset;
        }

        @Override
        public String toString()
        {
            return path + (selectedDataset instanceof N5ViewerDataSeleciton.MultiScaleDataset ? " (multiscale)" : "");
        }
    }

    private Consumer<N5ViewerDataSeleciton> okCallback;

    private JFrame dialog;
    private JTextField containerPathTxt;

    private JTree containerTree;
    private JList datasetsList;

    private JButton addSourceBtn;
    private JButton removeSourceBtn;

    private JButton okBtn;

    private DefaultTreeModel treeModel;
    private DefaultListModel listModel;

    private String lastBrowsePath;
    private String n5Path;
    private N5Reader n5;
    private N5TreeNode selectedNode;

    public void run(final Consumer<N5ViewerDataSeleciton> okCallback)
    {
        this.okCallback = okCallback;

        dialog = new JFrame("N5 Viewer");
        dialog.setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.PAGE_AXIS));
        dialog.setMinimumSize(new Dimension(750, 500));
        dialog.setPreferredSize(dialog.getMinimumSize());

        final JPanel containerPanel = new JPanel();
        containerPanel.add(new JLabel("N5 container:"));

        containerPathTxt = new JTextField();
        containerPathTxt.setPreferredSize(new Dimension(400, containerPathTxt.getPreferredSize().height));
        containerPanel.add(containerPathTxt);

        final JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> openContainer(this::openBrowseDialog));
        containerPanel.add(browseBtn);

        final JButton linkBtn = new JButton("Open link...");
        linkBtn.addActionListener(e -> openContainer(this::openLinkDialog));
        containerPanel.add(linkBtn);

        dialog.getContentPane().add(containerPanel);

        final JPanel datasetsPanel = new JPanel();

        treeModel = new DefaultTreeModel(null, true);
        containerTree = new JTree(treeModel);
        containerTree.setEnabled(false);
        containerTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final JScrollPane containerTreeScroller = new JScrollPane(containerTree);
        containerTreeScroller.setMaximumSize(new Dimension(280, 350));
        containerTreeScroller.setPreferredSize(containerTreeScroller.getMaximumSize());
        datasetsPanel.add(containerTreeScroller);

        final JPanel sourceButtonsPanel = new JPanel();
        sourceButtonsPanel.setLayout( new BoxLayout( sourceButtonsPanel, BoxLayout.Y_AXIS ) );
        addSourceBtn = new JButton("Add source");
        addSourceBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        addSourceBtn.setEnabled(false);
        addSourceBtn.addActionListener(e -> addSource());
        sourceButtonsPanel.add(addSourceBtn);

        removeSourceBtn = new JButton("Remove source");
        removeSourceBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        removeSourceBtn.setEnabled(false);
        removeSourceBtn.addActionListener(e -> removeSource());
        sourceButtonsPanel.add(removeSourceBtn);

        datasetsPanel.add(sourceButtonsPanel);

        listModel = new DefaultListModel();
        datasetsList = new JList(listModel);
        datasetsList.setEnabled(false);
        datasetsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final JScrollPane datasetsListScroller = new JScrollPane(datasetsList);
        datasetsListScroller.setMaximumSize(new Dimension(280, 350));
        datasetsListScroller.setPreferredSize(datasetsListScroller.getMaximumSize());
        datasetsPanel.add(datasetsListScroller);

        dialog.getContentPane().add(datasetsPanel);

        final JPanel okButtonPanel = new JPanel();
        okBtn = new JButton("OK");
        okBtn.setEnabled(false);
        okBtn.addActionListener(e -> ok());
        okButtonPanel.add(okBtn);

        dialog.getContentPane().add(okButtonPanel);

        dialog.pack();
        dialog.setVisible(true);
    }

    private String openBrowseDialog()
    {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (lastBrowsePath != null && !lastBrowsePath.isEmpty())
            fileChooser.setCurrentDirectory(new File(lastBrowsePath));
        int ret = fileChooser.showOpenDialog(dialog);
        if (ret != JFileChooser.APPROVE_OPTION)
            return null;
        return fileChooser.getSelectedFile().getAbsolutePath();
    }

    private String openLinkDialog()
    {
        return JOptionPane.showInputDialog(dialog, "Link: ", "N5 Viewer", JOptionPane.PLAIN_MESSAGE);
    }

    private void openContainer(final Supplier<String> opener)
    {
        final String selectedN5Path = opener.get();
        if (selectedN5Path == null || selectedN5Path.isEmpty())
            return;

        lastBrowsePath = Paths.get(selectedN5Path).getParent().toString();

        final DataAccessType type = DataAccessType.detectType(selectedN5Path);
        if (type == null) {
            JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
            return;
        }

        n5 = null;
        try {
            n5 = new DataAccessFactory(type).createN5Reader(selectedN5Path);

            if (!n5.exists("/") || n5.getVersion().equals(new N5Reader.Version(null))) {
                JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (final DataAccessException | IOException e) {
            IJ.handleException(e);
            return;
        }

        this.n5Path = selectedN5Path;
        containerPathTxt.setText(n5Path);
        addSourceBtn.setEnabled(true);

        final N5TreeNode n5RootNode;
        try {
            n5RootNode = N5DatasetDiscoverer.run(n5);
        } catch (final IOException e) {
            IJ.handleException(e);
            return;
        }

        treeModel.setRoot(N5DatasetDiscoverer.toJTreeNode(n5RootNode));

        containerTree.setEnabled(true);
        datasetsList.setEnabled(true);

        containerTree.addTreeSelectionListener(e -> {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) containerTree.getLastSelectedPathComponent();
            selectedNode = (node == null ? null : (N5TreeNode) node.getUserObject());
        });
    }

    private void addSource()
    {
        if (selectedNode != null)
        {
            final N5ViewerDataSeleciton.SelectedDataset selectedDataset = determineDataset(selectedNode);
            if (selectedDataset == null)
            {
                JOptionPane.showMessageDialog(dialog, "Selected N5 node is not a valid source." + System.lineSeparator() +
                        "A valid source can be either a dataset or a multiscale group.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
                return;
            }

            listModel.addElement(new SelectedListElement(selectedNode.path, selectedDataset));
            selectedNode = null;
            containerTree.clearSelection();

            removeSourceBtn.setEnabled(true);
            okBtn.setEnabled(true);
        }
    }

    private N5ViewerDataSeleciton.SelectedDataset determineDataset(final N5TreeNode node)
    {
        if (node.isDataset)
            return new N5ViewerDataSeleciton.SingleScaleDataset(node.path, null);

        final Set<String> childrenSet = new HashSet<>();
        for (final N5TreeNode childNode : node.children)
        {
            if (!childNode.isDataset)
                return null;
            childrenSet.add(childNode.groupName);
        }
        for (int i = 0; i < childrenSet.size(); ++i)
            if (!childrenSet.contains("s" + i))
                return null;

        final List<String> scaleLevelPaths = new ArrayList<>();
        for (int i = 0; i < childrenSet.size(); ++i)
            scaleLevelPaths.add(Paths.get(node.path, "s" + i).toString());

        return new N5ViewerDataSeleciton.MultiScaleDataset(scaleLevelPaths.toArray(new String[0]), null);
    }

    private void removeSource()
    {
        if (datasetsList.isSelectionEmpty())
            return;

        listModel.removeElementAt(datasetsList.getSelectedIndex());
        removeSourceBtn.setEnabled(!listModel.isEmpty());
        okBtn.setEnabled(!listModel.isEmpty());
    }

    private void ok()
    {
        final List<N5ViewerDataSeleciton.SelectedDataset> selectedDatasets = new ArrayList<>();
        for (final Enumeration enumeration = listModel.elements(); enumeration.hasMoreElements();)
            selectedDatasets.add((N5ViewerDataSeleciton.SelectedDataset) enumeration.nextElement());
        okCallback.accept(new N5ViewerDataSeleciton(n5Path, selectedDatasets));
    }
}
