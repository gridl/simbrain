package org.simbrain.network;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.ToolTipManager;

import org.simbrain.gauge.GaugeFrame;
import org.simbrain.network.actions.PasteAction;
import org.simbrain.network.dialog.neuron.NeuronDialog;
import org.simbrain.network.dialog.synapse.SynapseDialog;
import org.simbrain.network.nodes.DebugNode;
import org.simbrain.network.nodes.NeuronNode;
import org.simbrain.network.nodes.ScreenElement;
import org.simbrain.network.nodes.SelectionHandle;
import org.simbrain.network.nodes.SelectionMarquee;
import org.simbrain.network.nodes.SubnetworkNode;
import org.simbrain.network.nodes.SynapseNode;
import org.simbrain.util.Comparator;
import org.simbrain.workspace.Workspace;
import org.simnet.interfaces.NetworkEvent;
import org.simnet.interfaces.NetworkListener;
import org.simnet.interfaces.Neuron;
import org.simnet.interfaces.Synapse;
import org.simnet.networks.ContainerNetwork;

import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PNodeFilter;
import edu.umd.cs.piccolo.util.PPaintContext;
import edu.umd.cs.piccolox.handles.PHandle;

/**
 * Network panel.
 */
public final class NetworkPanel extends PCanvas implements NetworkListener, ActionListener {

    /** The model neural-network object. */
    private ContainerNetwork network = new ContainerNetwork();

    /** Default edit mode. */
    private static final EditMode DEFAULT_BUILD_MODE = EditMode.SELECTION;

    /** Build mode. */
    private EditMode editMode;

    /** Selection model. */
    private NetworkSelectionModel selectionModel;

    /** Action manager. */
    private NetworkActionManager actionManager;

    /** Cached context menu. */
    private JPopupMenu contextMenu;

    /** Last clicked position. */
    private Point2D lastClickedPosition;

    /** Tracks number of pastes that have occurred; used to correctly position pasted objects. */
    private double numberOfPastes = 0;

    /** Last selected Neuron. */
    private NeuronNode lastSelectedNeuron = null;

    /** Background color of network panel. */
    private Color backgroundColor = new Color(NetworkPreferences.getBackgroundColor());

    /** Color of all lines in network panel. */
    private Color lineColor = new Color(NetworkPreferences.getLineColor());

    /** Color of "active" neurons, with positive values. */
    private float hotColor = NetworkPreferences.getHotColor();

    /** Color of "inhibited" neurons, with negative values. */
    private float coolColor = NetworkPreferences.getCoolColor();

    /** Color of "excitatory" synapses, with positive values. */
    private Color excitatoryColor = new Color(NetworkPreferences.getExcitatoryColor());

    /** Color of "inhibitory" synapses, with negative values. */
    private Color inhibitoryColor = new Color(NetworkPreferences.getInhibitoryColor());

    /** Color of "spiking" synapse. */
    private Color spikingColor = new Color(NetworkPreferences.getSpikingColor());

    /** Network serializer. */
    private NetworkSerializer serializer;

    /** Temporary storage of persistent nodes; used by Castor. */
    private ArrayList nodeList = new ArrayList();

    /** Label which displays current time. */
    private PText timeLabel = new PText();

    /** Reference to bottom JToolBar. */
    private JToolBar southBar;

    /** Show input labels. */
    private boolean inOutMode = true;

    /** Use auto zoom. */
    private boolean autoZoomMode = true;

    /** Show subnet outline. */
    private boolean showSubnetOutline = false;

    /** Show time. */
    private boolean showTime = true;

    /** How much to nudge objects per key click. */
    private double nudgeAmount = NetworkPreferences.getNudgeAmount();

    /** Whether the xml files should use tabs or not. */
    private boolean usingTabs = true;

    /** Maximum diameter of the circle representing the synapse. */
    private int maxDiameter = NetworkPreferences.getMaxDiameter();

    /** Maximum diameter of the circle representing the synapse. */
    private int minDiameter = NetworkPreferences.getMinDiameter() ;

    /**
     * Create a new network panel.
     */
    public NetworkPanel() {

        super();

        // always render in high quality
        setDefaultRenderQuality(PPaintContext.HIGH_QUALITY_RENDERING);
        setAnimatingRenderQuality(PPaintContext.HIGH_QUALITY_RENDERING);
        setInteractingRenderQuality(PPaintContext.HIGH_QUALITY_RENDERING);

        setBackground(new Color(NetworkPreferences.getBackgroundColor()));

        editMode = DEFAULT_BUILD_MODE;
        selectionModel = new NetworkSelectionModel(this);
        actionManager = new NetworkActionManager(this);
        serializer = new NetworkSerializer(this);

        createContextMenu();

        removeDefaultEventListeners();
        addInputEventListener(new PanEventHandler());
        addInputEventListener(new ZoomEventHandler());
        addInputEventListener(new SelectionEventHandler());
        addInputEventListener(new ContextMenuEventHandler());

        network.addNetworkListener(this);

        selectionModel.addSelectionListener(new NetworkSelectionListener()
            {
                /** @see NetworkSelectionListener */
                public void selectionChanged(final NetworkSelectionEvent e) {
                    updateSelectionHandles(e);
                }
            });

        // just for testing...
        // PNode subnetwork = new SubnetworkNode(this, 100.0d, 100.0d);
        // subnetwork.addChild(new DebugNode(this, 20.0d, 20.0d));
        // subnetwork.addChild(new DebugNode(this, 40.0d, 40.0d));
        // getLayer().addChild(subnetwork);

        // Format the time Label
        // TODO: Make this a node type
        timeLabel.setPickable(false);
        timeLabel.offset(10, getCamera().getHeight() - 20);
        getCamera().addChild(timeLabel);
        updateTimeLabel();

        // register support for tool tips
        // TODO:  might be a memory leak, if not unregistered when the parent frame is removed
        ToolTipManager.sharedInstance().registerComponent(this);

        addKeyListener(new NetworkKeyAdapter(this));

    }


