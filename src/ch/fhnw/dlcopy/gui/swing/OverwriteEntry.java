package ch.fhnw.dlcopy.gui.swing;

/**
 * An entry for overwriting files
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class OverwriteEntry {

    private String source;
    private String destination;

    /**
     * Creates a new OverwriteEntry
     *
     * @param source the source
     * @param destination the destination to overwrite
     */
    public OverwriteEntry(String source, String destination) {
        this.source = source;
        this.destination = destination;
    }

    /**
     * returns the source
     *
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * sets the source
     *
     * @param source the source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * returns the destination to overwrite
     *
     * @return the destination to overwrite
     */
    public String getDestination() {
        return destination;
    }

    /**
     * sets the destination to overwrite
     *
     * @param destination the destination to overwrite
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }
}
