package ch.fhnw.dlcopy.gui.javafx;

/**
 * This class represents a FX Controller
 * In this TextField only Numbers can be insert
 */
import javafx.scene.control.TextField;

public class NumericTextField extends TextField {

    @Override
    /**
     * Replaces a range of characters with the given text.
     * @param start The starting index in the range, inclusive. This must be >= 0 and < the end.
     * @param end The ending index in the range, exclusive. This is one-past the last character to delete (consistent with the String manipulation methods). This must be > the start, and <= the length of the text.
     * @param text The text that is to replace the range. This must not be null.
     */
    public void replaceText(int start, int end, String text) {
        if (validate(text)) {
            super.replaceText(start, end, text);
        }
    }

    @Override
    /**
     * Replaces the selection with the given replacement String.
     * If there is no selection, then the replacement text is simply inserted at the current caret position.
     * If there was a selection, then the selection is cleared and the given replacement text inserted.
     * @param text The string, the selection should be replaced with
     */
    public void replaceSelection(String text) {
        if (validate(text)) {
            super.replaceSelection(text);
        }
    }

    /**
     * Validates the given text
     * @param text The text to be validated
     * @return True, if the text only contains numbers
     */
    private boolean validate(String text) {
        return text.matches("[0-9]*");
    }
}
