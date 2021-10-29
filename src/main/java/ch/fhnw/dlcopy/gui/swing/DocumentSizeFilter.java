package ch.fhnw.dlcopy.gui.swing;

import java.awt.Toolkit;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.DocumentFilter.FilterBypass;

/**
 * A DocumentFilter that filters regarding a maximum size
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DocumentSizeFilter extends DocumentFilter {

    private final static int MAX_CHARS = 11;

    @Override
    public void insertString(FilterBypass fb, int offs,
            String newText, AttributeSet a) throws BadLocationException {

        // only allow ASCII input
        if (!isASCII(newText)) {
            return;
        }

        Document document = fb.getDocument();
        int lenght = document.getLength();
        String text = document.getText(0, lenght);
        int specialLenght = getSpecialLength(text);
        System.out.println("specialLenght = " + specialLenght);
        int newLength = getSpecialLength(newText);
        System.out.println("newLength = " + newLength);

        if ((specialLenght + newLength) <= MAX_CHARS) {
            super.insertString(fb, offs, newText, a);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void replace(FilterBypass fb, int offs, int length, String newText,
            AttributeSet a) throws BadLocationException {

        // only allow ASCII input
        if (!isASCII(newText)) {
            return;
        }

        // try string replacement
        Document document = fb.getDocument();
        int docLength = document.getLength();
        String testText = document.getText(0, docLength);
        System.out.println("testText: \"" + testText + "\"");
        StringBuilder builder = new StringBuilder(testText);
        builder.replace(offs, offs + length, newText);
        String replacedText = builder.toString();
        System.out.println("replacedText: \"" + replacedText + "\"");

        // check, if the replacement still fits in
        int specialLength = getSpecialLength(replacedText);
        System.out.println("specialLength = " + specialLength);
        if (specialLength <= MAX_CHARS) {
            super.replace(fb, offs, length, newText, a);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private boolean isASCII(String string) {
        for (int i = 0, length = string.length(); i < length; i++) {
            char character = string.charAt(i);
            if ((character < 0) || (character > 127)) {
                return false;
            }
        }
        return true;
    }

    private int getSpecialLength(String string) {
        // follow special rules for VFAT labels
        int count = 0;
        for (int i = 0, length = string.length(); i < length; i++) {
            char character = string.charAt(i);
            System.out.print("character: \"" + character
                    + "\" (" + (int) character + ") = ");
            if ((character >= 0) && (character <= 127)) {
                // ASCII
                if ((character == 39) || (character == 96)) {
                    // I have no idea why those both characters take up 3 bytes
                    // but they really do...
                    System.out.println("3 Byte");
                    count += 3;
                } else {
                    System.out.println("1 Byte (simple ASCII)");
                    count++;
                }
            } else {
                // non ASCII
                System.out.println("2 Byte (non-ASCII)");
                count += 2;
            }
        }
        return count;
    }
}