    /**
     * Create and return a new File menu for this network panel.
     *
     * @return a new File menu for this network panel
     */
    JMenu createFileMenu() {

        JMenu fileMenu = new JMenu("File");

        // Create new items submenu
        fileMenu.add(actionManager.getNewNeuronAction());
        //fileMenu.add(createNewNetworkMenu());

        // Open / Close actions
        fileMenu.addSeparator();
        for (Iterator i = actionManager.getOpenCloseActions().iterator(); i.hasNext();) {
            fileMenu.add((Action) i.next());
        }

        // Network preferences
        fileMenu.addSeparator();
        fileMenu.add(actionManager.getShowNetworkPreferencesAction());
        fileMenu.addSeparator();

        // Close
        fileMenu.add(actionManager.getCloseNetworkAction());

        return fileMenu;
    }

    /**
     * Create and return a new Edit menu for this network panel.
     *
     * @return a new Edit menu for this network panel
     */
    JMenu createEditMenu() {

        JMenu editMenu = new JMenu("Edit");

        editMenu.add(actionManager.getCutAction());
        editMenu.add(actionManager.getCopyAction());
        editMenu.add(new PasteAction(this));
        //editMenu.add(actionManager.getClearAction());
        editMenu.addSeparator();
        editMenu.add(actionManager.getClearAction());
        editMenu.add(createSelectionMenu());
        editMenu.addSeparator();
        editMenu.add(createAlignMenu());
        editMenu.add(createSpacingMenu());
        editMenu.addSeparator();
        editMenu.add(actionManager.getClampWeightsAction());
        editMenu.addSeparator();
        editMenu.add(actionManager.getShowIOInfoAction());
        editMenu.add(actionManager.getSetAutoZoomAction());
        editMenu.addSeparator();
        editMenu.add(actionManager.getSetNeuronPropertiesAction());
        editMenu.add(actionManager.getSetSynapsePropertiesAction());

        return editMenu;
    }

    /**
     * Creates a new network JMenu.
     *
     * @return the new network menu
     */
    private JMenu createNewNetworkMenu() {
        JMenu newNetMenu = new JMenu("New Network");
        newNetMenu.add(actionManager.getNewBackpropNetworkAction());
        newNetMenu.add(actionManager.getNewCompetitiveNetworkAction());
        newNetMenu.add(actionManager.getNewElmanNetworkAction());
        newNetMenu.add(actionManager.getNewHopfieldNetworkAction());
        newNetMenu.add(actionManager.getNewWTANetworkAction());
        return newNetMenu;
    }

    /**
     * Create a selection JMenu.
     *
     * @return the selection menu.
     */
    public JMenu createSelectionMenu() {
        JMenu selectionMenu = new JMenu("Select");
        selectionMenu.add(actionManager.getSelectAllAction());
        selectionMenu.add(actionManager.getSelectAllWeightsAction());
        selectionMenu.add(actionManager.getSelectAllNeuronsAction());
        return selectionMenu;
    }

    /**
     * Create and return a new Gauge menu for this network panel.
     *
     * @return a new Gauge menu for this network panel
     */
    JMenu createGaugeMenu() {
        JMenu gaugeMenu = new JMenu("Gauge");
        gaugeMenu.add(actionManager.getAddGaugeAction());
        return gaugeMenu;
    }

    /**
     * Create and return a new Help menu for this network panel.
     *
     * @return a new Help menu for this network panel
     */
    JMenu createHelpMenu() {

        JMenu helpMenu = new JMenu("Help");

        // add actions
        helpMenu.add(actionManager.getShowHelpAction());

        return helpMenu;
    }

    /**
     * Create a new context menu for this network panel.
     */
    private void createContextMenu() {

        contextMenu = new JPopupMenu();

        contextMenu.add(actionManager.getNewNeuronAction());
        //contextMenu.add(createNewNetworkMenu());
        contextMenu.addSeparator();

        contextMenu.add(actionManager.getCutAction());
        contextMenu.add(actionManager.getCopyAction());
        contextMenu.add(actionManager.getPasteAction());
        contextMenu.addSeparator();

        contextMenu.add(actionManager.getShowNetworkPreferencesAction());
    }

    /**
     * Return the context menu for this network panel.
     *
     * <p>
     * This context menu should return actions that are appropriate for the
     * network panel as a whole, e.g. actions that change modes, actions that
     * operate on the selection, actions that add new components, etc.  Actions
     * specific to a node of interest should be built into a node-specific context
     * menu.
     * </p>
     *
     * @return the context menu for this network panel
     */
    JPopupMenu getContextMenu() {
        return contextMenu;
    }

    /**
     * Create the top tool bar.
     *
     * @return the toolbar.
     */
    protected JToolBar createTopToolBar() {

        JToolBar topTools = new JToolBar();

        for (Iterator i = actionManager.getNetworkModeActions().iterator(); i.hasNext();) {
            topTools.add((Action) i.next());
        }
        topTools.addSeparator();
        topTools.add(actionManager.getIterateNetworkAction());
        topTools.add(new ToggleButton(actionManager.getNetworkControlActions()));
        topTools.addSeparator();
        topTools.add(actionManager.getClearNeuronsAction());
        topTools.add(actionManager.getRandomizeObjectsAction());
        topTools.addSeparator();
        topTools.add(actionManager.getAddGaugeAction());
        topTools.addSeparator();
        topTools.add(new ToggleButton(actionManager.getInteractionModeActions()));

        return topTools;
    }

