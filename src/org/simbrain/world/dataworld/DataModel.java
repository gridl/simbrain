package org.simbrain.world.dataworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.simbrain.workspace.Consumer;
import org.simbrain.workspace.Producer;

public class DataModel<E> {

    /** Default initial number of rows. */
    private static final int DEFAULT_ROW_COUNT = 5;

    /** Default initial number of columns. */
    private static final int DEFAULT_COLUMN_COUNT = 5;

    /** The data. */
    private List<List<E>> data = new ArrayList<List<E>>();

    /** Number of columns. */
    private int width = DEFAULT_COLUMN_COUNT;

    /** Number of rows. */
    private int height = DEFAULT_ROW_COUNT;

    /** Iteration mode. */
    private boolean iterationMode = false;

    /** Use last column for iteration. */
    private boolean lastColumnBasedIteration = false;

    /** Randomization upper bound. */
    private int upperBound = 1;

    /** Randomization lower bound. */
    private int lowerBound = 0;

    private int currentRow = 0;

    private List<Listener> listeners = new ArrayList<Listener>();

    /** List of consumers. */
    private ArrayList<Consumer> consumers = new ArrayList<Consumer>();

    /** List of producers. */
    private ArrayList<Producer> producers = new ArrayList<Producer>();

    /** The parent component of this model. */
    private final DataWorldComponent parent;
    
    DataModel(DataWorldComponent parent) {
        this.parent = parent;
        
        for (int i = 0; i < height; i++) {
            data.add((List<E>) newRow());
        }
        
        for (int i = 0; i < width; i++) {
            consumers.add(new ConsumingColumn<E>(this, i));
            producers.add(new ProducingColumn<E>(this, i));
        }
    }

    public DataWorldComponent getParent() {
        return parent;
    }
    
