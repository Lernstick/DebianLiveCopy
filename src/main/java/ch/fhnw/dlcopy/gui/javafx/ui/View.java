package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import javafx.scene.Parent;

public interface View {

  default void init(){
    initSelf();
    initControls();
    layoutControls();
    setupBindings();
    setupEventHandlers();
    setupValueChangedListeners();
  }

  default void initSelf(){

  }

  default void initControls(){

  }

  default void layoutControls(){

  }

  default void setupBindings(){

  }

  default void setupEventHandlers(){

  }

  default void setupValueChangedListeners(){

  }
  
  /**
   * Returns the root parent. This parent can be displayed in a FX-scene
   * For displaying an other scene, this view can call `context.setScene(new ViewToBeDisplayed())`
   * @param context The context, where to set the scene
   * @return A Parent to be displayed
   */
  Parent getRoot(SceneContext context);
}
