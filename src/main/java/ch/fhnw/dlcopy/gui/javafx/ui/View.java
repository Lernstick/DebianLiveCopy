package ch.fhnw.dlcopy.gui.javafx.ui;

import javafx.scene.Parent;

interface View {

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
  
  Parent getView();
}
