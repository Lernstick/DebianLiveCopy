/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.dlcopy.gui.javafx;

import ch.fhnw.dlcopy.gui.javafx.ui.SceneManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ch.fhnw.dlcopy.gui.javafx.ui.*;

public class StorageMediaManagement extends Application{

    private Scene scene;

    public void setScene(SceneManager sceneManager){
        scene.setRoot(sceneManager.getRoot(this));
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = new StartscreenUI().getRoot(this);
        scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void launchFX(String[] args) {
        launch(args);
    }
}
