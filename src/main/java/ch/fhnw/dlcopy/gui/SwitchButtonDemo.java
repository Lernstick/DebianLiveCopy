package ch.fhnw.dlcopy.gui;

import ch.fhnw.dlcopy.gui.javafx.SwitchButton;
import java.io.IOException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class SwitchButtonDemo extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        BorderPane root = new BorderPane();
        SwitchButton button = new SwitchButton();
        root.setCenter(button);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
}
