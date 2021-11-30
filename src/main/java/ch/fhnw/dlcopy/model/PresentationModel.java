package ch.fhnw.dlcopy.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class PresentationModel {
    public final static int WINDOWS_HEIGHT_DEFAULT  = 720;
    public final static int WINDOWS_HEIGHT_MIN      = 720;
    public final static int WINDOWS_WIDTH_DEFAULT   = 1024;
    public final static int WINDOWS_WIDTH_MIN       = 1024;
    
    private static PresentationModel instance;
    
    private final IntegerProperty height    = new SimpleIntegerProperty(WINDOWS_HEIGHT_DEFAULT);
    private final IntegerProperty width     = new SimpleIntegerProperty(WINDOWS_WIDTH_DEFAULT);
    
    private PresentationModel(){}
    
    public static synchronized PresentationModel getInstance(){
        if (instance == null) {
            instance = new PresentationModel();
        }
        return instance;
    }
    
    public int getHeight(){
        return height.get();
    }
    
    public void setHeight(int height){
        if (height >= WINDOWS_HEIGHT_MIN) {
            this.height.set(height);
        } else {
            throw new IllegalArgumentException("Height has to be bigger than " + WINDOWS_HEIGHT_MIN + "px!");
        }
    }
    
    public IntegerProperty heightProperty(){
        return height;
    }
    
    public int getWidth(){
        return width.get();
    }
    
    public void setWidth(int width){
        if (width >= WINDOWS_WIDTH_MIN) {
            this.width.set(width);
        } else {
            throw new IllegalArgumentException("Width has to be bigger than " + WINDOWS_WIDTH_MIN + "px!");
        }
    }
    
    public IntegerProperty widthProperty(){
        return width;
    }
}