    public void addListener(Listener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
    
    public List<? extends Consumer> getConsumers() {
        return Collections.unmodifiableList(consumers);
    }
    
    public List<? extends Producer> getProducers() {
        return Collections.unmodifiableList(producers);
    }

    private List<E> newRow() {
        ArrayList<E> row = new ArrayList<E>();

        for (int i = 0; i < width; i++) {
            row.add(null);
        }
        return row;
    }

    public void set(int row, int column, E value) {
        data.get(row).set(column, value);
        for (Listener listener : listeners) listener.itemChanged(row, column);
    }

    public E get(int row, int column) {
        return data.get(row).get(column);
    }

    public void set(int column, E value) {
        set(currentRow, column, value);
    }

    public E get(int column) {
        return get(currentRow, column);
    }

   /**
    * @return The lower bound.
    */
    public int getLowerBound() {
        return lowerBound;
    }
    
    /**
     * Sets the lower bound value.
     * 
     * @param lowerBound Value to set
     */
    public void setLowerBound(final int lowerBound) {
        this.lowerBound = lowerBound;
    }

    /**
     * @return The upper bound value.
     */
    public int getUpperBound() {
        return upperBound;
    }

    /**
     * Sets the upper bound value.
     * 
     * @param upperBound Value to set
     */
    public void setUpperBound(final int upperBound) {
        this.upperBound = upperBound;
    }

    public int getCurrentRow() {
        return currentRow;
    }

    public boolean isIterationMode() {
        return iterationMode;
    }
    
    public void setIterationMode(boolean iterationMode) {
        this.iterationMode = iterationMode;
    }
    
    /**
     * @return Returns the columnIteration.
     */
    public boolean isLastColumnBasedIteration() {
        return lastColumnBasedIteration;
    }
    
    /**
     * @param columnIteration The columnIteration to set.
     */
    public void setLastColumnBasedIteration(final boolean columnIteration) {
        lastColumnBasedIteration = columnIteration;
    }

    public void setCurrentRow(int currentRow) {
        this.currentRow = currentRow;
    }

    public void addNewRow() {
        height++;
        data.add(newRow());
        for (Listener listener : listeners) listener.rowAdded(height - 1);
    }

    public void insertNewRow(int at) {
        height++;
        data.add(at, newRow());
        for (Listener listener : listeners) listener.rowAdded(at);
    }
    
    public void addNewColumn() {
        width++;
        for (List<E> row : data) row.add(null);
        consumers.add(new ConsumingColumn<E>(this, width - 1));
        for (Listener listener : listeners) listener.columnAdded(width - 1);
    }

    public void insertNewColumn(int at) {
        width++;
        for (List<E> row : data) row.add(at, null);
        consumers.add(new ConsumingColumn<E>(this, at));
        for (Listener listener : listeners) listener.columnAdded(at);
    }

    public void removeLastRow() {
        height--;
        data.remove(height);
        for (Listener listener : listeners) listener.rowRemoved(height);
    }
    
    public void removeRow(int at) {
        height--;
        data.remove(at);
        for (Listener listener : listeners) listener.rowRemoved(at);
    }
    
    public void removeLastColumn() {
        width--;
        for (List<E> row : data) row.remove(width);
        consumers.add(new ConsumingColumn<E>(this, width));
        for (Listener listener : listeners) listener.columnRemoved(width);
    }
    
    public void removeColumn(int at) {
        width--;
        for (List<E> row : data) row.remove(at);
        consumers.add(new ConsumingColumn<E>(this, at));
        for (Listener listener : listeners) listener.columnRemoved(at);
    }
    
    public int getColumnCount() {
        return width;
    }
    
    public int getRowCount() {
        return height;
    }
    
    /**
     * Fills the table with the given value.
     */
    public void fill(E value) {
        for (List<E> row : data) {
            Collections.fill(row, value);
        }
    }

    /**
     * Same as fill, but only fills the last column.
     */
    public void fillNew(E value) {
        for (List<E> row : data) {
            row.set(width - 1, value);
        }
    }
    
    public void update()
    {
        // TODO
    }
    
    public interface Listener {
        void dataChanged();
        void columnAdded(int column);
        void columnRemoved(int column);
        void rowAdded(int row);
        void rowRemoved(int row);
        void itemChanged(int row, int column);
    }
    
//    /** Persistable form of matrix data. */
//    private String[][] stringMatrixRepresentation;
    
//    /**
//     * {@inheritDoc}
//     */
//    public void preSaveInit() {
//        stringMatrixRepresentation = new
//            String[getModel().getRowCount()][getModel().getColumnCount()];
//        
//        for (int i = 0; i < getModel().getRowCount(); i++) {
//            for (int j = 0; j < getModel().getColumnCount(); j++) {
//                stringMatrixRepresentation[i][j] = new String("" +
//                getModel().getValueAt(i, j));
//            }
//        }
//    }
    
//    /**
//     * {@inheritDoc}
//     */
//    public void postOpenInit() {
//        model = new DefaultTableModel(stringMatrixRepresentation.length,
//            stringMatrixRepresentation[0].length);
//        for (int i = 0; i < stringMatrixRepresentation.length; i++) {
//            for (int j = 0; j < stringMatrixRepresentation[0].length; j++) {
//                model.setValueAt(stringMatrixRepresentation[i][j], i, j);
//            }
//        }
//    }
    
//    /**
//     * Add a matrix of string data to the table, as doubles.
//     *
//     * @param data the matrix of string doubles to add
//     */
//    public void addMatrix(final String[][] data) {
//        removeAllRows();
//        
//        int numCols = data[0].length;
//        model.addColumn("");
//        
//        for (int i = 0; i < numCols; i++) {
//            model.addColumn(Integer.toString(i));
//        }
//        
//        for (int i = 0; i < data.length; i++) {
//            Vector row = new Vector(data[i].length + 1);
//            
//            for (int j = 0; j < data[i].length; j++) {
//                row.add(j , Double.valueOf((String) data[i][j]));
//            }
//                
//            model.addRow(row);
//        }
//    }
}
