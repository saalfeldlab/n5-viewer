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
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DatasetSelectorDialog
{
    public static class SourceSelection {

        public final String sourcePath;
        public final boolean isMultiscale;

        public SourceSelection(final String sourcePath, final boolean isMultiscale) {
            this.sourcePath = sourcePath;
            this.isMultiscale = isMultiscale;
        }

        @Override
        public String toString() {
            return sourcePath;
        }
    }

    public static class Selection {

        public final String n5Path;
        public final List<SourceSelection> sourcePaths;

        public Selection(final String n5Path, final List<SourceSelection> sourcePaths)
        {
            this.n5Path = n5Path;
            this.sourcePaths = sourcePaths;
        }
    }

    private Consumer<Selection> okCallback;

    private JFrame dialog;
    private JTextField containerPathTxt;

    private JTree containerTree;
    private JList datasetsList;

    private JButton addSourceBtn;
    private JButton removeSourceBtn;

    private JButton okBtn;

    private DefaultTreeModel treeModel;
    private DefaultListModel listModel;

    private String n5Path;
    private N5Reader n5;
    private N5TreeNode n5RootNode;

    public void run(final Consumer<Selection> okCallback)
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
        //addSourceBtn.addActionListener(e -> addSource());
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

        //dialog.add(Box.createVerticalGlue());
//        final Box.Filler dialogGlue = (Box.Filler)Box.createVerticalGlue();
//        dialogGlue.changeShape(dialogGlue.getMinimumSize(),
//                new Dimension(0, Short.MAX_VALUE), // make glue greedy
//                dialogGlue.getMaximumSize());
//        dialog.getContentPane().add(dialogGlue);

        dialog.pack();
        dialog.setVisible(true);
    }

    private String openBrowseDialog()
    {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
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
        final String n5Path = opener.get();
        if (n5Path == null || n5Path.isEmpty())
            return;

        final DataAccessType type = DataAccessType.detectType(n5Path);
        if (type == null) {
            JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
            return;
        }

        n5 = null;
        try {
            n5 = new DataAccessFactory(type).createN5Reader(n5Path);

            if (!n5.exists("/") || n5.getVersion().equals(new N5Reader.Version(null))) {
                JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (final DataAccessException | IOException e) {
            IJ.handleException(e);
            return;
        }

        this.n5Path = n5Path;
        containerPathTxt.setText(n5Path);
        addSourceBtn.setEnabled(true);

        try {
            n5RootNode = N5DatasetDiscoverer.run(n5);
        } catch (final IOException e) {
            IJ.handleException(e);
            return;
        }

        treeModel.setRoot(N5DatasetDiscoverer.toJTreeNode(n5RootNode));

        containerTree.setEnabled(true);
        datasetsList.setEnabled(true);


//        tree.addTreeSelectionListener(e -> {
//            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
//            selectedNode = (node == null ? null : (N5TreeNode) node.getUserObject());
//
//            try {
//                if (selectedNode == null || (!selectedNode.isMultiscale && !n5.datasetExists(selectedNode.path))) {
//                    selectedNode = null;
//                    okBtn.setEnabled(false);
//                    return;
//                }
//            } catch (final IOException e1) {
//                IJ.handleException(e1);
//                return;
//            }
//
//            okBtn.setEnabled(true);
//            okBtn.setText(selectedNode.isMultiscale ? "Add multiscale dataset" : "Add dataset");
//        });
    }

    private void onSourceSelected(final SourceSelection selectedSource)
    {
        listModel.addElement(selectedSource);
        removeSourceBtn.setEnabled(true);
        okBtn.setEnabled(true);
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
        final List<SourceSelection> sourcePaths = new ArrayList<>();
        for (final Enumeration enumeration = listModel.elements(); enumeration.hasMoreElements();)
            sourcePaths.add((SourceSelection) enumeration.nextElement());
        okCallback.accept(new Selection(n5Path, sourcePaths));
    }


//    private void select()
//    {
//        if (selectedNode == null)
//            return;
//
//        try {
//            if (!selectedNode.isMultiscale && !n5.datasetExists(selectedNode.path)) {
//                JOptionPane.showMessageDialog(dialog, "Selected group is not a dataset", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
//                return;
//            }
//        } catch (final IOException e) {
//            IJ.handleException(e);
//            return;
//        }
//
//        dialog.setVisible(false);
//        callback.accept(new DatasetSelectorDialog.SourceSelection(selectedNode.path, selectedNode.isMultiscale));
//    }
}