    /**
     * Create the bottom tool bar.
     *
     * @return the toolbar.
     */
    protected JToolBar createBottomToolBar() {

        JToolBar bottomTools = new JToolBar();

        for (Iterator i = actionManager.getNetworkEditingActions().iterator(); i.hasNext();) {
            bottomTools.add((Action) i.next());
        }

        return bottomTools;
    }

    /**
     * Return the align sub menu.
     *
     * @return the align sub menu
     */
    public JMenu createAlignMenu() {

        JMenu alignSubMenu = new JMenu("Align");

        alignSubMenu.add(actionManager.getAlignHorizontalAction());
        alignSubMenu.add(actionManager.getAlignVerticalAction());

        return alignSubMenu;

    }

    /**
     * Return the space sub menu.
     *
     * @return the space sub menu
     */
    public JMenu createSpacingMenu() {

        JMenu spaceSubMenu = new JMenu("Space");

        spaceSubMenu.add(actionManager.getSpaceHorizontalAction());
        spaceSubMenu.add(actionManager.getSpaceVerticalAction());

        return spaceSubMenu;

    }

    /**
     * Remove the default event listeners.
     */
    private void removeDefaultEventListeners() {

        PInputEventListener panEventHandler = getPanEventHandler();
        PInputEventListener zoomEventHandler = getZoomEventHandler();
        removeInputEventListener(panEventHandler);
        removeInputEventListener(zoomEventHandler);
    }


    //
    // bound properties

    /**
     * Return the current edit mode for this network panel.
     *
     * @return the current edit mode for this network panel
     */
    public EditMode getEditMode() {
        return editMode;
    }

    /**
     * Set the current edit mode for this network panel to <code>editMode</code>.
     *
     * <p>This is a bound property.</p>
     *
     * @param editMode edit mode for this network panel, must not be null
     */
    public void setEditMode(final EditMode editMode) {

        if (editMode == null) {
            throw new IllegalArgumentException("editMode must not be null");
        }

        EditMode oldEditMode = this.editMode;
        this.editMode = editMode;
        firePropertyChange("editMode", oldEditMode, this.editMode);
        setCursor(this.editMode.getCursor());
    }

