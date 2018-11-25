package ch.fhnw.dlcopy;

/**
 * A subdirectory for backups
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Subdirectory {

    private boolean selected;
    private String description;

    public Subdirectory(boolean selected, String description) {
        this.selected = selected;
        this.description = description;
    }

    /**
     * Get the value of description
     *
     * @return the value of description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the value of description
     *
     * @param description new value of description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the value of selected
     *
     * @return the value of selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Set the value of selected
     *
     * @param selected new value of selected
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
