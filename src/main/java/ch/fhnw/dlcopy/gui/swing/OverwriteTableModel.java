package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.PreferredSizesTableModel;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A TableModel for the configuration of files and directories to be overwritten
 * during upgrade or reset.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class OverwriteTableModel extends PreferredSizesTableModel {

    private static final Logger LOGGER
            = Logger.getLogger(OverwriteTableModel.class.getName());
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");

    private final String XML_ENTRY = "entry";
    private final String XML_SOURCE = "source";
    private final String XML_DESTINATION = "destination";

    private final List<OverwriteEntry> ENTRIES;

    /**
     * creates a new OverwriteTableModel
     *
     * @param table the table for this model
     */
    public OverwriteTableModel(JTable table) {

        super(table, new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        ENTRIES = new ArrayList<>();

        initSizes();
    }

    @Override
    public int getRowCount() {
        if (ENTRIES == null) {
            return 0;
        }
        return ENTRIES.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return STRINGS.getString("Source");
            case 1:
                return STRINGS.getString("Destination");
        }
        return null;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

        switch (columnIndex) {
            case 0:
                return ENTRIES.get(rowIndex).getSource();
            case 1:
                return ENTRIES.get(rowIndex).getDestination();
        }

        return null;
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {

        String value = (String) aValue;

        switch (column) {
            case 0:
                ENTRIES.get(row).setSource(value);
                break;
            case 1:
                ENTRIES.get(row).setDestination(value);
        }
    }

    @Override
    public void addRow(Vector rowData) {
        addRow(new Object[]{rowData.get(0), rowData.get(1)});
    }

    @Override
    public void addRow(Object[] rowData) {
        int index = ENTRIES.size();
        ENTRIES.add(new OverwriteEntry(
                (String) rowData[0],
                (String) rowData[1])
        );
        fireTableRowsInserted(index, index);
    }

    @Override
    public void removeRow(int row) {
        ENTRIES.remove(row);
        fireTableRowsDeleted(row, row);
    }

    public void removeRows(int[] selectedRows) {
        // we have to start from the bottom in this case
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            removeRow(selectedRows[i]);
        }
    }

    /**
     * moves the selected rows up
     *
     * @param selectedRows the selected rows
     */
    public void moveUp(int[] selectedRows) {
        for (int selectedRow : selectedRows) {
            // swap values with previous index
            Collections.swap(ENTRIES, selectedRow, selectedRow - 1);
        }
        fireTableDataChanged();
    }

    /**
     * moves the selected rows down
     *
     * @param selectedRows the selected rows
     */
    public void moveDown(int[] selectedRows) {
        // we have to start from the bottom in this case
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            // swap values with next index
            Collections.swap(ENTRIES, selectedRows[i], selectedRows[i] + 1);
        }
        fireTableDataChanged();
    }

    /**
     * returns an XML representation of the model
     *
     * @return an XML representation of the model
     */
    public String getXML() {
        try {
            DocumentBuilderFactory dbFactory
                    = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            // root element
            Element rootElement = doc.createElement("model");
            doc.appendChild(rootElement);

            for (OverwriteEntry overwriteEntry : ENTRIES) {
                Element element = doc.createElement(XML_ENTRY);
                rootElement.appendChild(element);
                Attr attr = doc.createAttribute(XML_SOURCE);
                attr.setValue(overwriteEntry.getSource());
                element.setAttributeNode(attr);
                attr = doc.createAttribute(XML_DESTINATION);
                attr.setValue(overwriteEntry.getDestination());
                element.setAttributeNode(attr);
            }

            // write the content into xml file
            TransformerFactory transformerFactory
                    = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(source, result);
            return outputStream.toString();
        } catch (ParserConfigurationException | TransformerException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        return null;
    }

    /**
     * sets the model data from an XML document
     *
     * @param data the data to set into the table
     */
    public void setXML(String data) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser;
        try {
            saxParser = factory.newSAXParser();
            MyHandler handler = new MyHandler();
            ByteArrayInputStream inputStream
                    = new ByteArrayInputStream(data.getBytes());
            saxParser.parse(inputStream, handler);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    private class MyHandler extends DefaultHandler {

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {

            if (qName.equalsIgnoreCase(XML_ENTRY)) {
                String source = attributes.getValue(XML_SOURCE);
                String destination = attributes.getValue(XML_DESTINATION);
                ENTRIES.add(new OverwriteEntry(source, destination));
            }
        }
    }

    /**
     * returns the list of entries
     *
     * @return the list of entries
     */
    public List<OverwriteEntry> getEntries() {
        return ENTRIES;
    }
}