    /**
     * Delete selected itemes.
     */
    public void deleteSelectedObjects() {

        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode selectedNode = (PNode) i.next();

            if (selectedNode instanceof NeuronNode) {
                NeuronNode selectedNeuronNode = (NeuronNode) selectedNode;
                network.deleteNeuron(selectedNeuronNode.getNeuron());
            }
            else if (selectedNode instanceof SynapseNode) {
                SynapseNode selectedSynapseNode = (SynapseNode) selectedNode;
                network.deleteWeight(selectedSynapseNode.getSynapse());
            }
            else {
                getLayer().removeChild(selectedNode);
            }
        }
    }

    /**
     * Copy to the clipboard.
     */
    public void copy() {

        Clipboard.clear();
        numberOfPastes = 0;

        //List toCopy = new ArrayList();
        ArrayList toCopy = new ArrayList();

        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode selectedNode = (PNode) i.next();
            if (Clipboard.canBeCopied(selectedNode, this)) {
                toCopy.add(selectedNode);
            }
        }

        Clipboard.add(toCopy);
    }

    /**
     * Cut to the clipboard.
     */
    public void cut() {
        copy();
        deleteSelectedObjects();
    }

    /**
     * Paste from the clipboard.
     */
    public void paste() {
        Clipboard.paste(this);
        numberOfPastes++;
    }

    /**
     * Aligns neurons horizontally.
     */
    public void alignHorizontal() {
        Iterator i = getSelectedNeurons().iterator();
        double min = Double.MAX_VALUE;

        while (i.hasNext()) {
            NeuronNode node = (NeuronNode) i.next();
            NeuronNode n = (NeuronNode) node;

            if (n.getGlobalBounds().getY() < min) {
                min = n.getGlobalBounds().getY();
            }
        }

        i = getSelectedNeurons().iterator();

        while (i.hasNext()) {
            NeuronNode node = (NeuronNode) i.next();
            NeuronNode n = (NeuronNode) node;
            n.setOffset(n.getGlobalBounds().getX(), min);
        }

        repaint();
    }

    /**
     * Aligns neurons vertically.
     */
    public void alignVertical() {
        Iterator i = getSelectedNeurons().iterator();
        double min = Double.MAX_VALUE;

        while (i.hasNext()) {
            NeuronNode node = (NeuronNode) i.next();
            NeuronNode n = (NeuronNode) node;

            if (n.getGlobalBounds().getX() < min) {
                min = n.getGlobalBounds().getX();
            }
        }

        i = getSelectedNeurons().iterator();

        while (i.hasNext()) {
            NeuronNode node = (NeuronNode) i.next();
            NeuronNode n = (NeuronNode) node;
            n.setOffset(min, n.getGlobalBounds().getY());
        }

        repaint();
    }

    /**
     * Spaces neurons horizontally.
     */
    public void spaceHorizontal() {
        if (getSelectedNeurons().size() <= 1) {
            return;
        }

        ArrayList sortedNeurons = getSelectedNeurons();
        Collections.sort(sortedNeurons, new Comparator(Comparator.COMPARE_X));

        double min = ((NeuronNode) sortedNeurons.get(0)).getGlobalBounds().getX();
        double max = ((NeuronNode) sortedNeurons.get(sortedNeurons.size() - 1)).getGlobalBounds().getX();
        double space = (max - min) / (sortedNeurons.size() - 1);

        for (int j = 0; j < sortedNeurons.size(); j++) {
            NeuronNode n = (NeuronNode) sortedNeurons.get(j);
            n.setOffset(min + (space * j), n.getGlobalBounds().getY());
        }

        repaint();
    }

    /**
     * Spaces neurons vertically.
     */
    public void spaceVertical() {
        if (getSelectedNeurons().size() <= 1) {
            return;
        }

        ArrayList sortedNeurons = getSelectedNeurons();
        Collections.sort(sortedNeurons, new Comparator(Comparator.COMPARE_Y));

        double min = ((NeuronNode) sortedNeurons.get(0)).getGlobalBounds().getY();
        double max = ((NeuronNode) sortedNeurons.get(sortedNeurons.size() - 1)).getGlobalBounds().getY();
        double space = (max - min) / (sortedNeurons.size() - 1);

        for (int j = 0; j < sortedNeurons.size(); j++) {
            NeuronNode n = (NeuronNode) sortedNeurons.get(j);
            n.setOffset(n.getGlobalBounds().getX(), min + (space * j));
        }

        repaint();
    }

    /**
     * Creates and displays the neuron properties dialog.
     *
     */
    public void showSelectedNeuronProperties() {

        NeuronDialog dialog = new NeuronDialog(getSelectedNeurons());
        dialog.pack();
        dialog.setVisible(true);

    }

    /**
     * Creates and displays the synapse properties dialog.
     */
    public void showSelectedSynapseProperties() {

        SynapseDialog dialog = new SynapseDialog(getSelectedSynapses());
        dialog.pack();
        dialog.setVisible(true);

    }

    /**
     * Clear the selection.
     */
    public void clearSelection() {
        selectionModel.clear();
    }

    /**
     * Select all elements.
     */
    public void selectAll() {
        setSelection(getPersistentNodes());
    }

    /**
     * Return true if the selection is empty.
     *
     * @return true if the selection is empty
     */
    public boolean isSelectionEmpty() {
        return selectionModel.isEmpty();
    }

    /**
     * Return true if the specified element is selected.
     *
     * @param element element
     * @return true if the specified element is selected
     */
    public boolean isSelected(final Object element) {
        return selectionModel.isSelected(element);
    }

    /**
     * Return the selection as a collection of selected elements.
     *
     * @return the selection as a collection of selected elements
     */
    public Collection getSelection() {
        return selectionModel.getSelection();
    }

    /**
     * Set the selection to the specified collection of elements.
     *
     * @param elements elements
     */
    public void setSelection(final Collection elements) {
        selectionModel.setSelection(elements);
    }

    /**
     * Toggle the selected state of the specified element; if
     * it is selected, remove it from the selection, if it is
     * not selected, add it to the selection.
     *
     * @param element element
     */
    public void toggleSelection(final Object element) {

        if (isSelected(element)) {
            selectionModel.remove(element);
        } else {
            selectionModel.add(element);
        }
    }

    /**
     * Add the specified network selection listener.
     *
     * @param l network selection listener to add
     */
    public void addSelectionListener(final NetworkSelectionListener l) {
        selectionModel.addSelectionListener(l);
    }

    /**
     * Remove the specified network selection listener.
     *
     * @param l network selection listener to remove
     */
    public void removeSelectionListener(final NetworkSelectionListener l) {
        selectionModel.removeSelectionListener(l);
    }

    /**
     * Update selection handles.
     *
     * @param event the NetworkSelectionEvent
     */
    private void updateSelectionHandles(final NetworkSelectionEvent event) {

        Set selection = event.getSelection();
        Set oldSelection = event.getOldSelection();

        Set difference = new HashSet(oldSelection);
        difference.removeAll(selection);

        for (Iterator i = difference.iterator(); i.hasNext();) {
            PNode node = (PNode) i.next();
            SelectionHandle.removeSelectionHandleFrom(node);
        }
        for (Iterator i = selection.iterator(); i.hasNext();) {
            PNode node = (PNode) i.next();
            if (node instanceof ScreenElement) {
                ScreenElement screenElement = (ScreenElement) node;
                if (screenElement.showSelectionHandle()) {
                    SelectionHandle.addSelectionHandleTo(node);
                }
            }
        }
    }

    /**
     * Filters all but NeuronNodes.
     */
    private class NeuronFilter
        implements PNodeFilter {

        /** @see PNodeFilter */
        public boolean accept(final PNode node) {

            boolean isNeuron = (node instanceof NeuronNode);

            return isNeuron;
        }

        /** @see PNodeFilter */
        public boolean acceptChildrenOf(final PNode node) {
            return true;
        }
    }

    /**
     * Filters all but persistable items.
     */
    private class PersistentFilter
        implements PNodeFilter {

        /** @see PNodeFilter */
        public boolean accept(final PNode node) {

            boolean isNeuron = (node instanceof NeuronNode);
            boolean isSynapse = (node instanceof SynapseNode);

            return (isNeuron || isSynapse);
        }

        /** @see PNodeFilter */
        public boolean acceptChildrenOf(final PNode node) {
            return true;
        }
    }

    /**
     * Returns selected Neurons.
     *
     * @return list of selectedNeurons
     */
    public ArrayList getSelectedNeurons() {
        ArrayList ret = new ArrayList();
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode e = (PNode) i.next();
            if (e instanceof NeuronNode) {
                ret.add(e);
            }
        }
        return ret;
    }

    /**
     * Returns selected Synapses.
     *
     * @return list of selected Synapses
     */
    public ArrayList getSelectedSynapses() {
        ArrayList ret = new ArrayList();
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode e = (PNode) i.next();
            if (e instanceof SynapseNode) {
                ret.add(e);
            }
        }
        return ret;
    }

    /**
     * Returns selected Neurons.
     *
     * @return list of selectedNeurons
     */
    public Collection getSelectedModelNeurons() {
        Collection ret = new ArrayList();
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode e = (PNode) i.next();
            if (e instanceof NeuronNode) {
                ret.add(((NeuronNode) e).getNeuron());
            }
        }
        return ret;
    }

    /**
     * Returns model network elements corresponding to selected screen elements.
     *
     * @return list of selected  model elements
     */
    public Collection getSelectedModelElements() {
        Collection ret = new ArrayList();
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode e = (PNode) i.next();
            if (e instanceof NeuronNode) {
                ret.add(((NeuronNode) e).getNeuron());
            } else if (e instanceof SynapseNode) {
                ret.add(((SynapseNode) e).getSynapse());
            }
        }
        return ret;
    }
    
    /**
     * Returns model network elements corresponding to selected screen elements.
     *
     * @return list of selected  model elements
     */
    public Collection getCoupledNodes() {
        Collection ret = new ArrayList();
        for (Iterator i = getNeuronNodes().iterator(); i.hasNext();) {
            NeuronNode node = (NeuronNode) i.next();
            if (node.getNeuron().isInput() || node.getNeuron().isOutput()) {
                ret.add(node);
            }
        }
        return ret;
    }

    /**
     * Returns synapse nodes.
     *
     * @return list of synapse nodes
     */
    public Collection getSynapseNodes() {
        Collection ret = new ArrayList();
        for (Iterator i = getLayer().getAllNodes().iterator(); i.hasNext();) {
            PNode e = (PNode) i.next();
            if (e instanceof SynapseNode) {
                ret.add(e);
            }
        }
        return ret;
    }

    /**
     * Returns all Neurons.
     *
     * @return list of NeuronNodes
     */
    public Collection getNeuronNodes() {
        return getLayer().getAllNodes(new NeuronFilter(), null);
    }

    /**
     * Returns all Neurons.
     *
     * @return list of NeuronNodes
     */
    public Collection getPersistentNodes() {
        return getLayer().getAllNodes(new PersistentFilter(), null);
    }

    /**
     * Update the time representation.
     */
    public void updateTimeLabel() {
        timeLabel.setText(network.getTimeLabel());
    }

    /**
     * Called by network preferences as preferences are changed.  Iterates through screen elemenets
     * and resets relevant colors.
     */
    public void resetColors() {
        for (Iterator i = getLayer().getChildrenIterator(); i.hasNext();) {
            Object obj = i.next();
            if (obj instanceof ScreenElement) {
                ((ScreenElement) obj).resetColors();
            }
        }
        repaint();
    }

    /**
     * Called by network preferences as preferences are changed.  Iterates through screen elemenets
     * and resets relevant colors.
     */
    public void resetSynapseDiameters() {
        for (Iterator i = getSynapseNodes().iterator(); i.hasNext();) {
            SynapseNode synapse = (SynapseNode) i.next();
            synapse.updateDiameter();
        }
        repaint();
    }

    /**
     * Returns information about the network in String form.
     *
     * @return String description about this NeuronNode.
     */
    public String toString() {
        String ret = new String();
        for (Iterator i = getPersistentNodes().iterator(); i.hasNext();) {
            ret += ((PNode) i.next()).toString();
        }
        return ret;
    }

    /**
     * @return Returns the network.
     */
    public ContainerNetwork getNetwork() {
        return network;
    }

    /**
     * Used by Castor.
     *
     * @param network The network to set.
     */
    public void setNetwork(final ContainerNetwork network) {
        this.network = network;
    }

    /**
     * @return Returns the lastClickedPosition.
     */
    public Point2D getLastClickedPosition() {
        return lastClickedPosition;
    }

    /**
     * Centers the neural network in the middle of the PCanvas.
     */
    public void centerCamera() {
        PCamera camera = getCamera();

        if (autoZoomMode && editMode.isSelection()) {
            //PBounds filtered = filteredUnionOfChildBounds(layer, filter);
            PBounds filtered = getLayer().getFullBounds();
            PBounds adjustedFiltered = new PBounds(filtered.getX() - 20, filtered.getY() - 20,
                    filtered.getWidth() + 40, filtered.getHeight() + 40);

            camera.animateViewToCenterBounds(adjustedFiltered, true, 0);
        }

    }

    /**
     * Calculate the union of child bounds for the specified node
     * after applying the specified filter.
     *
     * @param node node
     * @param filter filter
     * @return filtered union of child bounds
     */
    private PBounds filteredUnionOfChildBounds(final PNode node, final PNodeFilter filter) {

        PBounds b = new PBounds();

        if (node.getChildrenCount() == 0) {
            if (filter.accept(node)) {
                b.add(node.getFullBounds());
            }
        }
        else {
            if (filter.acceptChildrenOf(node)) {
                for (Iterator i = node.getChildrenIterator(); i.hasNext(); ) {
                    PNode child = (PNode) i.next();
                    b.add(filteredUnionOfChildBounds(child, filter));
                }
            }
        }
        return b;
    }

    /** Node filter that rejects selection handle and marquee nodes. */
    private final PNodeFilter filter = new PNodeFilter() {

            /** @see PNodeFilter */
            public boolean accept(final PNode node) {
                return (!((node instanceof SelectionMarquee) || (node instanceof PHandle)));
            }

            /** @see PNodeFilter */
            public boolean acceptChildrenOf(final PNode node) {
                return true;
            }
        };

    /**
     * @param lastClickedPosition The lastClickedPosition to set.
     */
    public void setLastClickedPosition(final Point2D lastLeftClicked) {
        // If left clicking somewhere assume not multiple pasting.
        setNumberOfPastes(0);
        this.lastClickedPosition = lastLeftClicked;
    }

    /**
     * Returns a reference to the workspace.
     *
     * @return a reference to the workspace
     */
    public Workspace getWorkspace() {
        return (Workspace) getTopLevelAncestor();
    }

    /** @see NetworkListener. */
    public void modelCleared(final NetworkEvent e) {
        // TODO Auto-generated method stub
    }

    /** @see NetworkListener. */
    public void neuronAdded(final NetworkEvent e) {

        Point2D p;
        // If a node is selected, put this node to its left
        if (getSelectedNeurons().size() == 1) {
            NeuronNode node = (NeuronNode) getSelectedNeurons().toArray()[0];
            p = new Point((int) node.getOffset().getX() + 45, (int) node.getOffset().getY());
        } else {
            // Put nodes at last left clicked position, if any
            p = getLastClickedPosition();
            if (p == null) {
                p = new Point(100,100);
            }
        }

        NeuronNode node = new NeuronNode(this, e.getNeuron(), p.getX(), p.getY());
        getLayer().addChild(node);
        selectionModel.setSelection(Collections.singleton(node));
        getNetworkFrame().setChangedSinceLastSave(true);
    }

    /** @see NetworkListener. */
    public void neuronRemoved(final NetworkEvent e) {
        NeuronNode node = findNeuronNode(e.getNeuron());
        getLayer().removeChild(node);
        getNetworkFrame().setChangedSinceLastSave(true);
    }

    /** @see NetworkListener. */
    public void neuronChanged(final NetworkEvent e) {
        NeuronNode node = findNeuronNode(e.getOldNeuron());
        node.setNeuron(e.getNeuron());
        node.update();
        getNetworkFrame().setChangedSinceLastSave(true);
        resetColors();
    }

    /** @see NetworkListener. */
    public void synapseAdded(final NetworkEvent e) {
        NeuronNode source = findNeuronNode(e.getSynapse().getSource());
        NeuronNode target = findNeuronNode(e.getSynapse().getTarget());
        SynapseNode node = new SynapseNode(this, source, target, e.getSynapse());
        getLayer().addChild(node);
        node.moveToBack();
        getNetworkFrame().setChangedSinceLastSave(true);
    }

    /** @see NetworkListener. */
    public void synapseRemoved(final NetworkEvent e) {
        SynapseNode toDelete = findSynapseNode(e.getSynapse());
        if (toDelete != null) {
            toDelete.getTarget().getConnectedSynapses().remove(toDelete);
            toDelete.getSource().getConnectedSynapses().remove(toDelete);
            getLayer().removeChild(toDelete);
        }
        getNetworkFrame().setChangedSinceLastSave(true);
    }

    /** @see NetworkListener. */
    public void subnetAdded(final NetworkEvent e) {
//        SubnetworkNode subnetwork = new SubnetworkNode(this, 0, 80);
//        for (Iterator neurons = e.getSubnet().getNeuronList().iterator(); neurons.hasNext();) {
//            Neuron neuron = (Neuron) neurons.next();
//            NeuronNode node = findNeuronNode(neuron);
//            subnetwork.addChild(node);
//        }
//        this.getLayer().addChild(subnetwork);
    }

    /** @see NetworkListener. */
    public void subnetRemoved(final NetworkEvent e) {
        System.out.println("this subnetwork removed:\n" + e.getSubnet());
    }

    /** @see NetworkListener. */
    public void couplingChanged(final NetworkEvent e) {
        NeuronNode changed = findNeuronNode(e.getNeuron());
        changed.updateInLabel();
        changed.updateOutLabel();
        getNetworkFrame().setChangedSinceLastSave(true);
    }

    /** @see NetworkListener. */
    public void synapseChanged(final NetworkEvent e) {
        findSynapseNode(e.getOldSynapse()).setSynapse(e.getSynapse());
        getNetworkFrame().setChangedSinceLastSave(true);
        resetColors();     
    }

    /**
     * Find the NeuronNode corresponding to a given model Neuron.
     *
     * @param n the model neuron.
     * @return the correonding NeuronNode.
     */
    public NeuronNode findNeuronNode(final Neuron n) {
        for (Iterator i = getNeuronNodes().iterator(); i.hasNext();) {
            NeuronNode node = ((NeuronNode) i.next());
            if (n == node.getNeuron()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Find the SynapseNode corresponding to a given model Synapse.
     *
     * @param s the model synapse.
     * @return the corresponding SynapseNode.
     */
    public SynapseNode findSynapseNode(final Synapse s) {
        for (Iterator i = getSynapseNodes().iterator(); i.hasNext();) {
            SynapseNode node = ((SynapseNode) i.next());
            if (s == node.getSynapse()) {
                return node;
            }
        }
        return null;
    }
    /**
     * @param lastSelectedNeuron The lastSelectedNeuron to set.
     */
    public void setLastSelectedNeuron(final NeuronNode lastSelectedNeuron) {
        this.lastSelectedNeuron = lastSelectedNeuron;
    }

    /**
     * @return Returns the lastSelectedNeuron.
     */
    public NeuronNode getLastSelectedNeuron() {
        return lastSelectedNeuron;
    }

    /**
     * Set the background color, store it to user preferences, and repaint the
     * panel.
     *
     * @param clr
     *            new background color for network panel
     */
    public void setBackgroundColor(final Color clr) {
        backgroundColor = clr;
        setBackground(backgroundColor);
        repaint();
    }

    /**
     * Get the background color.
     *
     * @return the background color
     */
    public Color getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Show the dialog for opening a network.
     */
    public void showOpenFileDialog() {
        serializer.showOpenFileDialog();
    }

    /**
     * Open the specified network.
     *
     * @param file the file describing the network to open
     */
    public void openNetwork(final File file) {
        serializer.readNetwork(file);
        getNetworkFrame().setChangedSinceLastSave(false);
    }

    /**
     * Show the dialog for saving a network.
     */
    public void showSaveFileDialog() {
        serializer.showSaveFileDialog();
        getNetworkFrame().setChangedSinceLastSave(false);
    }

    /**
     * Save the current network.
     */
    public void saveCurrentNetwork() {
        if (serializer.getCurrentFile() == null) {
            showSaveFileDialog();
        } else {
            serializer.writeNet(serializer.getCurrentFile());
        }
        getNetworkFrame().setChangedSinceLastSave(false);
    }

    /**
     * Save network to specified file.
     *
     * @param networkFile the file to save the network to.
     */
    public void saveNetwork(final File networkFile) {
        serializer.writeNet(networkFile);
    }

    /**
     * Return height bottom toolbar is taking up.
     *
     * @return height bottom toolbar is taking up
     */
    private double getToolbarOffset() {
        if (southBar != null) {
            return southBar.getHeight();
        }
        return 0;
    }

    /** @see PCanvas. */
    public void repaint() {
        super.repaint();

        if (timeLabel != null) {
            timeLabel.setBounds(10, getCamera().getHeight() - getToolbarOffset(), timeLabel.getHeight(), timeLabel.getWidth());
        }

        if ((network != null) && (getLayer().getChildrenCount() > 0)
                && (!editMode.isPan())) {
            centerCamera();
        }
    }

    /**
     * @return Auto zoom mode.
     */
    public boolean getAutoZoomMode() {
        return autoZoomMode;
    }

    /**
     * @param autoZoomMode Auto zoom mode.
     */
    public void setAutoZoomMode(final boolean autoZoomMode) {
        this.autoZoomMode = autoZoomMode;
        repaint();
    }

    /**
     * Used by Castor.
     *
     * @return temporary list of persistable PNodes
     */
    public ArrayList getNodeList() {
        return nodeList;
    }

    /**
     * Used by Castor.
     *
     * @param list temporary list of persistable PNodes
     */
    public void setNodeList(final ArrayList list) {
        nodeList = list;
    }

    /**
     * @return a reference to the parent network frame
     */
    public NetworkFrame getNetworkFrame() {
        return ((NetworkFrame) getRootPane().getParent());
    }

    /**
     * @return Returns the coolColor.
     */
    public float getCoolColor() {
        return coolColor;
    }

    /**
     * @param coolColor The coolColor to set.
     */
    public void setCoolColor(float coolColor) {
        this.coolColor = coolColor;
    }

    /**
     * @return Returns the excitatoryColor.
     */
    public Color getExcitatoryColor() {
        return excitatoryColor;
    }

    /**
     * @param excitatoryColor The excitatoryColor to set.
     */
    public void setExcitatoryColor(final Color excitatoryColor) {
        this.excitatoryColor = excitatoryColor;
    }

    /**
     * @return Returns the hotColor.
     */
    public float getHotColor() {
        return hotColor;
    }

    /**
     * @param hotColor The hotColor to set.
     */
    public void setHotColor(final float hotColor) {
        this.hotColor = hotColor;
    }

    /**
     * @return Returns the inhibitoryColor.
     */
    public Color getInhibitoryColor() {
        return inhibitoryColor;
    }

    /**
     * @param inhibitoryColor The inhibitoryColor to set.
     */
    public void setInhibitoryColor(final Color inhibitoryColor) {
        this.inhibitoryColor = inhibitoryColor;
    }

    /**
     * @param inOutMode The in out mode to set.
     */
    public void setInOutMode(final boolean inOutMode) {
        this.inOutMode = inOutMode;
        for (Iterator i = getCoupledNodes().iterator(); i.hasNext();) {
            NeuronNode node = (NeuronNode) i.next();
            node.updateInLabel();
            node.updateOutLabel();
        }
        repaint();
    }

    /**
     * @return Returns the in out mode.
     */
    public boolean getInOutMode() {
        return inOutMode;
    }

    /**
     * @return Returns the lineColor.
     */
    public Color getLineColor() {
        return lineColor;
    }

    /**
     * @param lineColor The lineColor to set.
     */
    public void setLineColor(final Color lineColor) {
        this.lineColor = lineColor;
    }

    /**
     * @return Returns the numberOfPastes.
     */
    public double getNumberOfPastes() {
        return numberOfPastes;
    }

    /**
     * @param numberOfPastes The numberOfPastes to set.
     */
    public void setNumberOfPastes(final double numberOfPastes) {
        this.numberOfPastes = numberOfPastes;
    }

    /**
     * @return Returns show subnet outline.
     */
    public boolean getShowSubnetOutline() {
        return showSubnetOutline;
    }

    /**
     * @param showSubnetOutline Sets Show subnet outline.
     */
    public void setShowSubnetOutline(final boolean showSubnetOutline) {
        this.showSubnetOutline = showSubnetOutline;
    }

    /**
     * @return Returns Show time.
     */
    public boolean getShowTime() {
        return showTime;
    }

    /**
     * @param showTime Sets the show time.
     */
    public void setShowTime(final boolean showTime) {
        this.showTime = showTime;
        timeLabel.setVisible(showTime);
    }

    /**
     * @return Returns the spiking synapse color.
     */
    public Color getSpikingColor() {
        return spikingColor;
    }

    /**
     * @param spikingColor Sets the spiking synapse color.
     */
    public void setSpikingColor(final Color spikingColor) {
        this.spikingColor = spikingColor;
    }

    /**
     * @return Return the nudge amount.
     */
    public double getNudgeAmount() {
        return nudgeAmount;
    }

    /**
     * @param nudgeAmount Sets the nudge amount.
     */
    public void setNudgeAmount(final double nudgeAmount) {
        this.nudgeAmount = nudgeAmount;
    }

    /**
     * @return Returns the isUsingTabs.
     */
    public boolean getUsingTabs() {
        return usingTabs;
    }

    /**
     * @param usingTabs The isUsingTabs to set.
     */
    public void setUsingTabs(final boolean usingTabs) {
        this.usingTabs = usingTabs;
    }

    /**
     * @return Returns the maximum synapse diameter.
     */
    public int getMaxDiameter() {
        return maxDiameter;
    }

    /**
     * @param maxDiameter Sets the maximum synapse diameter.
     */
    public void setMaxDiameter(final int maxDiameter) {
        this.maxDiameter = maxDiameter;
    }

    /**
     * @return Returns the minimum synapse diameter.
     */
    public int getMinDiameter() {
        return minDiameter;
    }

    /**
     * @param minDiameter Sets the minimum synapse diameter.
     */
    public void setMinDiameter(int minDiameter) {
        this.minDiameter = minDiameter;
    }

    /**
     * Update the network, gauges, and world. This is where the main control
     * between components happens. Called by world component (on clicks), and
     * the network-thread.
     */
    public void networkChanged() {
        for (Iterator i = getPersistentNodes().iterator(); i.hasNext();) {
            PNode node = (PNode) i.next();
            if (node instanceof NeuronNode) {
                NeuronNode neuronNode = (NeuronNode) node;
                neuronNode.update();
            }
            else if (node instanceof SynapseNode) {
                SynapseNode synapseNode = (SynapseNode) node;
                synapseNode.updateColor();
                synapseNode.updateDiameter();
            }
        }
        updateTimeLabel();
    }

    /**
     * Returns a string representation of the current directory.
     *
     * @return String representation of current directory
     */
    public String getCurrentDirectory() {
        return serializer.getCurrentDirectory();
    }

    /** @see ActionListener */
    public void actionPerformed(ActionEvent e) {

        Object o = e.getSource();

        if (o instanceof JMenuItem) {
            JMenuItem m = (JMenuItem) o;

            String st = m.getActionCommand();

            // Gauge events
            if (st.startsWith("Gauge:")) {
                // I use the label's text since it is the gauge's name
                GaugeFrame gauge = getWorkspace().getGauge(m.getText());

                if (gauge != null) {
                    gauge.setVariables(getSelectedModelElements(), getNetworkFrame().getTitle());
                    getNetwork().addNetworkListener(gauge);
                    gauge.getGaugePanel().update();
                }
            }
        }
    }

    /**
     * Increases neuron and synapse activation levels.
     */
    public void incrementSelectedObjects() {
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode node = (PNode) i.next();
            if (node instanceof NeuronNode) {
                NeuronNode neuronNode = (NeuronNode) node;
                neuronNode.getNeuron().incrementActivation();
                neuronNode.update();
            }
            else if (node instanceof SynapseNode) {
                SynapseNode synapseNode = (SynapseNode) node;
                synapseNode.getSynapse().incrementWeight();
                synapseNode.updateColor();
                synapseNode.updateDiameter();
            }
        }
    }

    /**
     * Decreases neuron and synapse activation levels.
     */
    public void decrementSelectedObjects() {
        for (Iterator i = getSelection().iterator(); i.hasNext();) {
            PNode node = (PNode) i.next();
            if (node instanceof NeuronNode) {
                NeuronNode neuronNode = (NeuronNode) node;
                neuronNode.getNeuron().decrementActivation();
                neuronNode.update();
            }
            else if (node instanceof SynapseNode) {
                SynapseNode synapseNode = (SynapseNode) node;
                synapseNode.getSynapse().decrementWeight();
                synapseNode.updateColor();
                synapseNode.updateDiameter();
            }
        }
    }

    /**
     * Nudge selected object.
     *
     * @param offsetX amount to nudge in the x direction (multipled by nudgeAmount)
     * @param offsetY amount to nudge in the y direction (multipled by nudgeAmount)
     */
    protected void nudge(final int offsetX, final int offsetY) {
        for (Iterator i = getSelectedNeurons().iterator(); i.hasNext();) {
            NeuronNode node = (NeuronNode) i.next();
            node.offset(offsetX * nudgeAmount, offsetY * nudgeAmount);
            node.setBounds(node.getBounds());
        }
        repaint();
    }

    /**
     * Close model network.
     */
    public void closeNetwork() {
        getNetwork().removeNetworkListener(this);
        getNetwork().close();
    }

    /**
     * Add a gauge which by default gauges all neurons of current network.
     */
    public void addGauge() {
        getWorkspace().addGauge();
        GaugeFrame gauge = getWorkspace().getLastGauge();
        // By default gauge all neurons of the current network
        gauge.setVariables(getNetwork().getNeuronList(), getNetworkFrame().getTitle());
    }

}