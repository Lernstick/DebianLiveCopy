package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.StorageMediaManagement;
import javafx.scene.Parent;

public interface SceneManager {
    Parent getRoot(StorageMediaManagement context);
}
