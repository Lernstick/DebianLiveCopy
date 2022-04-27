package ch.fhnw.dlcopy.model;

import java.util.Locale;

/**
 * A instance witch implements this interface can be registered in the presentation model
 * It then gets updated, when the language has changed.
 * @author lukas.gysin
 */
public interface LanguageObserver {
    
    /**
     * This methode gets called, when the language has changed.
     * @param oldLanguage The old language
     * @param newLanguage The new set language
     */
    public void languageChanged(Locale oldLanguage, Locale newLanguage);
}
