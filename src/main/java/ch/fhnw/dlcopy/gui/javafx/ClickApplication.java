package ch.fhnw.dlcopy.gui.javafx;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ClickApplication extends Application {
    public static void main(String[] args){
        launch(args);
    }
    
    // application for acceptance tests.
    @Override public void start(Stage stage) {
        Parent sceneRoot = new ClickPane();
        Scene scene = new Scene(sceneRoot, 100, 100);
        stage.setScene(scene);
        stage.show();
    }

    // scene object for unit tests
    public static class ClickPane extends StackPane {
        public ClickPane() {
            super();
            Button button = new Button("click me!");
            button.setOnAction(actionEvent -> button.setText("clicked!"));
            getChildren().add(button);
        }
    }
}
